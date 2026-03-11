package dfm.medischeduler_routeservice;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

import dfm.medischeduler_common.model.Student;

/**
 * Manages the lifecycle of request batches for the Google Routes API.
 *
 * The BatchManager receives incoming potential-match messages from the
 * Kafka stream, sorts them into travel-mode buckets, and dispatches full
 * batches to {@link RouteMatrixBuilder} while respecting the Routes API
 * rate limits (at most 2500 matrix elements per minute).
 *
 * <h3>Key Concepts</h3>
 * <ul>
 *   <li><b>Upload Batch</b> &mdash; the total set of potential matches
 *       submitted at the start of a scheduling run.</li>
 *   <li><b>Request Batch</b> &mdash; a subset of potential matches that
 *       fits within a single ComputeRouteMatrix request (&le; 625 elements).
 *       Batches are grouped by travel mode because the API requires a single
 *       mode per request.</li>
 * </ul>
 *
 * A scheduled task resets the per-minute element counter every 60 seconds
 * to avoid exceeding the rate limit. When the rate limit is reached, batch
 * processing is retried using a {@link ScheduledExecutorService} rather than
 * blocking the calling thread.
 */
@Component
public class BatchManager {

    private static final Logger logger = LoggerFactory.getLogger(BatchManager.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** Maximum matrix elements allowed per minute by the Routes API. */
    private static final int MAX_ELEMENTS_PER_MINUTE = 2500;

    /** The four travel modes supported by the Routes API. */
    private static final List<String> TRAVEL_METHODS = List.of("BIKE", "WALK", "DRIVE", "TRANSIT");

    @Autowired
    private RedisAPI redisApi;

    @Autowired
    private RouteMatrixBuilder routeMatrixBuilder;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ApplicationContext applicationContext;

    /** One request batch per travel mode. */
    private HashMap<String, RequestBatch> requestBatchQueue;

    /**
     * Polls Redis to determine how many matches remain unprocessed.
     *
     * @param clientId the client identifier
     * @return the number of elements still awaiting processing
     */
    private int pollTotalProgress(String clientId) {
        int totalUploadCount = redisApi.getTotalCount(clientId);
        int processedCount = redisApi.getProcessedCount(clientId);
        int failedCount = redisApi.getFailedCount(clientId);
        int notFoundCount = redisApi.getNotFoundCount(clientId);
        return totalUploadCount - (processedCount + failedCount + notFoundCount);
    }

    /**
     * Creates a new request batch in Redis and returns its generated ID.
     *
     * @param clientId the client identifier
     * @return the new batch ID
     */
    private String createNewBatchId(String clientId) {
        String newBatchId = clientId + ":" + UUID.randomUUID().toString();
        redisApi.createNewBatch(newBatchId, clientId);
        redisApi.setBatchMetadata(newBatchId, "processingStartTime", Instant.now().toString(), clientId);
        redisApi.setBatchMetadata(newBatchId, "status", "processing", clientId);
        return newBatchId;
    }

    /**
     * Initializes one {@link RequestBatch} per travel mode for the given client.
     *
     * @param clientId the client identifier
     */
    public void createAllBatches(String clientId) {
        requestBatchQueue = new HashMap<>();
        for (String method : TRAVEL_METHODS) {
            RequestBatch batch = applicationContext.getBean(RequestBatch.class);
            String newBatchId = createNewBatchId(clientId);
            batch.setBatchId(newBatchId);
            batch.setClientId(clientId);
            batch.setBatchType(method);
            batch.setBatchStatus("processing");
            batch.setBatchFillStatus(0);
            requestBatchQueue.put(method, batch);
        }
    }

    /**
     * Sends a full request batch to the Routes API if the rate limit allows.
     * If the per-minute element budget is exhausted, schedules a non-blocking
     * retry after the remaining rate-limit window elapses.
     *
     * @param batch the request batch to process
     */
    private void processBatch(RequestBatch batch) {
        String elemStr = redisApi.getGlobalStateMetadata("elemProcessedInLastMin");
        int elemProcessed = (elemStr != null) ? Integer.parseInt(elemStr) : 0;

        if (elemProcessed <= MAX_ELEMENTS_PER_MINUTE) {
            try {
                routeMatrixBuilder.asyncComputeRouteMatrix(
                        new ArrayList<>(), // TODO: convert origins to RouteMatrixOrigin
                        new ArrayList<>(), // TODO: convert destinations to RouteMatrixDestination
                        batch.getBatchType(),
                        batch.getBatchId(),
                        batch.getClientId());
            } catch (Exception e) {
                logger.error("Error processing batch {}: {}", batch.getBatchId(), e.getMessage(), e);
                return;
            }

            kafkaTemplate.send("ROUTE_MATRIX_TOPIC", batch.getClientId(),
                    "batch processed: " + batch.getBatchId());
            redisApi.incrementGlobalStateMetadata("elemProcessedInLastMin", batch.getRouteMatrixSize());
            redisApi.setBatchMetadata(batch.getBatchId(), "status", "processed", batch.getClientId());
            batch.setBatchStatus("processed");
        } else {
            long delaySeconds = calculateRemainingWaitSeconds();
            logger.info("Rate limit reached, scheduling retry in {} seconds for batch {}", delaySeconds, batch.getBatchId());
            scheduler.schedule(() -> processBatch(batch), delaySeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Cleans up a processed batch from Redis and re-queues any values
     * that were cached while waiting for the rate limit to refresh.
     *
     * @param batch the batch to delete
     */
    private void deleteBatch(RequestBatch batch) {
        redisApi.removeBatchFromRedis(batch.getBatchId(), batch.getClientId());

        // re-process any values that were cached while rate-limited
        Set<Object> members = redisApi.getCachedValues(batch.getClientId());
        if (members != null) {
            for (Object member : members) {
                handleIncomingJob(batch.getClientId(), member.toString());
                redisApi.removeFromCache(member.toString(), batch.getClientId());
            }
        }
    }

    /**
     * Entry point for processing an incoming potential-match message.
     *
     * Extracts the student ID, teacher ID, and travel method from the JSON
     * payload, looks up the corresponding addresses from Redis, and adds
     * the pair to the appropriate travel-mode batch. If the batch is full,
     * it is dispatched for processing immediately.
     *
     * @param clientId the client identifier (Kafka record key)
     * @param value    the JSON message body from the MATCH_TOPIC
     */
    public void handleIncomingJob(String clientId, String value) {
        try {
            JsonNode node = mapper.readTree(value);
            String studentId = node.get("studentId").asText();
            String teacherId = node.get("teacherId").asText();

            // look up the student's travel method from Redis
            Student student = redisApi.getStudent("student:" + studentId, clientId);
            String travelMethod = (student != null) ? student.getTravelMethod() : "DRIVE";

            String studentAddress = redisApi.getStudentAddress("student:" + studentId, clientId);
            String teacherAddress = redisApi.getTeacherAddress("teacher:" + teacherId, clientId);

            if (requestBatchQueue == null) {
                createAllBatches(clientId);
            }

            RequestBatch batch = requestBatchQueue.get(travelMethod);
            if (batch == null) {
                logger.error("No batch found for travel method: {}", travelMethod);
                return;
            }

            String batchStatus = redisApi.getBatchMetadata(batch.getBatchId(), "status", clientId);

            if ("processing".equalsIgnoreCase(batchStatus)) {
                String result = batch.updateBatch(studentId, teacherId, studentAddress, teacherAddress);
                if ("batchFull".equals(result)) {
                    processBatch(batch);
                    redisApi.cacheValueForProcessing(value, clientId);
                }
            } else if ("processed".equalsIgnoreCase(batchStatus)) {
                // previous batch is done; create a new one and clean up the old
                String newBatchId = createNewBatchId(clientId);
                RequestBatch newBatch = applicationContext.getBean(RequestBatch.class);
                newBatch.setBatchId(newBatchId);
                newBatch.setClientId(clientId);
                newBatch.setBatchType(batch.getBatchType());
                newBatch.setBatchStatus("processing");
                newBatch.setBatchFillStatus(0);
                requestBatchQueue.put(travelMethod, newBatch);

                deleteBatch(batch);
            }
        } catch (Exception e) {
            logger.error("Error handling incoming job for client {}: {}", clientId, e.getMessage(), e);
        }
    }

    /**
     * Resets the per-minute rate-limit counter every 60 seconds.
     * This ensures that no more than {@value #MAX_ELEMENTS_PER_MINUTE} matrix
     * elements are sent to the Routes API per minute.
     */
    @Scheduled(fixedRate = 60000)
    public void refreshRateLimitEachMinute() {
        redisApi.setGlobalStateMetadata("elemProcessedInLastMin", "0");
        redisApi.setGlobalStateMetadata("processingStartTime", Instant.now().toString());
    }

    /**
     * Calculates the number of seconds remaining until the current rate-limit
     * minute window elapses. Returns 0 if the window has already passed or the
     * processing start time is unavailable.
     *
     * @return the remaining wait time in seconds (at least 0)
     */
    private long calculateRemainingWaitSeconds() {
        String startTimeStr = redisApi.getGlobalStateMetadata("processingStartTime");
        if (startTimeStr == null) return 0;

        Instant startTime = Instant.parse(startTimeStr);
        long elapsedSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        return Math.max(0, 60 - elapsedSeconds);
    }

    /**
     * Shuts down the scheduled executor service when the application context
     * is destroyed, ensuring no retry tasks are left running.
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
