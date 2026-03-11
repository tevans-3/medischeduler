package dfm.medischeduler_routeservice;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Kafka producer that sends serialized messages to the specified topic.
 *
 * Used by {@link MatchController} to publish potential student-teacher
 * pairings to the {@code MATCH_TOPIC} for downstream processing by the
 * Kafka Streams topology.
 */
@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a message to the given Kafka topic, keyed by the client ID.
     *
     * The client ID is used as the record key so that all messages for a
     * single scheduling run are routed to the same partition, preserving
     * ordering guarantees.
     *
     * @param topic    the Kafka topic to publish to
     * @param message  the serialized message payload (typically JSON)
     * @param clientId the client identifier used as the Kafka record key
     */
    public void sendMessage(String topic, String message, String clientId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, clientId, message);
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                RecordMetadata meta = result.getRecordMetadata();
                logger.info("Sent to topic={} partition={} offset={}",
                        meta.topic(), meta.partition(), meta.offset());
            } else {
                logger.error("Failed to send message: {}", ex.getMessage(), ex);
            }
        });
    }
}
