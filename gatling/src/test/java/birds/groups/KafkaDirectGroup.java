package birds.groups;

import birds.utils.Config;
import birds.utils.Keys;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static org.galaxio.gatling.kafka.javaapi.KafkaDsl.*;

/**
 * Direct Kafka producer chains used by KafkaDirectSimulation.
 *
 * Each chain publishes multiple records per virtual user so the direct Kafka run
 * is a sustained producer load, not a single-send smoke check.
 */
public final class KafkaDirectGroup {

    private static final String LARGE_NOTES =
            "x".repeat(Math.max(32, Config.KAFKA_DIRECT_LARGE_NOTES_BYTES));

    public static ChainBuilder produceSpreadSmall(String requestName) {
        return group("Spread Key / Small Payload").on(
                produceLoop(requestName, false, false)
        );
    }

    public static ChainBuilder produceHotKeySmall(String requestName) {
        return group("Hot Key / Small Payload").on(
                produceLoop(requestName, true, false)
        );
    }

    public static ChainBuilder produceSpreadLarge(String requestName) {
        return group("Spread Key / Large Payload").on(
                produceLoop(requestName, false, true)
        );
    }

    private static ChainBuilder produceLoop(String requestName, boolean hotKey, boolean largePayload) {
        return repeat(Config.KAFKA_DIRECT_MESSAGES_PER_USER).on(
                exec(session -> withKafkaMessage(session, hotKey, largePayload))
                        .exec(kafka(requestName)
                                .send(
                                        "#{" + Keys.BIRD_ID + "}",
                                        "#{" + Keys.KAFKA_PAYLOAD + "}",
                                        new RecordHeaders()))
        );
    }

    private static Session withKafkaMessage(Session session, boolean hotKey, boolean largePayload) {
        int birdId = hotKey
                ? Config.KAFKA_DIRECT_HOT_KEY_BIRD_ID
                : ThreadLocalRandom.current().nextInt(1, Config.SEED_BIRD_COUNT + 1);

        String payload = largePayload
                ? buildLargePayload(birdId)
                : buildSmallPayload(birdId);

        return session
                .set(Keys.BIRD_ID, birdId)
                .set(Keys.KAFKA_PAYLOAD, payload);
    }

    private static String buildSmallPayload(int birdId) {
        return String.format(
                "{\"bird_id\":%d,"
                        + "\"location\":\"Gatling Direct\","
                        + "\"observer_name\":\"GatlingKafka\","
                        + "\"notes\":\"Direct producer load test\"}",
                birdId);
    }

    private static String buildLargePayload(int birdId) {
        return String.format(
                "{\"bird_id\":%d,"
                        + "\"location\":\"Gatling Direct Large\","
                        + "\"observer_name\":\"GatlingKafkaLarge\","
                        + "\"notes\":\"%s\"}",
                birdId,
                LARGE_NOTES);
    }

    private KafkaDirectGroup() {}
}
