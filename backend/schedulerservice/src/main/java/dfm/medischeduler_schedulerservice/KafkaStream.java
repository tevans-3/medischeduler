package dfm.medischeduler_schedulerservice;

import java.util.List;
import java.util.Map;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

/**
 * Kafka Streams service that consumes messages from {@code ROUTE_MATRIX_TOPIC}.
 *
 * Each message from the Route Service indicates that a batch of route
 * computations has completed. This service polls Redis to check whether
 * <em>all</em> routes have been computed. Once the entire upload has been
 * processed, it loads the required data from Redis, triggers the OR-Tools
 * solver, and persists the resulting assignments back to Redis before
 * notifying downstream consumers via Kafka.
 */
@Service
public class KafkaStream {

    private static final Logger logger = LoggerFactory.getLogger(KafkaStream.class);
    private static final Serde<String> STRING_SERDE = Serdes.String();

    @Autowired
    private RedisAPI redisApi;

    @Autowired
    private ORToolsScheduler orToolsScheduler;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Defines the Kafka Streams topology.
     *
     * Reads from {@code ROUTE_MATRIX_TOPIC}. For each message, checks
     * whether all route computations are complete. If so, invokes the
     * OR-Tools scheduler.
     *
     * @param streamsBuilder injected by Spring Kafka
     */
    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder) {
        KStream<String, String> messageStream = streamsBuilder.stream(
                "ROUTE_MATRIX_TOPIC", Consumed.with(STRING_SERDE, STRING_SERDE));

        messageStream.foreach((key, value) -> {
            try {
                String clientId = key;
                handleMessage(clientId, value);
                logger.info("(Scheduler Service Consumer) Message received: {}", value);
            } catch (Exception e) {
                logger.error("Error processing message: {}", value, e);
            }
        });
    }

    /**
     * Checks whether all routes have been computed and, if so, loads the
     * required data from Redis, runs the OR-Tools solver, and persists the
     * resulting assignments.
     *
     * <p>All Redis reads are performed <em>before</em> the solver is invoked
     * so that the CPU-bound optimization phase performs no I/O. After the
     * solver returns, the assignments are batch-written to Redis and a
     * notification is sent to the {@code OPTIMAL_ASSIGNMENTS_TOPIC} Kafka
     * topic.
     *
     * @param clientId the client identifier
     * @param value    the Kafka message value (informational)
     */
    private void handleMessage(String clientId, String value) {
        boolean allDone = isThisTheLastBatch(clientId);
        if (allDone) {
            logger.info("All routes computed for client {}. Running OR-Tools solver.", clientId);

            // --- Load all data from Redis before solving ---
            List<Student> students = redisApi.getAllStudents(clientId);
            List<Teacher> teachers = redisApi.getAllTeachers(clientId);
            Map<String, Long> routeMatrixData = redisApi.getRouteMatrixData(clientId);

            // --- Run the solver (pure computation, no I/O) ---
            Map<String, String> assignments = orToolsScheduler.runScheduler(
                    clientId, students, teachers, routeMatrixData);

            if (!assignments.isEmpty()) {
                // --- Batch-write assignments to Redis ---
                for (Map.Entry<String, String> entry : assignments.entrySet()) {
                    redisApi.setOptimalAssignment(clientId, entry.getKey(), entry.getValue());
                }

                // --- Notify downstream consumers ---
                kafkaTemplate.send("OPTIMAL_ASSIGNMENTS_TOPIC", clientId, "assignments generated");

                logger.info("Solver completed successfully for client {} ({} assignments)",
                        clientId, assignments.size());
            } else {
                logger.warn("Solver failed for client {}", clientId);
            }
        }
    }

    /**
     * Determines whether all route matrix elements have been processed
     * (successfully, failed, or not-found) by comparing the sum of
     * processed + failed + notFound against the total upload count.
     *
     * @param clientId the client identifier
     * @return {@code true} if all elements have been accounted for
     */
    private boolean isThisTheLastBatch(String clientId) {
        int totalUploadCount = redisApi.getTotalCount(clientId);
        int processedCount = redisApi.getProcessedCount(clientId);
        int failedCount = redisApi.getFailedCount(clientId);
        int notFoundCount = redisApi.getNotFoundCount(clientId);

        int totalSoFar = processedCount + failedCount + notFoundCount;

        if (totalSoFar >= totalUploadCount && totalUploadCount > 0) {
            return true;
        }

        double pctRemaining = (totalUploadCount > 0)
                ? (1.0 - (double) totalSoFar / totalUploadCount) * 100
                : 100.0;
        logger.info("Progress for client {}: {}/{} matches ({}% remaining)",
                clientId, totalSoFar, totalUploadCount, String.format("%.1f", pctRemaining));
        return false;
    }
}
