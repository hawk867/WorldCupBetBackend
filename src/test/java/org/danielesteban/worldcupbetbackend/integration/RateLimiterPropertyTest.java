package org.danielesteban.worldcupbetbackend.integration;

import net.jqwik.api.*;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RateLimiterPropertyTest {

    // Feature: football-data-integration, Property 7: Rate limiter permite máximo N peticiones por ventana
    // Validates: Requirements 6.1, 6.2
    @Property(tries = 100)
    void rateLimiterAllowsAtMostNRequestsPerWindow(
            @ForAll("rateLimits") int rateLimit,
            @ForAll("requestCounts") int totalRequests) {

        FootballDataProperties properties = Mockito.mock(FootballDataProperties.class);
        when(properties.rateLimit()).thenReturn(rateLimit);

        RateLimiter rateLimiter = new RateLimiter(properties);

        try {
            int successCount = 0;
            for (int i = 0; i < totalRequests; i++) {
                if (rateLimiter.tryAcquire()) {
                    successCount++;
                }
            }

            // Only rateLimit calls should succeed without blocking
            assertThat(successCount).isEqualTo(Math.min(totalRequests, rateLimit));
            // Available permits should be rateLimit minus successful acquisitions
            assertThat(rateLimiter.availablePermits()).isEqualTo(rateLimit - successCount);
        } finally {
            rateLimiter.shutdown();
        }
    }

    @Provide
    Arbitrary<Integer> rateLimits() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> requestCounts() {
        return Arbitraries.integers().between(1, 20);
    }
}
