package org.danielesteban.worldcupbetbackend.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class RateLimitInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for rate limit token", e);
        }

        ClientHttpResponse response = execution.execute(request, body);

        if (response.getStatusCode().value() == 429) {
            long retryAfterSeconds = parseRetryAfter(response);
            log.warn("Received 429 Too Many Requests. Waiting {} seconds before retry.", retryAfterSeconds);

            try {
                Thread.sleep(retryAfterSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for Retry-After", e);
            }

            // Acquire another token and retry
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for rate limit token on retry", e);
            }

            response = execution.execute(request, body);
        }

        return response;
    }

    private long parseRetryAfter(ClientHttpResponse response) {
        String retryAfter = response.getHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter);
            } catch (NumberFormatException e) {
                log.warn("Could not parse Retry-After header value: {}. Defaulting to 60s.", retryAfter);
            }
        }
        return 60L;
    }
}
