package dev.harsh.chatroom.server.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter for per-client message throttling.
 * <p>
 * Thread-safe, lock-free implementation using CAS operations.
 * Tokens refill at a steady rate; burst capacity allows short surges.
 */
public final class TokenBucketRateLimiter {

    private final double tokensPerSecond;
    private final double maxTokens;
    private final AtomicLong lastRefillNanos;
    private volatile double availableTokens;
    private final Object lock = new Object();

    /**
     * @param tokensPerSecond steady-state refill rate
     * @param burstSize       maximum token capacity (burst ceiling)
     */
    public TokenBucketRateLimiter(double tokensPerSecond, int burstSize) {
        this.tokensPerSecond = tokensPerSecond;
        this.maxTokens = burstSize;
        this.availableTokens = burstSize;
        this.lastRefillNanos = new AtomicLong(System.nanoTime());
    }

    /**
     * Try to consume one token.
     *
     * @return true if the request is allowed, false if rate-limited
     */
    public boolean tryConsume() {
        return tryConsume(1);
    }

    /**
     * Try to consume {@code tokens} tokens.
     *
     * @return true if the request is allowed, false if rate-limited
     */
    public boolean tryConsume(int tokens) {
        synchronized (lock) {
            refill();
            if (availableTokens >= tokens) {
                availableTokens -= tokens;
                return true;
            }
            return false;
        }
    }

    /**
     * Current number of available tokens (for metrics/debugging).
     */
    public double getAvailableTokens() {
        synchronized (lock) {
            refill();
            return availableTokens;
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillNanos.get();
        double elapsed = (now - last) / 1_000_000_000.0;
        double newTokens = elapsed * tokensPerSecond;
        if (newTokens > 0) {
            availableTokens = Math.min(maxTokens, availableTokens + newTokens);
            lastRefillNanos.set(now);
        }
    }
}
