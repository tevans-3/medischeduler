package dfm.medischeduler_routeservice;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dfm.medischeduler_common.model.PotentialMatch;
import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

/**
 * Service responsible for generating and publishing potential student-teacher
 * matches to Kafka asynchronously.
 *
 * Extracted from {@link MatchController} so that Spring's {@code @Async} proxy
 * works correctly (self-invocation within the same bean bypasses the proxy).
 */
@Service
public class MatchGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(MatchGeneratorService.class);

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private ObjectMapper mapper;

    /**
     * Generates all possible student-teacher pairings and publishes each
     * one as a JSON message to the MATCH_TOPIC Kafka topic.
     *
     * Runs asynchronously via Spring's task executor. The returned
     * {@link CompletableFuture} completes when all matches have been sent.
     *
     * @param students the list of students
     * @param teachers the list of teachers
     * @param clientId the client identifier used as the Kafka key
     * @return a CompletableFuture that completes when all matches are published
     */
    @Async
    public CompletableFuture<Void> generateMatches(List<Student> students,
                                                     List<Teacher> teachers,
                                                     String clientId) {
        for (Teacher teacher : teachers) {
            for (Student student : students) {
                PotentialMatch match = new PotentialMatch(student, teacher);
                try {
                    String json = mapper.writeValueAsString(match);
                    kafkaProducerService.sendMessage("MATCH_TOPIC", json, clientId);
                } catch (JsonProcessingException e) {
                    log.error("Serialization error for match: {}", e.getMessage());
                    throw new RuntimeException("Message not sent, serialization error", e);
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
