package birds.groups;

import birds.endpoints.BirdEndpoints;
import birds.endpoints.SystemEndpoints;
import birds.utils.Feeders;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Browse — the most common user journey (read-only, highest traffic weight).
 *
 * Simulates a user who: checks the API is alive, browses the bird list,
 * clicks into a random bird's detail page, views its diet and sightings,
 * then checks the catalogue statistics.
 *
 * Pauses between steps model human think-time (1–2 s random window).
 * All requests are wrapped in a named group so the Gatling HTML report
 * shows aggregated response-time stats for the journey as a whole.
 */
public final class BrowseGroup {

    public static ChainBuilder browse() {
        return group("Browse").on(
                exec(SystemEndpoints.health())
                        .pause(1, 2)
                        .exec(BirdEndpoints.list())
                        .pause(1, 2)
                        // Feed a random seed-bird ID so detail calls are spread across records
                        .feed(Feeders.randomBirdId())
                        .exec(BirdEndpoints.get())
                        .pause(1, 2)
                        .exec(BirdEndpoints.getDiet())
                        .pause(1, 2)
                        .exec(BirdEndpoints.listSightings())
                        .pause(1, 2)
                        .exec(SystemEndpoints.stats())
        );
    }

    private BrowseGroup() {}
}
