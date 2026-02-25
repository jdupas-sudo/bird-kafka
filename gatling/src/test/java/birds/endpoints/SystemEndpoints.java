package birds.endpoints;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * HTTP request definitions for system-level endpoints.
 * These are read-only, require no session variables, and can be freely
 * inserted into any scenario without side effects.
 */
public final class SystemEndpoints {

    /** GET /api/health — liveness check, used to verify the API is up before heavier calls. */
    public static ChainBuilder health() {
        return exec(http("Health")
                .get("/api/health")
                .check(status().is(200)));
    }

    /** GET /api/stats — aggregated counts and averages across the entire data set. */
    public static ChainBuilder stats() {
        return exec(http("Stats")
                .get("/api/stats")
                .check(status().is(200))
                .check(jsonPath("$.total_birds").exists()));
    }

    /**
     * GET /api/events — reads the Kafka event log persisted by the consumer.
     * Useful for verifying that Kafka throughput keeps pace with write load.
     */
    public static ChainBuilder events() {
        return exec(http("List Events")
                .get("/api/events?limit=50")
                .check(status().is(200)));
    }

    private SystemEndpoints() {}
}
