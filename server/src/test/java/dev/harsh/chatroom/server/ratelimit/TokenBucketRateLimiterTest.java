package dev.harsh.chatroom.server.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the token bucket rate limiter.
 */
class TokenBucketRateLimiterTest {

    @Test
    @DisplayName("Allows requests within burst capacity")
    void allowsWithinBurst() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 20);

        // Should allow up to burst size (20) immediately
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryConsume(), "Request " + i + " should be allowed");
        }
    }

    @Test
    @DisplayName("Rejects requests when tokens exhausted")
    void rejectsWhenExhausted() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 5);

        // Drain all tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryConsume());
        }

        // Next request should be rejected
        assertFalse(limiter.tryConsume());
    }

    @Test
    @DisplayName("Tokens refill over time")
    void tokensRefillOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100, 5);

        // Drain all tokens
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume();
        }
        assertFalse(limiter.tryConsume());

        // Wait for refill (100 tokens/sec = 1 token per 10ms)
        Thread.sleep(100);

        // Should have refilled some tokens
        assertTrue(limiter.tryConsume());
    }

    @Test
    @DisplayName("Available tokens never exceed max (burst size)")
    void tokensNeverExceedMax() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1000, 10);

        // Wait for potential over-refill
        Thread.sleep(100);

        // Available should be capped at 10
        assertTrue(limiter.getAvailableTokens() <= 10.0);
    }

    @Test
    @DisplayName("Multi-token consume works correctly")
    void multiTokenConsume() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10);

        assertTrue(limiter.tryConsume(5)); // 5 remaining
        assertTrue(limiter.tryConsume(5)); // 0 remaining
        assertFalse(limiter.tryConsume(1)); // empty
    }
}
