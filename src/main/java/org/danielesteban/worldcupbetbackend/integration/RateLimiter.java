package org.danielesteban.worldcupbetbackend.integration;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimiter {

    private final int maxRequests;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public RateLimiter(FootballDataProperties properties) {
        this.maxRequests = properties.rateLimit();
        this.semaphore = new Semaphore(maxRequests);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-replenish");
            t.setDaemon(true);
            return t;
        });
        // Replenish tokens every 60 seconds
        scheduler.scheduleAtFixedRate(this::replenish, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Acquires a token, blocking if none are available until the window resets.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }

    /**
     * Attempts to acquire a token without blocking.
     *
     * @return true if a token was acquired, false otherwise
     */
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * Returns the number of available tokens (for monitoring).
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    private void replenish() {
        int currentPermits = semaphore.availablePermits();
        int toRelease = maxRequests - currentPermits;
        if (toRelease > 0) {
            semaphore.release(toRelease);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
