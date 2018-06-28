package eu.europeana.metis.data.checker.service;

import com.google.common.base.Strings;
import eu.europeana.metis.data.checker.common.exception.DataCheckerServiceException;
import eu.europeana.metis.data.checker.common.model.ExtendedValidationResult;
import eu.europeana.metis.data.checker.service.executor.ValidationTask;
import eu.europeana.metis.data.checker.service.executor.ValidationTaskFactory;
import eu.europeana.metis.data.checker.service.executor.ValidationTaskResult;
import eu.europeana.metis.data.checker.service.persistence.RecordDao;
import eu.europeana.validation.model.ValidationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The Data Checker Service implementation to upload (and potentially transform from EDM-External to EDM-Internal) records
 * Created by ymamakis on 9/2/16.
 */
@Service
public class DataCheckerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataCheckerService.class);
  private final ExecutorService executor;

  private final String dataCheckerUrl;
  private final RecordDao dao;
  private final ValidationTaskFactory factory;

  /**
   * Constructor for the data checker service
   *
   * @param dataCheckerServiceConfig The configuration for the data checker servide
   * @param dao The DAO for records.
   * @param factory The factory for creating validation tasks.
   */
  @Autowired
  public DataCheckerService(DataCheckerServiceConfig dataCheckerServiceConfig, RecordDao dao,
      ValidationTaskFactory factory) {
    this.dataCheckerUrl = dataCheckerServiceConfig.getDataCheckerUrl();
    this.dao = dao;
    this.factory = factory;
    this.executor = Executors.newFixedThreadPool(dataCheckerServiceConfig.getThreadCount());
  }


  /**
   * Persist temporarily (24h) records in the data checker portal
   *
   * @param records The records to persist as list of XML strings
   * @param collectionId The collection id to apply (can not be null).
   * @param applyCrosswalk Whether the records are in EDM-External (thus need conversion to
   * EDM-Internal)
   * @param crosswalkPath The path of the conversion XSL from EDM-External to EDM-Internal. Can be
   * null, in which case the default will be used.
   * @param individualRecords Whether we need to return the IDs of the individual records.
   * @return The data checker URL of the records along with the result of the validation
   * @throws DataCheckerServiceException an error occured while
   */
  public ExtendedValidationResult createRecords(List<String> records, final String collectionId,
      boolean applyCrosswalk, String crosswalkPath, boolean individualRecords)
      throws DataCheckerServiceException {

    // Create the tasks
    final Function<String, ValidationTask> validationTaskCreator = record -> factory
        .createValidationTask(applyCrosswalk, record, collectionId, crosswalkPath);
    final List<ValidationTask> tasks = records.stream().map(validationTaskCreator)
        .collect(Collectors.toList());

    // Schedule the tasks.
    final ExecutorCompletionService<ValidationTaskResult> executorCompletionService = new ExecutorCompletionService<>(
        executor);
    final List<Future<ValidationTaskResult>> taskResultFutures = tasks.stream()
        .map(executorCompletionService::submit).collect(Collectors.toList());

    // Wait until the tasks are finished, then commit the changes.
    final List<ValidationTaskResult> taskResults = waitForTasksToComplete(taskResultFutures);
    commitChanges();

    // Done: compile the results.
    return compileResult(taskResults, collectionId, individualRecords);
  }
  
  private void commitChanges() throws DataCheckerServiceException {
    try {
      dao.commit();
    } catch (IOException | SolrServerException e) {
      LOGGER.error("Updating search engine failed", e);
      throw new DataCheckerServiceException("Updating search engine failed", e);
    }
  }

  private List<ValidationTaskResult> waitForTasksToComplete(
      List<Future<ValidationTaskResult>> taskResultFutures) throws DataCheckerServiceException {
    final List<ValidationTaskResult> taskResults;
    try {
      int counter = 1;
      taskResults = new ArrayList<>(taskResultFutures.size());
      for (Future<ValidationTaskResult> taskResultFuture : taskResultFutures) {
        LOGGER.info("Retrieving validation result {} of {}.", counter, taskResultFutures.size());
        counter++;
        taskResults.add(taskResultFuture.get());
      }
    } catch (InterruptedException e) {
      LOGGER.error("Processing validations interrupted", e);
      Thread.currentThread().interrupt();
      throw new DataCheckerServiceException("Processing validations was interrupted", e);
    } catch (ExecutionException e) {
      LOGGER.error("Executing validations failed", e);
      throw new DataCheckerServiceException("Executing validations failed", e);
    }
    return taskResults;
  }

  private ExtendedValidationResult compileResult(final List<ValidationTaskResult> taskResults,
      String collectionId, boolean includeRecordIds) {

    // Obtain the failed results as list of validation results.
    final List<ValidationResult> failedResults =
        taskResults.stream().filter(result -> !result.isSuccess())
            .map(ValidationTaskResult::getValidationResult).collect(Collectors.toList());

    // Obtain the succeeded results as list of record IDs.
    final List<String> succeededResults;
    if (includeRecordIds) {
      succeededResults = taskResults.stream().filter(ValidationTaskResult::isSuccess)
          .map(ValidationTaskResult::getRecordId).filter(record -> !Strings.isNullOrEmpty(record))
          .collect(Collectors.toList());
    } else {
      succeededResults = null;
    }

    // Compile the validation result object.
    final ExtendedValidationResult extendedValidationResult = new ExtendedValidationResult();
    extendedValidationResult.setResultList(failedResults);
    extendedValidationResult.setSuccess(failedResults.isEmpty());
    extendedValidationResult.setRecords(succeededResults);
    extendedValidationResult.setPortalUrl(this.dataCheckerUrl + collectionId + "*");
    extendedValidationResult.setDate(new Date());

    // Done.
    return extendedValidationResult;
  }

  /**
   * Delete records at midnight every 24 hrs
   */
  @Scheduled(cron = "00 00 00 * * *")
  public void deleteRecords() throws IOException, SolrServerException {
    dao.deleteRecordIdsByTimestamp();
  }

  @PreDestroy
  public void close() {
    if (executor != null) {
      executor.shutdown();
    }
  }
}