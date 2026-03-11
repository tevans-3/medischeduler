package dfm.medischeduler_routeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Route Service microservice.
 *
 * This service is responsible for:
 * <ul>
 *   <li>Receiving student and teacher data via REST endpoints</li>
 *   <li>Generating all potential student-teacher pairings</li>
 *   <li>Computing driving/transit/bike/walk distances using the Google Routes API</li>
 *   <li>Caching route data in Redis for consumption by the Scheduler Service</li>
 * </ul>
 *
 * {@code @EnableScheduling} activates the rate-limit refresh timer in
 * {@link BatchManager}. {@code @EnableKafkaStreams} enables the Kafka
 * Streams topology defined in {@link KafkaStreamService}.
 */
@SpringBootApplication
@EnableScheduling
@EnableKafkaStreams
public class MedischedulerRouteserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedischedulerRouteserviceApplication.class, args);
    }
}
