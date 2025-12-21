package dfm.medischeduler_routeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableScheduling
@EnableKafkaStreams
public class MedischedulerRouteserviceApplication {
	public static void main(String[] args) {
		SpringApplication.run(MedischedulerRouteserviceApplication.class, args);
	}

}
