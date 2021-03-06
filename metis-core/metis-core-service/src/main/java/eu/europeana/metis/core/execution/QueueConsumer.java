package eu.europeana.metis.core.execution;

import com.rabbitmq.client.AMQP.BasicProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import eu.europeana.metis.core.workflow.WorkflowExecution;

/**
 * Class that handles the initializing connection to the RabbitMQ distributed queue and handling the
 * consuming of items from the queue, through the implemented {@link #handleDelivery(String,
 * Envelope, BasicProperties, byte[])} method.
 *
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2018-04-13
 */
public class QueueConsumer extends DefaultConsumer {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueueConsumer.class);

  private final WorkflowExecutionSettings workflowExecutionSettings;
  private final PersistenceProvider persistenceProvider;
  private final WorkflowExecutionMonitor workflowExecutionMonitor;

  private final ExecutorService threadPool;
  private final ExecutorCompletionService<WorkflowExecution> completionService;
  private int threadsCounter;

  /**
   * Constructor with all required parameters to initialize the consumer connection to the
   * distributed queue and initialize the execution pool
   *
   * @param rabbitmqConsumerChannel the consumer channel of the queue
   * @param rabbitmqQueueName the queue name
   * @param workflowExecutionSettings the object that contains execution related settings
   * @param persistenceProvider the object that contains persistence related objects
   * @param workflowExecutionMonitor the object used to monitor executions
   * @throws IOException if the consumer channel initialization fails
   */
  public QueueConsumer(Channel rabbitmqConsumerChannel, String rabbitmqQueueName,
      WorkflowExecutionSettings workflowExecutionSettings, PersistenceProvider persistenceProvider,
      WorkflowExecutionMonitor workflowExecutionMonitor) throws IOException {
    super(persistenceProvider.getRabbitmqConsumerChannel());
    this.workflowExecutionSettings = workflowExecutionSettings;
    this.persistenceProvider = persistenceProvider;
    threadPool =
        Executors.newFixedThreadPool(this.workflowExecutionSettings.getMaxConcurrentThreads());
    completionService = new ExecutorCompletionService<>(threadPool);
    this.workflowExecutionMonitor = workflowExecutionMonitor;

    // For correct priority. Keep in mind this pre-fetches a message before going into
    // handleDelivery
    rabbitmqConsumerChannel.basicQos(1);
    // Auto acknowledge false(second parameter) because of Qos.
    rabbitmqConsumerChannel.basicConsume(rabbitmqQueueName, false, this);
  }

  //Does not run as a thread. Each execution will run separately one after the other for each consumption
  //Make sure that if an exception occurs from mongo connections, the related "current" execution is safe
  //to not be processed in this run and will be picked up on a later stage. See also the configuration of
  //the related ConnectionFactory.
  @Override
  public void handleDelivery(String consumerTag, Envelope rabbitmqEnvelope,
      AMQP.BasicProperties properties, byte[] body) throws IOException {
    String objectId = new String(body, StandardCharsets.UTF_8);
    LOGGER.info("WorkflowExecution id: {} received from queue.", objectId);
    //Clean thread pool, some executions might have already finished
    if (threadsCounter >= workflowExecutionSettings.getMaxConcurrentThreads()) {
      LOGGER.debug(
          "Trying to clean thread pool, found thread pool full with threadsCounter: {}, maxConcurrentThreads: {}",
          threadsCounter, workflowExecutionSettings.getMaxConcurrentThreads());
      checkAndCleanCompletionService();
    }

    boolean sendAck = true;
    try {
      WorkflowExecution workflowExecution =
          persistenceProvider.getWorkflowExecutionDao().getById(objectId);
      if (workflowExecution == null) {
        // This execution no longer exists and we need to ignore it.
        LOGGER.warn("Workflow execution with id: {} is in queue but no longer exists.", objectId);
      } else if (workflowExecution.isCancelling()) {
        // Has been cancelled, do not execute
        workflowExecution.setWorkflowAndAllQualifiedPluginsToCancelled();
        persistenceProvider.getWorkflowExecutionDao().update(workflowExecution);
        LOGGER.info("Cancelled inqueue user workflow execution with id: {}",
            workflowExecution.getId());
      } else {
        sendAck = submitExecutionOrMarkNack(objectId);
      }
    } catch (RuntimeException e) {
      LOGGER.error(
          "Exception occurred during submitting message from queue to a workflowExecution for id {}",
          objectId, e);
    } finally {
      sendAckOrNack(rabbitmqEnvelope, objectId, sendAck);
    }
  }

  private boolean submitExecutionOrMarkNack(String objectId) {
    boolean sendAck;
    //If the thread pool is still full, executions are still active. Flag to send the message back to the queue.
    if (threadsCounter >= workflowExecutionSettings.getMaxConcurrentThreads()) {
      sendAck = false;
    } else {
      // Submit for execution
      WorkflowExecutor workflowExecutor = new WorkflowExecutor(objectId, persistenceProvider,
          workflowExecutionSettings, workflowExecutionMonitor);
      completionService.submit(workflowExecutor);
      threadsCounter++;
      sendAck = true;
    }
    return sendAck;
  }

  private void sendAckOrNack(Envelope rabbitmqEnvelope, String objectId, boolean sendAck)
      throws IOException {
    if (sendAck) {
      // Send ACK back to remove from queue asap.
      super.getChannel().basicAck(rabbitmqEnvelope.getDeliveryTag(), false);
      LOGGER.debug("ACK sent for {} with tag {}", objectId, rabbitmqEnvelope.getDeliveryTag());
    } else {
      //Send NACK to send message back to the queue. Message will go to the same position it was or as close as possible
      //NACK multiple(second parameter) we want one. Requeue(Third parameter), do not discard
      super.getChannel().basicNack(rabbitmqEnvelope.getDeliveryTag(), false, true);
      LOGGER.debug("NACK sent for {} with tag {}", objectId, rabbitmqEnvelope.getDeliveryTag());
    }
  }

  private void checkAndCleanCompletionService() throws IOException {
    //Block for a small period and try cleaning up
    try {
      Future<WorkflowExecution> userWorkflowExecutionFuture = completionService
          .poll(workflowExecutionSettings.getPollingTimeoutForCleaningCompletionServiceInSecs(),
              TimeUnit.SECONDS);
      if (userWorkflowExecutionFuture != null) {
        threadsCounter--;
      }
      while (completionService.poll() != null) {
        threadsCounter--;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(
          "Interrupted while polling for taking a Future from the ExecutorCompletionService", e);
    }
  }

  @PreDestroy
  void close() {
    threadPool.shutdown();
  }

  int getThreadsCounter() {
    return threadsCounter;
  }
}
