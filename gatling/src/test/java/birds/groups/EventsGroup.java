package birds.groups;

import birds.endpoints.SystemEndpoints;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Events — polls the Kafka-backed event log endpoint.
 *
 * Running this concurrently with the Create & Cleanup scenario lets us verify
 * that the Kafka consumer writes events fast enough to keep up with write load.
 * If event counts lag behind write counts in the report, it signals consumer
 * back-pressure or broker throughput issues.
 */
public final class EventsGroup {

    public static ChainBuilder poll() {
        return group("Events").on(
                exec(SystemEndpoints.events())
        );
    }

    private EventsGroup() {}
}
