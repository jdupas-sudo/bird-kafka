package birds.utils;

/**
 * Single source of truth for all Gatling session variable names.
 *
 * Keeping keys here prevents typos from silently breaking a scenario chain
 * (a mistyped key returns null at runtime rather than a compile error).
 * Reference these constants in both feeders and endpoints instead of
 * using raw strings.
 */
public final class Keys {

    /** ID of a randomly selected seed bird, supplied by the randomBirdId feeder. */
    public static final String BIRD_ID = "birdId";

    /** Server-assigned ID of a bird created during the Create & Cleanup scenario. */
    public static final String NEW_BIRD_ID = "newBirdId";

    /** Monotonically increasing counter used to give each created bird a unique name. */
    public static final String UNIQUE_ID = "uniqueId";

    /** Search keyword drawn from the search-terms.csv feeder. */
    public static final String SEARCH_TERM = "searchTerm";

    /** JSON payload string assembled per-VU for direct Kafka produce requests. */
    public static final String KAFKA_PAYLOAD = "kafkaPayload";

    /** Unique marker injected into a Kafka-ingested sighting and searched in /api/events. */
    public static final String LAG_PROBE_MARKER = "lagProbeMarker";

    /** Per-session flag indicating whether the expected sighting.created event was observed. */
    public static final String LAG_FOUND = "lagFound";

    /** Number of poll attempts performed while waiting for the expected event. */
    public static final String LAG_POLL_ATTEMPT = "lagPollAttempt";

    /** Raw /api/events response body captured during lag polling. */
    public static final String LAG_EVENTS_BODY = "lagEventsBody";

    private Keys() {}
}
