package org.danielesteban.worldcupbetbackend.integration;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetryInterceptorPropertyTest {

    // Feature: football-data-integration, Property 8: Reintentos con backoff exponencial para errores transitorios
    // Validates: Requirements 7.1, 7.2, 7.3
    @Property(tries = 100)
    void retriesOnServerErrorUpToMaxRetries(@ForAll("serverErrorCodes") int statusCode) throws IOException {
        // Use maxRetries=1 to keep tests fast (avoids real Thread.sleep delays)
        int maxRetries = 1;
        RetryInterceptor interceptor = new RetryInterceptor(maxRetries);

        HttpRequest request = mock(HttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://test.example.com/api"));

        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse errorResponse = mock(ClientHttpResponse.class);
        when(errorResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(statusCode));

        // All attempts fail with 5xx
        when(execution.execute(any(), any())).thenReturn(errorResponse);

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(ExternalApiException.class);

        // Should have attempted maxRetries + 1 times total
        verify(execution, times(maxRetries + 1)).execute(any(), any());
    }

    // Feature: football-data-integration, Property 8 (success case): If attempt k succeeds, return that response
    // Validates: Requirements 7.1, 7.2, 7.3
    @Property(tries = 100)
    void returnsResponseWhenRetrySucceeds(@ForAll("serverErrorCodes") int errorCode) throws IOException {
        int maxRetries = 1;
        RetryInterceptor interceptor = new RetryInterceptor(maxRetries);

        HttpRequest request = mock(HttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://test.example.com/api"));

        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        ClientHttpResponse errorResponse = mock(ClientHttpResponse.class);
        when(errorResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(errorCode));

        ClientHttpResponse successResponse = mock(ClientHttpResponse.class);
        when(successResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(200));

        // First attempt fails, second succeeds
        when(execution.execute(any(), any()))
                .thenReturn(errorResponse)
                .thenReturn(successResponse);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

        assertThat(result).isSameAs(successResponse);
        verify(execution, times(2)).execute(any(), any());
    }

    // Feature: football-data-integration, Property 9: Errores 4xx no son reintentados
    // Validates: Requirements 7.4
    @Property(tries = 100)
    void clientErrorsAreNotRetried(@ForAll("clientErrorCodes") int statusCode) throws IOException {
        int maxRetries = 3;
        RetryInterceptor interceptor = new RetryInterceptor(maxRetries);

        HttpRequest request = mock(HttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://test.example.com/api"));

        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse clientErrorResponse = mock(ClientHttpResponse.class);
        when(clientErrorResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(statusCode));

        when(execution.execute(any(), any())).thenReturn(clientErrorResponse);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

        // Should return the response immediately without retrying
        assertThat(result).isSameAs(clientErrorResponse);
        // Total attempts must be exactly 1
        verify(execution, times(1)).execute(any(), any());
    }

    @Provide
    Arbitrary<Integer> serverErrorCodes() {
        return Arbitraries.of(500, 502, 503, 504);
    }

    @Provide
    Arbitrary<Integer> clientErrorCodes() {
        // 4xx codes excluding 429 (which has special handling)
        return Arbitraries.integers().between(400, 499)
                .filter(code -> code != 429);
    }
}
