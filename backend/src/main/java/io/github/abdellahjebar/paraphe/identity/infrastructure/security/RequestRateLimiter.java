package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most {@code maxRequests} per key in each window, counted in memory. A fixed window is crude
 * but enough to stop someone hammering the public review links.
 */
public final class RequestRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxRequests;
    private final Duration length;

    public RequestRateLimiter(Clock clock, int maxRequests, Duration length) {
        this.clock = clock;
        this.maxRequests = maxRequests;
        this.length = length;
    }

    /** Counts a request and says whether it is within the limit. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> !e.getValue().start().plus(length).isAfter(now));
        }
        Window updated = windows.merge(key, new Window(now, 1), (current, fresh) ->
                current.start().plus(length).isAfter(now)
                        ? new Window(current.start(), current.count() + 1)
                        : fresh);
        return updated.count() <= maxRequests;
    }

    public Duration windowLength() {
        return length;
    }

    /** Forgets everything. For tests. */
    public void clear() {
        windows.clear();
    }
}
