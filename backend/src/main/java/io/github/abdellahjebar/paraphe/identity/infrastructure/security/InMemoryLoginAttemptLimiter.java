package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import io.github.abdellahjebar.paraphe.identity.port.LoginAttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sliding window of recent failures per key, kept in memory.
 *
 * <p>Valid for a single instance, which is what the first version runs. State is lost on restart,
 * which only ever relaxes the limit.
 */
@Component
class InMemoryLoginAttemptLimiter implements LoginAttemptLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    InMemoryLoginAttemptLimiter(Clock clock,
                                @Value("${paraphe.login.max-failures:5}") int maxFailures,
                                @Value("${paraphe.login.window-minutes:15}") long windowMinutes) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    public boolean isBlocked(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return false;
        }
        synchronized (recent) {
            purge(recent);
            return recent.size() >= maxFailures;
        }
    }

    @Override
    public void recordFailure(String key) {
        if (failures.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            purge(recent);
            recent.addLast(clock.instant());
        }
    }

    @Override
    public void reset(String key) {
        failures.remove(key);
    }

    /** Forgets everything. For tests. */
    void clear() {
        failures.clear();
    }

    private void purge(Deque<Instant> recent) {
        Instant limit = clock.instant().minus(window);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(limit)) {
            recent.removeFirst();
        }
    }

    private void cleanup() {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                purge(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }
}
