package birds.utils;

import io.gatling.javaapi.core.FeederBuilder;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.csv;

/**
 * All feeder definitions in one place.
 *
 * Two styles are used here intentionally:
 *
 *   - CSV feeder (searchTerms): the data lives in resources/data/search-terms.csv,
 *     making it easy to extend without touching Java code.
 *
 *   - Programmatic feeder (randomBirdId): generates values on the fly when the
 *     data set is small or purely numeric (avoids maintaining a CSV for 1-15).
 *
 *   - AtomicInteger counter (nextUniqueId): shared across all virtual users to
 *     guarantee globally unique bird names even under concurrent load.
 */
public final class Feeders {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * Picks a random bird ID in the range [1, SEED_BIRD_COUNT].
     * Each virtual user gets its own value via the session key {@link Keys#BIRD_ID}.
     */
    public static Iterator<Map<String, Object>> randomBirdId() {
        return Stream.generate(
                (Supplier<Map<String, Object>>) () -> Map.of(
                        Keys.BIRD_ID,
                        ThreadLocalRandom.current().nextInt(1, Config.SEED_BIRD_COUNT + 1)
                )
        ).iterator();
    }

    /**
     * Draws a random search keyword from resources/data/search-terms.csv.
     * Random strategy means each virtual user may get the same term — that is
     * intentional, as popular searches are more realistic than round-robin.
     */
    public static FeederBuilder<String> searchTerms() {
        return csv("data/search-terms.csv").random();
    }

    /**
     * Returns the next globally unique integer. Used to give each created bird
     * a distinct name so concurrent virtual users never collide.
     */
    public static int nextUniqueId() {
        return COUNTER.incrementAndGet();
    }

    private Feeders() {}
}
