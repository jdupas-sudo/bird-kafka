package birds;

import birds.groups.KafkaDirectGroup;
import birds.utils.Config;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.galaxio.gatling.kafka.javaapi.protocol.KafkaProtocolBuilder;

import java.time.Duration;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static org.galaxio.gatling.kafka.javaapi.KafkaDsl.*;

/**
 * Stand-alone simulation for pure Kafka producer load testing.
 *
 * This simulation bypasses the HTTP API and publishes directly to the
 * sighting.ingest topic using Gatling's Kafka plugin.
 *
 * It intentionally covers multiple producer patterns in one run:
 *   1) spread keys + small payload + acks=1      (baseline)
 *   2) hot key + small payload + acks=1          (single-partition pressure)
 *   3) spread keys + small payload + acks=all    (durability overhead)
 *   4) spread keys + large payload + acks=1      (payload throughput pressure)
 *
 * Note: this still measures producer-side publish latency only; it does not
 * directly measure downstream consumer lag/processing time.
 */
public class KafkaDirectSimulation extends Simulation {

    static {
        // Required when Gatling runs in sameProcess mode:
        // Maven keeps a different thread context classloader, and Kafka reflective
        // config loading (serializer classes) must resolve against this simulation's
        // classpath.
        Thread.currentThread().setContextClassLoader(KafkaDirectSimulation.class.getClassLoader());
    }

    private final KafkaProtocolBuilder kafkaAcks1Protocol = kafkaProtocol("1");
    private final KafkaProtocolBuilder kafkaAcksAllProtocol = kafkaProtocol("all");

    private final ScenarioBuilder spreadKeyAcks1 = scenario("Kafka Direct / Spread Key / Acks=1")
            .exec(KafkaDirectGroup.produceSpreadSmall("Kafka Send / Spread / Small / Acks=1"));

    private final ScenarioBuilder hotKeyAcks1 = scenario("Kafka Direct / Hot Key / Acks=1")
            .exec(KafkaDirectGroup.produceHotKeySmall("Kafka Send / Hot Key / Small / Acks=1"));

    private final ScenarioBuilder spreadKeyAcksAll = scenario("Kafka Direct / Spread Key / Acks=all")
            .exec(KafkaDirectGroup.produceSpreadSmall("Kafka Send / Spread / Small / Acks=all"));

    private final ScenarioBuilder spreadKeyLargePayload = scenario("Kafka Direct / Spread Key / Large Payload / Acks=1")
            .exec(KafkaDirectGroup.produceSpreadLarge("Kafka Send / Spread / Large / Acks=1"));

    {
        int users = Config.USERS;
        Duration duration = Duration.ofSeconds(Config.DURATION_SEC);

        setUp(
                spreadKeyAcks1.injectOpen(
                        rampUsers(users).during(duration)
                ).protocols(kafkaAcks1Protocol),

                hotKeyAcks1.injectOpen(
                        rampUsers(Math.max(1, users / 2)).during(duration)
                ).protocols(kafkaAcks1Protocol),

                spreadKeyAcksAll.injectOpen(
                        rampUsers(Math.max(1, users / 2)).during(duration)
                ).protocols(kafkaAcksAllProtocol),

                spreadKeyLargePayload.injectOpen(
                        rampUsers(Math.max(1, users / 3)).during(duration)
                ).protocols(kafkaAcks1Protocol)
        ).assertions(
                // Keep the direct producer suite a practical CI signal:
                // fail only when there are meaningful send failures.
                global().successfulRequests().percent().gt(99.0)
        );
    }

    private KafkaProtocolBuilder kafkaProtocol(String acks) {
        return kafka()
                .topic("sighting.ingest")
                .properties(Map.of(
                        ProducerConfig.ACKS_CONFIG, acks,
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.KAFKA_BOOTSTRAP_SERVERS,
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
                ));
    }
}
