package birds.utils;

import java.util.Optional;

/**
 * Centralises all runtime configuration.
 * Values are read from environment variables so test behaviour can be changed
 * without modifying any source code (e.g. in CI or via docker compose run -e).
 */
public final class Config {

    /** Base URL of the Birds API. Defaults to the docker-compose service name. */
    public static final String BASE_URL = Optional.ofNullable(System.getenv("TARGET_URL"))
            .orElse("http://birds-api:8080");

    /** Peak virtual-user count, applied to the Browse scenario (heaviest). */
    public static final int USERS = positiveIntEnv("USERS", 10);

    /** Ramp-up window in seconds over which virtual users are injected. */
    public static final int DURATION_SEC = positiveIntEnv("DURATION", 30);

    /** Number of birds inserted by the seed script — used to bound random IDs. */
    public static final int SEED_BIRD_COUNT = 15;

    /**
     * Load-model profile for BirdsApiSimulation injection.
     * Supported values:
     *   smoke | capacity | soak | stress | breakpoint | ramp-hold
     */
    public static final String TEST_TYPE = Optional.ofNullable(System.getenv("TEST_TYPE"))
            .orElse("breakpoint");

    /**
     * Kafka broker address used by KafkaDirectSimulation.
     * Inside Docker this resolves to the kafka service on the compose network.
     * For local testing override with: KAFKA_BOOTSTRAP_SERVERS=localhost:9092
     */
    public static final String KAFKA_BOOTSTRAP_SERVERS = Optional.ofNullable(System.getenv("KAFKA_BOOTSTRAP_SERVERS"))
            .orElse("kafka:9092");

    /**
     * Number of produce operations each virtual user performs in KafkaDirectSimulation.
     * Higher values turn direct Kafka runs into sustained load tests instead of single-shot checks.
     */
    public static final int KAFKA_DIRECT_MESSAGES_PER_USER =
            positiveIntEnv("KAFKA_DIRECT_MESSAGES_PER_USER", 30);

    /**
     * Bird ID used as the fixed key in the "hot partition" direct Kafka scenario.
     * Keeping a constant key forces all records to the same partition.
     */
    public static final int KAFKA_DIRECT_HOT_KEY_BIRD_ID =
            positiveIntEnv("KAFKA_DIRECT_HOT_KEY_BIRD_ID", 1);

    /**
     * Size of the notes field for large-payload direct Kafka scenarios.
     * This is the payload size amplifier, not the full record byte size.
     */
    public static final int KAFKA_DIRECT_LARGE_NOTES_BYTES =
            positiveIntEnv("KAFKA_DIRECT_LARGE_NOTES_BYTES", 4096);

    /**
     * Virtual-user count for KafkaLagSimulation.
     * This simulation sends one async Kafka ingest per VU and waits for downstream visibility.
     */
    public static final int KAFKA_LAG_USERS =
            positiveIntEnv("KAFKA_LAG_USERS", Math.max(1, USERS / 2));

    /**
     * Maximum number of /api/events polling attempts per lag probe before failing.
     * Total wait budget is approximately:
     *   KAFKA_LAG_MAX_POLLS * KAFKA_LAG_POLL_INTERVAL_MS
     */
    public static final int KAFKA_LAG_MAX_POLLS =
            positiveIntEnv("KAFKA_LAG_MAX_POLLS", 20);

    /** Delay between /api/events poll attempts in KafkaLagSimulation. */
    public static final int KAFKA_LAG_POLL_INTERVAL_MS =
            positiveIntEnv("KAFKA_LAG_POLL_INTERVAL_MS", 250);

    /** Number of events requested per poll while searching for the probe marker. */
    public static final int KAFKA_LAG_EVENTS_LIMIT =
            positiveIntEnv("KAFKA_LAG_EVENTS_LIMIT", 200);

    private static int positiveIntEnv(String name, int defaultValue) {
        String raw = Optional.ofNullable(System.getenv(name)).orElse(String.valueOf(defaultValue));
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private Config() {}
}
