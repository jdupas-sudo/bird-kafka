package birds;

import birds.groups.BrowseGroup;
import birds.groups.CreateCleanupGroup;
import birds.groups.EventsGroup;
import birds.groups.KafkaSightingGroup;
import birds.groups.SearchGroup;
import birds.utils.Config;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.Locale;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Entry point for the Birds load test — covers both the HTTP API and the
 * Kafka transport layer in a single run.
 *
 * This class is intentionally kept lean — it only wires together the protocols,
 * the scenario definitions, and the injection/assertion profile.
 * All request logic lives in endpoints/, groups/, and utils/.
 *
 * Traffic mix:
 * Browse → USERS VUs (read-heavy HTTP baseline)
 * Search → USERS / 2 VUs (HTTP search)
 * Create / Cleanup → USERS / 3 VUs (sync HTTP writes, triggers Kafka events)
 * Kafka Sighting Ingest → USERS / 4 VUs (HTTP 202 → sighting.ingest → consumer)
 * Events → USERS / 5 VUs (HTTP event log poll)
 *
 * Configure via env vars: TARGET_URL, KAFKA_BOOTSTRAP_SERVERS, USERS, DURATION.
 * See Config.java for defaults.
 */
public class BirdsApiSimulation extends Simulation {

        static {
                // Required when Gatling runs in sameProcess mode:
                // Maven keeps a different thread context classloader, and Kafka
                // reflective config loading (serializer classes) must resolve against
                // this simulation's classpath.
                Thread.currentThread().setContextClassLoader(BirdsApiSimulation.class.getClassLoader());
                Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                        System.err.println("[BirdsApiSimulation] Uncaught exception in thread: " + thread.getName());
                        throwable.printStackTrace(System.err);
                });
        }

        // HTTP protocol — base URL + default headers applied to every HTTP request.
        private final HttpProtocolBuilder httpProtocol = http
                        .baseUrl(Config.BASE_URL)
                        .acceptHeader("application/json")
                        .contentTypeHeader("application/json");

        // Each scenario delegates entirely to its group — one line per scenario.
        private final ScenarioBuilder browse = scenario("Browse")
                        .exec(BrowseGroup.browse());

        private final ScenarioBuilder search = scenario("Search")
                        .exec(SearchGroup.search());

        private final ScenarioBuilder create = scenario("Create & Cleanup")
                        .exec(CreateCleanupGroup.createAndCleanup());

        private final ScenarioBuilder kafkaSightings = scenario("Kafka Sighting Ingest")
                        .exec(KafkaSightingGroup.ingest());

        private final ScenarioBuilder events = scenario("Events")
                        .exec(EventsGroup.poll());

        /*
         * Instance initializer — Gatling's Java DSL requires setUp() to be called
         * at construction time.
         *
         * rampUsers(n).during(d): linearly injects n VUs over d seconds,
         * avoiding a thundering-herd spike at t=0.
         *
         * Math.max(1, ...) ensures every scenario gets at least one VU even
         * when USERS is set to a very low value during local debugging.
         *
         * Assertions (applied globally across all scenarios):
         *
         * p95 < 650 ms
         * FastAPI + SQLite reads land ~50–150 ms; writes hit single-writer
         * SQLite contention at ~200–400 ms; the async Kafka 202 path and
         * direct Kafka produce are cheapest at ~20–100 ms. The blended p95
         * should stay under 650 ms — if it doesn't, the SQLite write lock
         * or the Kafka broker is the bottleneck.
         *
         * max < 5 000 ms
         * Hard ceiling: any outlier above 5 s indicates a timeout or stalled
         * consumer, not normal tail latency.
         *
         * success rate > 95 %
         * Tolerates rare transient errors (e.g. a DELETE racing with another
         * VU deleting the same bird) without masking real failures.
         */
        {
                int browseLoad = Math.max(1, Config.USERS);
                int searchLoad = Math.max(1, Config.USERS / 2);
                int createLoad = Math.max(1, Config.USERS / 3);
                int kafkaIngestLoad = Math.max(1, Config.USERS / 4);
                int eventsLoad = Math.max(1, Config.USERS / 5);

                setUp(
                                injectionProfile(browse, browseLoad),
                                injectionProfile(search, searchLoad),
                                injectionProfile(create, createLoad),
                                injectionProfile(kafkaSightings, kafkaIngestLoad),
                                injectionProfile(events, eventsLoad))
                                .protocols(httpProtocol)
                                .assertions(
                                                global().responseTime().percentile(95).lt(650),
                                                global().responseTime().max().lt(5000),
                                                global().successfulRequests().percent().gt(95.0));
        }

        static final PopulationBuilder injectionProfile(ScenarioBuilder scn, int load) {
                String testType = Config.TEST_TYPE.toLowerCase(Locale.ROOT);
                int safeLoad = Math.max(1, load);
                int durationSec = Math.max(1, Config.DURATION_SEC);
                double targetRate = Math.max(1.0, safeLoad / 10.0);

                return switch (testType) {
                        case "capacity" -> scn.injectOpen(
                                        incrementUsersPerSec(Math.max(1.0, targetRate * 0.5))
                                                        .times(4)
                                                        .eachLevelLasting(Math.max(10, durationSec / 2))
                                                        .separatedByRampsLasting(Math.max(4, durationSec / 8))
                                                        .startingFrom(Math.max(1.0, targetRate * 0.25)));
                        case "soak" -> scn.injectOpen(
                                        constantUsersPerSec(targetRate)
                                                        .during(Math.max(180, durationSec)));
                        case "stress" -> scn.injectOpen(
                                        stressPeakUsers(safeLoad)
                                                        .during(Math.max(20, durationSec / 3)));
                        case "breakpoint" -> scn.injectOpen(
                                        rampUsers(safeLoad)
                                                        .during(Duration.ofSeconds(durationSec)));
                        case "ramp-hold" -> scn.injectOpen(
                                        rampUsersPerSec(0.0)
                                                        .to(targetRate)
                                                        .during(Math.max(30, durationSec / 2)),
                                        constantUsersPerSec(targetRate)
                                                        .during(Math.max(60, durationSec)));
                        case "smoke" -> scn.injectOpen(
                                        atOnceUsers(1));
                        default -> scn.injectOpen(
                                        rampUsers(safeLoad)
                                                        .during(Duration.ofSeconds(durationSec)));
                };
        }
}
