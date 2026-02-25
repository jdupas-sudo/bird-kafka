package birds.groups;

import birds.endpoints.BirdEndpoints;
import birds.utils.Feeders;
import birds.utils.Keys;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Create & Cleanup — exercises every write path and the full Kafka event pipeline.
 *
 * Each virtual user runs a complete lifecycle:
 *   1. POST  /api/birds               → fires bird.created  Kafka event
 *   2. POST  /api/birds/{id}/sightings → fires sighting.created Kafka event
 *   3. PUT   /api/birds/{id}           → fires bird.updated  Kafka event
 *   4. DELETE /api/birds/{id}           → fires bird.deleted  Kafka event
 *
 * A globally unique ID (AtomicInteger) is stamped into the session before the
 * create request so concurrent virtual users never produce conflicting bird names.
 * The server-assigned record ID is then saved as {@link Keys#NEW_BIRD_ID} and
 * threaded through all subsequent steps in the chain.
 *
 * Guard strategy — why doIf instead of exitHereIfFailed():
 *   exitHereIfFailed() is unreliable inside group().on() — Gatling continues
 *   executing the group chain even when the session is in a failed state, which
 *   causes every downstream step to throw "No attribute named 'newBirdId' is defined"
 *   and inflate the error count with noise that masks the real failure.
 *   doIf(session.contains(NEW_BIRD_ID)) checks the session attribute directly:
 *   if create succeeded the key exists and the cleanup chain runs; if create
 *   failed the key is absent and the downstream steps are silently skipped,
 *   leaving only the original create failure in the report.
 *
 * The DELETE at the end keeps the database from growing unbounded during long runs.
 */
public final class CreateCleanupGroup {

    public static ChainBuilder createAndCleanup() {
        return group("Create & Cleanup").on(
                // Stamp a unique suffix so bird names never collide across virtual users
                exec(session -> session.set(Keys.UNIQUE_ID, Feeders.nextUniqueId()))
                        .exec(BirdEndpoints.create())   // saves newBirdId on success
                        // Only proceed if the create succeeded and newBirdId was captured.
                        // doIf is used instead of exitHereIfFailed() because the latter
                        // does not reliably interrupt execution inside group().on().
                        .doIf(session -> session.contains(Keys.NEW_BIRD_ID)).then(
                                pause(1, 2)
                                        .exec(BirdEndpoints.createSighting())
                                        .pause(1, 2)
                                        .exec(BirdEndpoints.update())
                                        .pause(1, 2)
                                        .exec(BirdEndpoints.delete())
                        )
        );
    }

    private CreateCleanupGroup() {}
}
