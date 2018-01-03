package eu.europeana.metis.core.service;

import eu.europeana.cloud.mcs.driver.DataSetServiceClient;
import eu.europeana.cloud.service.mcs.exception.DataSetAlreadyExistsException;
import eu.europeana.cloud.service.mcs.exception.MCSException;
import eu.europeana.metis.core.dao.DatasetDao;
import eu.europeana.metis.core.dao.ScheduledWorkflowDao;
import eu.europeana.metis.core.dao.WorkflowDao;
import eu.europeana.metis.core.dao.WorkflowExecutionDao;
import eu.europeana.metis.core.dataset.Dataset;
import eu.europeana.metis.core.exceptions.BadContentException;
import eu.europeana.metis.core.exceptions.NoDatasetFoundException;
import eu.europeana.metis.core.exceptions.NoScheduledWorkflowFoundException;
import eu.europeana.metis.core.exceptions.NoWorkflowExecutionFoundException;
import eu.europeana.metis.core.exceptions.NoWorkflowFoundException;
import eu.europeana.metis.core.exceptions.ScheduledWorkflowAlreadyExistsException;
import eu.europeana.metis.core.exceptions.WorkflowAlreadyExistsException;
import eu.europeana.metis.core.exceptions.WorkflowExecutionAlreadyExistsException;
import eu.europeana.metis.core.execution.WorkflowExecutorManager;
import eu.europeana.metis.core.workflow.ScheduleFrequence;
import eu.europeana.metis.core.workflow.ScheduledWorkflow;
import eu.europeana.metis.core.workflow.Workflow;
import eu.europeana.metis.core.workflow.WorkflowExecution;
import eu.europeana.metis.core.workflow.WorkflowStatus;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-05-24
 */
@Service
public class OrchestratorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorService.class);

  private final WorkflowExecutionDao workflowExecutionDao;
  private final WorkflowDao workflowDao;
  private final ScheduledWorkflowDao scheduledWorkflowDao;
  private final DatasetDao datasetDao;
  private final WorkflowExecutorManager workflowExecutorManager;
  private final DataSetServiceClient ecloudDataSetServiceClient;
  private String ecloudProvider; //Initialize with setter

  @Autowired
  public OrchestratorService(WorkflowDao workflowDao,
      WorkflowExecutionDao workflowExecutionDao,
      ScheduledWorkflowDao scheduledWorkflowDao,
      DatasetDao datasetDao,
      WorkflowExecutorManager workflowExecutorManager,
      DataSetServiceClient ecloudDataSetServiceClient) throws IOException {
    this.workflowDao = workflowDao;
    this.workflowExecutionDao = workflowExecutionDao;
    this.scheduledWorkflowDao = scheduledWorkflowDao;
    this.datasetDao = datasetDao;
    this.workflowExecutorManager = workflowExecutorManager;
    this.ecloudDataSetServiceClient = ecloudDataSetServiceClient;

    this.workflowExecutorManager.initiateConsumer();
  }

  public void createWorkflow(Workflow workflow)
      throws WorkflowAlreadyExistsException {
    checkRestrictionsOnWorkflowCreate(workflow);
    workflowDao.create(workflow);
  }

  public void updateWorkflow(Workflow workflow) throws NoWorkflowFoundException {
    String storedId = checkRestrictionsOnWorkflowUpdate(workflow);
    workflow.setId(new ObjectId(storedId));
    workflowDao.update(workflow);
  }

  public void deleteWorkflow(String workflowOwner, String workflowName) {
    workflowDao.deleteWorkflow(workflowOwner, workflowName);
  }

  public Workflow getWorkflow(String workflowOwner, String workflowName) {
    return workflowDao.getWorkflow(workflowOwner, workflowName);
  }

  public List<Workflow> getAllWorkflows(String workflowOwner, String nextPage) {
    return workflowDao.getAllWorkflows(workflowOwner, nextPage);
  }

  public WorkflowExecution getRunningWorkflowExecution(int datasetName) {
    return workflowExecutionDao
        .getRunningWorkflowExecution(datasetName);
  }

  public void addWorkflowInQueueOfWorkflowExecutions(int datasetId,
      String workflowOwner, String workflowName, int priority)
      throws NoDatasetFoundException, NoWorkflowFoundException, WorkflowExecutionAlreadyExistsException {

    Dataset dataset = checkDatasetExistence(datasetId);
    Workflow workflow = checkWorkflowExistence(workflowOwner, workflowName);
    checkAndCreateDatasetInEcloud(dataset);

    WorkflowExecution workflowExecution = new WorkflowExecution(dataset, workflow,
        priority);
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    String storedWorkflowExecutionId = workflowExecutionDao
        .existsAndNotCompleted(datasetId);
    if (storedWorkflowExecutionId != null) {
      throw new WorkflowExecutionAlreadyExistsException(
          String.format("Workflow execution already exists with id %s and is not completed",
              storedWorkflowExecutionId));
    }
    workflowExecution.setCreatedDate(new Date());
    String objectId = workflowExecutionDao.create(workflowExecution);
    workflowExecutorManager.addWorkflowExecutionToQueue(objectId, priority);
    LOGGER.info("WorkflowExecution with id: {}, added to execution queue", objectId);
  }

  //Used for direct, on the fly provided, execution of a Workflow
  public void addWorkflowInQueueOfWorkflowExecutions(int datasetId,
      Workflow workflow, int priority)
      throws WorkflowExecutionAlreadyExistsException, NoDatasetFoundException, WorkflowAlreadyExistsException {
    Dataset dataset = checkDatasetExistence(datasetId);
    //Generate uuid workflowName and check if by any chance it exists.
    workflow.setWorkflowName(new ObjectId().toString());
    checkRestrictionsOnWorkflowCreate(workflow);
    checkAndCreateDatasetInEcloud(dataset);

    WorkflowExecution workflowExecution = new WorkflowExecution(dataset, workflow,
        priority);
    workflowExecution.setWorkflowStatus(WorkflowStatus.INQUEUE);
    String storedWorkflowExecutionId = workflowExecutionDao
        .existsAndNotCompleted(datasetId);
    if (storedWorkflowExecutionId != null) {
      throw new WorkflowExecutionAlreadyExistsException(
          String.format(
              "Workflow execution for datasetId: %s, already exists with id: %s, and is not completed",
              datasetId, storedWorkflowExecutionId));
    }
    workflowExecution.setCreatedDate(new Date());
    String objectId = workflowExecutionDao.create(workflowExecution);
    workflowExecutorManager.addWorkflowExecutionToQueue(objectId, priority);
    LOGGER.info("WorkflowExecution with id: %s, added to execution queue", objectId);
  }

  public void cancelWorkflowExecution(int datasetName)
      throws NoWorkflowExecutionFoundException {

    WorkflowExecution workflowExecution = workflowExecutionDao
        .getRunningOrInQueueExecution(datasetName);
    if (workflowExecution != null) {
      workflowExecutorManager.cancelWorkflowExecution(workflowExecution);
    } else {
      throw new NoWorkflowExecutionFoundException(String.format(
          "Running workflowExecution with datasetName: %s, does not exist or not running",
          datasetName));
    }
  }

  public void removeActiveWorkflowExecutionsFromList(
      List<WorkflowExecution> workflowExecutions) {
    workflowExecutionDao
        .removeActiveExecutionsFromList(workflowExecutions,
            workflowExecutorManager.getMonitorCheckIntervalInSecs());
  }

  public void addWorkflowExecutionToQueue(String workflowExecutionObjectId,
      int priority) {
    workflowExecutorManager
        .addWorkflowExecutionToQueue(workflowExecutionObjectId, priority);
  }

  private void checkRestrictionsOnWorkflowCreate(Workflow workflow)
      throws WorkflowAlreadyExistsException {

    if (StringUtils.isNotEmpty(workflowExists(workflow))) {
      throw new WorkflowAlreadyExistsException(String.format(
          "Workflow with workflowOwner: %s, and workflowName: %s, already exists",
          workflow.getWorkflowOwner(), workflow.getWorkflowName()));
    }
  }

  private String checkRestrictionsOnWorkflowUpdate(Workflow workflow)
      throws NoWorkflowFoundException {

    String storedId = workflowExists(workflow);
    if (StringUtils.isEmpty(storedId)) {
      throw new NoWorkflowFoundException(String.format(
          "Workflow with workflowOwner: %s, and workflowName: %s, not found",
          workflow.getWorkflowOwner(),
          workflow
              .getWorkflowName()));
    }

    return storedId;
  }

  private String workflowExists(Workflow workflow) {
    return workflowDao.exists(workflow);
  }

  public int getWorkflowExecutionsPerRequest() {
    return workflowExecutionDao.getWorkflowExecutionsPerRequest();
  }

  public int getScheduledWorkflowsPerRequest() {
    return scheduledWorkflowDao.getScheduledWorkflowPerRequest();
  }

  public int getWorkflowsPerRequest() {
    return workflowDao.getWorkflowsPerRequest();
  }

  public List<WorkflowExecution> getAllWorkflowExecutions(int datasetId,
      String workflowOwner,
      String workflowName,
      WorkflowStatus workflowStatus, String nextPage) {
    return workflowExecutionDao
        .getAllWorkflowExecutions(datasetId, workflowOwner, workflowName, workflowStatus,
            nextPage);
  }

  public List<WorkflowExecution> getAllWorkflowExecutions(WorkflowStatus workflowStatus,
      String nextPage) {
    return workflowExecutionDao.getAllWorkflowExecutions(workflowStatus, nextPage);
  }

  public ScheduledWorkflow getScheduledWorkflowByDatasetId(int datasetId) {
    return scheduledWorkflowDao.getScheduledWorkflowByDatasetId(datasetId);
  }

  public void scheduleWorkflow(ScheduledWorkflow scheduledWorkflow)
      throws NoDatasetFoundException, NoWorkflowFoundException, BadContentException, ScheduledWorkflowAlreadyExistsException {
    checkRestrictionsOnScheduleWorkflow(scheduledWorkflow);
    scheduledWorkflowDao.create(scheduledWorkflow);
  }

  public List<ScheduledWorkflow> getAllScheduledWorkflows(
      ScheduleFrequence scheduleFrequence, String nextPage) {
    return scheduledWorkflowDao.getAllScheduledWorkflows(scheduleFrequence, nextPage);
  }

  public List<ScheduledWorkflow> getAllScheduledWorkflowsByDateRangeONCE(
      LocalDateTime lowerBound,
      LocalDateTime upperBound, String nextPage) {
    return scheduledWorkflowDao
        .getAllScheduledWorkflowsByDateRangeONCE(lowerBound, upperBound, nextPage);
  }

  private Dataset checkDatasetExistence(int datasetId) throws NoDatasetFoundException {
    Dataset dataset = datasetDao.getDatasetByDatasetId(datasetId);
    if (dataset == null) {
      throw new NoDatasetFoundException(
          String.format("No dataset found with datasetId: %s, in METIS", datasetId));
    }
    return dataset;
  }

  private Workflow checkWorkflowExistence(String workflowOwner, String workflowName)
      throws NoWorkflowFoundException {
    Workflow workflow = workflowDao
        .getWorkflow(workflowOwner, workflowName);
    if (workflow == null) {
      throw new NoWorkflowFoundException(String.format(
          "No workflow found with workflowOwner: %s, and workflowName: %s, in METIS",
          workflowOwner, workflowName));
    }
    return workflow;
  }

  private void checkScheduledWorkflowExistenceForDatasetId(int datasetId)
      throws ScheduledWorkflowAlreadyExistsException {
    String id = scheduledWorkflowDao.existsForDatasetId(datasetId);
    if (id != null) {
      throw new ScheduledWorkflowAlreadyExistsException(String.format(
          "ScheduledWorkflow for datasetId: %s with id %s, already exists",
          datasetId, id));
    }
  }

  public void updateScheduledWorkflow(ScheduledWorkflow scheduledWorkflow)
      throws NoScheduledWorkflowFoundException, BadContentException, NoWorkflowFoundException {
    String storedId = checkRestrictionsOnScheduledWorkflowUpdate(scheduledWorkflow);
    scheduledWorkflow.setId(new ObjectId(storedId));
    scheduledWorkflowDao.update(scheduledWorkflow);
  }

  private void checkRestrictionsOnScheduleWorkflow(ScheduledWorkflow scheduledWorkflow)
      throws NoWorkflowFoundException, NoDatasetFoundException, ScheduledWorkflowAlreadyExistsException, BadContentException {
    checkDatasetExistence(scheduledWorkflow.getDatasetId());
    checkWorkflowExistence(scheduledWorkflow.getWorkflowOwner(),
        scheduledWorkflow.getWorkflowName());
    checkScheduledWorkflowExistenceForDatasetId(scheduledWorkflow.getDatasetId());
    if (scheduledWorkflow.getPointerDate() == null) {
      throw new BadContentException("PointerDate cannot be null");
    }
    if (scheduledWorkflow.getScheduleFrequence() == null
        || scheduledWorkflow.getScheduleFrequence() == ScheduleFrequence.NULL) {
      throw new BadContentException("NULL or null is not a valid scheduleFrequence");
    }
  }

  private String checkRestrictionsOnScheduledWorkflowUpdate(
      ScheduledWorkflow scheduledWorkflow)
      throws NoScheduledWorkflowFoundException, BadContentException, NoWorkflowFoundException {
    checkWorkflowExistence(scheduledWorkflow.getWorkflowOwner(),
        scheduledWorkflow.getWorkflowName());
    String storedId = scheduledWorkflowDao.existsForDatasetId(scheduledWorkflow.getDatasetId());
    if (StringUtils.isEmpty(storedId)) {
      throw new NoScheduledWorkflowFoundException(String.format(
          "Workflow with datasetName: %s, not found", scheduledWorkflow.getDatasetId()));
    }
    if (scheduledWorkflow.getPointerDate() == null) {
      throw new BadContentException("PointerDate cannot be null");
    }
    if (scheduledWorkflow.getScheduleFrequence() == null
        || scheduledWorkflow.getScheduleFrequence() == ScheduleFrequence.NULL) {
      throw new BadContentException("NULL or null is not a valid scheduleFrequence");
    }
    return storedId;
  }

  public void deleteScheduledWorkflow(int datasetId) {
    scheduledWorkflowDao.deleteScheduledWorkflow(datasetId);
  }

  private String checkAndCreateDatasetInEcloud(Dataset dataset) {
    if (StringUtils.isEmpty(dataset.getEcloudDatasetId()) || dataset.getEcloudDatasetId()
        .startsWith("NOT_CREATED_YET")) {
      final String uuid = UUID.randomUUID().toString();
      dataset.setEcloudDatasetId(uuid);
      datasetDao.update(dataset);
      try {
        ecloudDataSetServiceClient
            .createDataSet(ecloudProvider, uuid, "Metis generated dataset");
        return uuid;
      } catch (DataSetAlreadyExistsException e) {
        LOGGER.info("Dataset already exist, not recreating", e);
      } catch (MCSException e) {
        LOGGER.error("An error has occurred during ecloud dataset creation.", e);
      }
    } else {
      LOGGER.info("Dataset with name {} already has a dataset initialized in Ecloud with id {}",
          dataset.getDatasetName(), dataset.getEcloudDatasetId());
    }
    return dataset.getEcloudDatasetId();
  }

  public void setEcloudProvider(String ecloudProvider) {
    this.ecloudProvider = ecloudProvider;
  }
}
