package dfm.medischeduler_routeservice;

import org.springframework.kafka.core.KafkaTemplate; 
import org.springframework.stereotype.Service; 
import org.apache.kafka.common.KafkaException;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.kafka.support.SendResult;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * @param topic
     * @param message
     * @param clientId
     */
    public void sendMessage(String topic, String message, String clientId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, clientId, message); 
        kafkaTemplate.send(record).addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onSuccess(org.springframework.kafka.support.SendResult<String, String> result) {
                RecordMetadata meta = result.getRecordMetadata();
                logger.info("Sent to topic=%s partition=%d offset=%d%n", 
                                   meta.topic(), meta.partition(), meta.offset());
            }
            @Override
            public void onFailure(Throwable ex) {
                if (ex instanceof SerializationException){
                    deadLetterQueue.send(badOriginalPayload);
                }
                else {
                    //log and discard
                    logger.error("Failed to send message: " + ex.getMessage());
                }
            }
        });
    }

}