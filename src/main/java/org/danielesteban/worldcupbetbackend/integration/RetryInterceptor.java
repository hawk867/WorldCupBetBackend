package org.danielesteban.worldcupbetbackend.integration;

import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Set;

public class RetryInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryInterceptor.class);
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(500, 502, 503, 504);

    private final int maxRetries;

    public RetryInterceptor(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        IOException lastException = null;
        ClientHttpResponse lastResponse = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                int statusCode = response.getStatusCode().value();

                if (RETRYABLE_STATUSES.contains(statusCode)) {
                    log.warn("football-data.org API error: status={}, endpoint={}, attempt={}/{}",
                            statusCode, request.getURI(), attempt + 1, maxRetries + 1);
                    lastResponse = response;

                    if (attempt < maxRetries) {
                        sleepWithBackoff(attempt);
                        continue;
                    }
                    // All retries exhausted
                    throw new ExternalApiException(statusCode,
                            "API request failed after " + (maxRetries + 1) + " attempts with status " + statusCode);
                }

                // 4xx errors (except 429) are not retried
                if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
                    return response;
                }

                return response;

            } catch (SocketTimeoutException e) {
                log.warn("Timeout calling football-data.org: endpoint={}, attempt={}/{}",
                        request.getURI(), attempt + 1, maxRetries + 1);
                lastException = e;

                if (attempt < maxRetries) {
                    sleepWithBackoff(attempt);
                    continue;
                }
            } catch (ExternalApiException e) {
                throw e;
            } catch (IOException e) {
                if (e.getCause() instanceof SocketTimeoutException) {
                    log.warn("Timeout calling football-data.org: endpoint={}, attempt={}/{}",
                            request.getURI(), attempt + 1, maxRetries + 1);
                    lastException = e;

                    if (attempt < maxRetries) {
                        sleepWithBackoff(attempt);
                        continue;
                    }
                } else {
                    throw e;
                }
            }
        }

        if (lastException != null) {
            throw new ExternalApiException("API request failed after " + (maxRetries + 1)
                    + " attempts due to timeout", lastException);
        }

        throw new ExternalApiException(
                lastResponse != null ? lastResponse.getStatusCode().value() : 0,
                "API request failed after " + (maxRetries + 1) + " attempts");
    }

    private void sleepWithBackoff(int attempt) {
        try {
            long waitMs = (long) Math.pow(2, attempt) * 1000L;
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
