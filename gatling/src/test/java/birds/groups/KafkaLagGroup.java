package birds.groups;

import birds.utils.Config;
import birds.utils.Feeders;
import birds.utils.Keys;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Kafka lag probe — dedicated end-to-end visibility check.
 *
 * For each virtual user:
 *   1) Publish one async ingest request (HTTP 202).
 *   2) Poll /api/events for sighting.created records.
 *   3) Fail the final verification request if the marker is not observed in time.
 */
public final class KafkaLagGroup {

    public static ChainBuilder probe() {
        return group("Kafka Lag Probe").on(
                feed(Feeders.randomBirdId())
                        .exec(KafkaLagGroup::initializeProbe)
                        .exec(http("Kafka Lag / Ingest")
                                .post("/api/kafka/sightings")
                                .body(StringBody(KafkaLagGroup::buildIngestBody))
                                .asJson()
                                .check(status().is(202)))
                        .asLongAs(KafkaLagGroup::shouldPoll).on(
                                pause(Duration.ofMillis(Config.KAFKA_LAG_POLL_INTERVAL_MS)),
                                exec(http("Kafka Lag / Poll Events")
                                        .get("/api/events?event_type=sighting.created&limit=" + Config.KAFKA_LAG_EVENTS_LIMIT)
                                        .check(status().is(200))
                                        .check(bodyString().saveAs(Keys.LAG_EVENTS_BODY))),
                                exec(KafkaLagGroup::updateProbeState))
                        .exec(http("Kafka Lag / Verify Marker")
                                .get("/api/events?event_type=sighting.created&limit=" + Config.KAFKA_LAG_EVENTS_LIMIT)
                                .check(status().is(200))
                                .check(substring("#{" + Keys.LAG_PROBE_MARKER + "}").exists()))
        );
    }

    private static Session initializeProbe(Session session) {
        String marker = "lag-probe-" + UUID.randomUUID();
        return session
                .set(Keys.LAG_PROBE_MARKER, marker)
                .set(Keys.LAG_FOUND, false)
                .set(Keys.LAG_POLL_ATTEMPT, 0);
    }

    private static boolean shouldPoll(Session session) {
        return !session.getBoolean(Keys.LAG_FOUND)
                && session.getInt(Keys.LAG_POLL_ATTEMPT) < Config.KAFKA_LAG_MAX_POLLS;
    }

    private static Session updateProbeState(Session session) {
        int attempt = session.getInt(Keys.LAG_POLL_ATTEMPT) + 1;
        String eventsBody = session.getString(Keys.LAG_EVENTS_BODY);
        String marker = session.getString(Keys.LAG_PROBE_MARKER);
        boolean found = eventsBody != null && eventsBody.contains(marker);

        return session
                .set(Keys.LAG_POLL_ATTEMPT, attempt)
                .set(Keys.LAG_FOUND, found)
                .remove(Keys.LAG_EVENTS_BODY);
    }

    private static String buildIngestBody(Session session) {
        int birdId = session.getInt(Keys.BIRD_ID);
        String marker = session.getString(Keys.LAG_PROBE_MARKER);

        return String.format(
                Locale.ROOT,
                "{\"bird_id\":%d,\"location\":\"%s\",\"latitude\":51.5074,\"longitude\":-0.1278,\"observer_name\":\"Kafka Lag Probe\",\"notes\":\"%s\"}",
                birdId,
                marker,
                marker
        );
    }

    private KafkaLagGroup() {}
}
