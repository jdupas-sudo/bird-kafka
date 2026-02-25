package birds;

import birds.groups.KafkaLagGroup;
import birds.utils.Config;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Dedicated pass/fail simulation for consumer-side lag visibility.
 *
 * Each virtual user sends one Kafka-ingested sighting and waits until the
 * corresponding sighting.created event appears in /api/events.
 */
public class KafkaLagSimulation extends Simulation {

    static {
        // Required when Gatling runs in sameProcess mode:
        // Maven keeps a different thread context classloader, and Kafka reflective
        // config loading (serializer classes) must resolve against this simulation's
        // classpath.
        Thread.currentThread().setContextClassLoader(KafkaLagSimulation.class.getClassLoader());
    }

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(Config.BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder kafkaLag = scenario("Kafka Lag / End-to-End")
            .exec(KafkaLagGroup.probe());

    {
        int users = Config.KAFKA_LAG_USERS;
        Duration duration = Duration.ofSeconds(Config.DURATION_SEC);

        setUp(
                kafkaLag.injectOpen(
                        rampUsers(users).during(duration)
                )
        ).protocols(httpProtocol).assertions(
                // Binary CI signal: every probe must be observed downstream in time.
                global().successfulRequests().percent().is(100.0)
        );
    }
}
