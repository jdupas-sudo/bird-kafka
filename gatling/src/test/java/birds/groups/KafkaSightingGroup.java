package birds.groups;

import birds.endpoints.BirdEndpoints;
import birds.utils.Feeders;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Kafka Sighting Ingest — exercises the asynchronous sighting creation path.
 *
 * Flow:
 *   1. Feed a random bird ID into the session.
 *   2. POST /api/kafka/sightings with that bird ID in the body.
 *   3. API publishes to the sighting.ingest Kafka topic and returns 202 immediately.
 *   4. The consumer (running inside the API) picks up the message, persists the
 *      Sighting row, and fires a sighting.created event to the audit log.
 *
 * This is deliberately separate from CreateCleanupGroup which uses the synchronous
 * POST /api/birds/{id}/sightings path. Running both concurrently lets you compare:
 *   - Synchronous REST throughput  (201 response, sighting exists immediately)
 *   - Asynchronous Kafka throughput (202 response, sighting appears after consumer lag)
 *
 * Pair with the Events scenario to observe consumer lag: if sighting.created events
 * in /api/events trail behind the number of 202 responses, the Kafka consumer is
 * becoming a bottleneck under load.
 */
public final class KafkaSightingGroup {

    public static ChainBuilder ingest() {
        return group("Kafka Sighting Ingest").on(
                // Spread ingest requests across all seed birds rather than hammering one
                feed(Feeders.randomBirdId())
                        .exec(BirdEndpoints.ingestSighting())
        );
    }

    private KafkaSightingGroup() {}
}
