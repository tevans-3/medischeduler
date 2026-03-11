package dfm.medischeduler_schedulerservice;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dfm.medischeduler_common.model.Assignment;
import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

/**
 * Kafka Streams sink that listens for assignment-complete notifications on
 * {@code OPTIMAL_ASSIGNMENTS_TOPIC} and pushes the results to the frontend
 * via WebSocket.
 *
 * When a message arrives indicating that assignments have been generated:
 * <ol>
 *   <li>All students and teachers are loaded from Redis.</li>
 *   <li>For each student, the optimal teacher assignment is looked up.</li>
 *   <li>The resulting list of {@link Assignment} objects is serialized to
 *       JSON and sent to the WebSocket topic
 *       {@code /topic/{clientId}/upload/assignments}.</li>
 * </ol>
 */
@Service
public class KafkaSink {

    private static final Logger logger = LoggerFactory.getLogger(KafkaSink.class);
    private static final Serde<String> STRING_SERDE = Serdes.String();

    @Autowired
    private RedisAPI redisApi;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper mapper;

    /**
     * Defines the Kafka Streams topology for the assignment sink.
     *
     * @param streamsBuilder injected by Spring Kafka
     */
    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder) {
        KStream<String, String> messageStream = streamsBuilder.stream(
                "OPTIMAL_ASSIGNMENTS_TOPIC", Consumed.with(STRING_SERDE, STRING_SERDE));

        messageStream.foreach((key, value) -> {
            try {
                handleMessage(key, value);
                logger.info("(Sink) Assignment notification received for client: {}", key);
            } catch (Exception e) {
                logger.error("Error processing assignment notification: {}", value, e);
            }
        });
    }

    /**
     * Builds the full list of assignments and pushes them to the WebSocket.
     *
     * @param clientId the client identifier (Kafka key)
     * @param value    the message value (informational)
     */
    private void handleMessage(String clientId, String value) {
        List<Assignment> assignments = getAssignments(clientId);

        try {
            String jsonAssignments = mapper.writeValueAsString(assignments);
            messagingTemplate.convertAndSend(
                    "/topic/" + clientId + "/upload/assignments", jsonAssignments);
            logger.info("Sent {} assignments to WebSocket for client {}", assignments.size(), clientId);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize assignments for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Loads all students and resolves each one's optimal teacher assignment
     * from Redis.
     *
     * @param clientId the client identifier
     * @return the list of student-teacher assignments
     */
    private List<Assignment> getAssignments(String clientId) {
        List<Assignment> assignments = new ArrayList<>();
        List<Student> students = redisApi.getAllStudents(clientId);

        for (Student student : students) {
            String teacherId = redisApi.getOptimalAssignment(clientId, student.getId());
            if (teacherId != null) {
                Teacher teacher = redisApi.getTeacher("teacher:" + teacherId, clientId);
                Assignment assignment = new Assignment(student, teacher);
                assignments.add(assignment);
            }
        }
        return assignments;
    }
}
