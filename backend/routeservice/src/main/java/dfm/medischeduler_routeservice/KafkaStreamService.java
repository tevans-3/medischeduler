package dfm.medischeduler_routeservice;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Kafka Streams service that consumes messages from the {@code MATCH_TOPIC}.
 *
 * Each message represents a potential student-teacher pairing serialized
 * as JSON. The stream hands every message off to {@link BatchManager},
 * which accumulates pairings into rate-limited request batches for the
 * Google Routes API.
 */
@Service
public class KafkaStreamService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaStreamService.class);
    private static final Serde<String> STRING_SERDE = Serdes.String();

    @Autowired
    private BatchManager batchManager;

    /**
     * Defines the Kafka Streams topology.
     *
     * Reads from {@code MATCH_TOPIC} and forwards each message to the
     * {@link BatchManager#handleIncomingJob(String, String)} method. The
     * Kafka record key is the client ID; the value is the JSON payload
     * containing student and teacher IDs.
     *
     * @param streamsBuilder injected by Spring Kafka to construct the topology
     */
    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder) {
        KStream<String, String> messageStream = streamsBuilder.stream(
                "MATCH_TOPIC", Consumed.with(STRING_SERDE, STRING_SERDE));

        messageStream.foreach((key, value) -> {
            try {
                String clientId = key;
                batchManager.handleIncomingJob(clientId, value);
                logger.info("(Route Service Consumer) Message received: {}", value);
            } catch (Exception e) {
                logger.error("Error processing message: {}", value, e);
            }
        });
    }
}
