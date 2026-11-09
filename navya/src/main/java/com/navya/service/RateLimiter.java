package com.navya.service;

import com.navya.exception.RateLimitedException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket per key. Holds up to `capacity` tokens and refills `perMinute` tokens a minute.
 * shortcut: state is in memory, so it is per app instance and resets on restart, and old keys are
 * never cleaned up. Move to a shared store (Redis) when more than one instance serves users.
 */
public final class RateLimiter {
    private static final class Bucket {
        double tokens;
        long lastMillis;
    }

    private final int capacity;
    private final double refillPerMilli;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, int perMinute, Clock clock) {
        this.capacity = capacity;
        this.refillPerMilli = perMinute / 60_000.0;
        this.clock = clock;
    }

    public void check(String key) {
        if (!tryAcquire(key)) throw new RateLimitedException("Too many attempts, wait a moment and try again");
    }

    public boolean tryAcquire(String key) {
        Bucket b = buckets.computeIfAbsent(key, k -> {
            Bucket n = new Bucket();
            n.tokens = capacity;
            n.lastMillis = clock.millis();
            return n;
        });
        synchronized (b) {
            long now = clock.millis();
            b.tokens = Math.min(capacity, b.tokens + (now - b.lastMillis) * refillPerMilli);
            b.lastMillis = now;
            if (b.tokens < 1) return false;
            b.tokens -= 1;
            return true;
        }
    }
}
