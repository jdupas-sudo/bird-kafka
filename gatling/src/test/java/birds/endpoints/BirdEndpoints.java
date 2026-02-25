package birds.endpoints;

import birds.utils.Keys;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Atomic HTTP request definitions for every bird-related endpoint.
 *
 * Each method returns a ChainBuilder — a reusable, composable piece of a chain
 * that groups and scenarios can stitch together without repeating request config.
 *
 * Session variable requirements are documented on each method so callers know
 * which keys must be present before invoking the request.
 */
public final class BirdEndpoints {

    /** GET /api/birds — paginated list of all birds. */
    public static ChainBuilder list() {
        return exec(http("List Birds")
                .get("/api/birds?skip=0&limit=20")
                .check(status().is(200))
                .check(jsonPath("$[0].id").exists()));
    }

    /**
     * GET /api/birds/{birdId} — fetch a single bird by ID.
     * Requires session key: {@link Keys#BIRD_ID}
     */
    public static ChainBuilder get() {
        return exec(http("Get Bird")
                .get("/api/birds/#{" + Keys.BIRD_ID + "}")
                .check(status().is(200))
                .check(jsonPath("$.common_name").exists()));
    }

    /**
     * GET /api/birds/search?q={searchTerm} — keyword search.
     * Requires session key: {@link Keys#SEARCH_TERM}
     */
    public static ChainBuilder search() {
        return exec(http("Search Birds")
                .get("/api/birds/search?q=#{" + Keys.SEARCH_TERM + "}")
                .check(status().is(200)));
    }

    /**
     * POST /api/birds — create a new bird from the body template.
     * Requires session key: {@link Keys#UNIQUE_ID} (embedded in the body via EL).
     * Saves response key: {@link Keys#NEW_BIRD_ID} for use in subsequent requests.
     * Uses ElFileBody so the #{uniqueId} placeholder is resolved by Gatling's EL engine.
     */
    public static ChainBuilder create() {
        return exec(http("Create Bird")
                .post("/api/birds")
                .body(ElFileBody("bodies/create-bird.json"))
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs(Keys.NEW_BIRD_ID)));
    }

    /**
     * PUT /api/birds/{newBirdId} — partial update using a static body template.
     * Requires session key: {@link Keys#NEW_BIRD_ID}
     */
    public static ChainBuilder update() {
        return exec(http("Update Bird")
                .put("/api/birds/#{" + Keys.NEW_BIRD_ID + "}")
                .body(RawFileBody("bodies/update-bird.json"))
                .check(status().is(200)));
    }

    /**
     * DELETE /api/birds/{newBirdId} — remove the bird created in this session.
     * Requires session key: {@link Keys#NEW_BIRD_ID}
     */
    public static ChainBuilder delete() {
        return exec(http("Delete Bird")
                .delete("/api/birds/#{" + Keys.NEW_BIRD_ID + "}")
                .check(status().is(204)));
    }

    /**
     * GET /api/birds/{birdId}/diet — list diet items for a bird.
     * Requires session key: {@link Keys#BIRD_ID}
     */
    public static ChainBuilder getDiet() {
        return exec(http("Get Diet")
                .get("/api/birds/#{" + Keys.BIRD_ID + "}/diet")
                .check(status().is(200)));
    }

    /**
     * GET /api/birds/{birdId}/sightings — list sightings for a bird.
     * Requires session key: {@link Keys#BIRD_ID}
     */
    public static ChainBuilder listSightings() {
        return exec(http("List Sightings")
                .get("/api/birds/#{" + Keys.BIRD_ID + "}/sightings")
                .check(status().is(200)));
    }

    /**
     * POST /api/birds/{newBirdId}/sightings — record a sighting for a newly created bird.
     * Synchronous: the sighting is persisted before the response is returned.
     * Requires session key: {@link Keys#NEW_BIRD_ID}
     */
    public static ChainBuilder createSighting() {
        return exec(http("Add Sighting")
                .post("/api/birds/#{" + Keys.NEW_BIRD_ID + "}/sightings")
                .body(RawFileBody("bodies/create-sighting.json"))
                .check(status().is(201)));
    }

    /**
     * POST /api/kafka/sightings — publish a sighting to the sighting.ingest Kafka topic.
     * Asynchronous: returns 202 Accepted immediately; the consumer creates the
     * sighting and fires sighting.created in the background.
     * Requires session key: {@link Keys#BIRD_ID} (embedded in the body via EL).
     */
    public static ChainBuilder ingestSighting() {
        return exec(http("Ingest Sighting via Kafka")
                .post("/api/kafka/sightings")
                .body(ElFileBody("bodies/ingest-sighting.json"))
                .check(status().is(202)));
    }

    private BirdEndpoints() {}
}
