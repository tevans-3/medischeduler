package dfm.medischeduler_schedulerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

/**
 * Entry point for the Scheduler Service microservice.
 *
 * This service is responsible for:
 * <ul>
 *   <li>Listening on the {@code ROUTE_MATRIX_TOPIC} to detect when all
 *       route computations are complete.</li>
 *   <li>Running the OR-Tools CP-SAT solver to generate optimal
 *       student-to-teacher assignments.</li>
 *   <li>Pushing assignments to the frontend via WebSocket (STOMP).</li>
 * </ul>
 */
@SpringBootApplication
@EnableKafkaStreams
public class MedischedulerSchedulerserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedischedulerSchedulerserviceApplication.class, args);
    }
}
