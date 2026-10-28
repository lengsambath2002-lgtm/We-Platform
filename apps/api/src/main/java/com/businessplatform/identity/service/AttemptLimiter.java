package com.businessplatform.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window: at most {@code max} attempts per key in {@code window}. Per API instance,
 * which is enough to slow down abuse of a public endpoint (several instances each allow their own).
 */
public class AttemptLimiter {

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public AttemptLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    /** Records an attempt for {@code key}; false when the key is over its limit (not recorded then). */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Instant from = now.minus(window);
        if (attempts.size() > 10_000) {
            attempts.values().removeIf(d -> { synchronized (d) { return d.isEmpty() || d.peekLast().isBefore(from); } });
        }
        Deque<Instant> d = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            while (!d.isEmpty() && d.peekFirst().isBefore(from)) {
                d.pollFirst();
            }
            if (d.size() >= max) {
                return false;
            }
            d.addLast(now);
            return true;
        }
    }
}
