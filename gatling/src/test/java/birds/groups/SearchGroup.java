package birds.groups;

import birds.endpoints.BirdEndpoints;
import birds.utils.Feeders;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Search — lightweight scenario that targets the search endpoint exclusively.
 *
 * Keywords are drawn randomly from resources/data/search-terms.csv, so the
 * same term can appear multiple times across virtual users (intentional —
 * popular searches are more realistic than a strict round-robin).
 */
public final class SearchGroup {

    public static ChainBuilder search() {
        return group("Search").on(
                // CSV feeder injects a "searchTerm" key into each virtual user's session
                feed(Feeders.searchTerms())
                        .exec(BirdEndpoints.search())
        );
    }

    private SearchGroup() {}
}
