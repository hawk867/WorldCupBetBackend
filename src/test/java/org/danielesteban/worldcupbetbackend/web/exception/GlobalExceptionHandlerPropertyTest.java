package org.danielesteban.worldcupbetbackend.web.exception;

import net.jqwik.api.*;
import org.danielesteban.worldcupbetbackend.service.exception.*;
import org.danielesteban.worldcupbetbackend.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: web-layer
 * Property-based tests for GlobalExceptionHandler
 */
@SuppressWarnings("unused")
class GlobalExceptionHandlerPropertyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    // Feature: web-layer, Property 5: Mapeo de excepciones de dominio a códigos HTTP
    // **Validates: Requirements 16.1-16.9**
    @Property(tries = 100)
    void domainExceptionsMappedToCorrectHttpStatusCodes(
            @ForAll("domainExceptions") RuntimeException exception) {

        ResponseEntity<ErrorResponse> response = invokeHandler(exception);

        int expectedStatus = expectedStatusFor(exception);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(expectedStatus);
    }

    // Feature: web-layer, Property 5 (cont.): Non-domain exceptions map to 500
    // **Validates: Requirements 16.9**
    @Property(tries = 100)
    void nonDomainExceptionsMappedTo500WithGenericMessage(
            @ForAll("nonDomainExceptions") Exception exception) {

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
    }

    // Feature: web-layer, Property 6: Estructura y seguridad del ErrorResponse
    // **Validates: Requirements 17.1, 17.2, 17.3**
    @Property(tries = 100)
    void errorResponseHasRequiredFieldsAndNoSensitiveInfo(
            @ForAll("domainExceptions") RuntimeException exception) {

        ResponseEntity<ErrorResponse> response = invokeHandler(exception);

        ErrorResponse body = response.getBody();

        // 1. Body must not be null
        assertThat(body).isNotNull();

        // 2. All required fields must be present and non-null
        assertThat(body.status()).isGreaterThan(0);
        assertThat(body.error()).isNotNull().isNotBlank();
        assertThat(body.message()).isNotNull().isNotBlank();
        assertThat(body.timestamp()).isNotNull();

        // 3. Message must NOT contain stack traces, class names, or file paths
        String message = body.message();
        assertThat(message).doesNotContain("at org.");
        assertThat(message).doesNotContain(".java:");
        assertThat(message).doesNotContain("Exception:");
        assertThat(message).doesNotMatch(".*\\.(java|class|jar).*");
        assertThat(message).doesNotMatch(".*at [a-zA-Z]+\\.[a-zA-Z]+\\..*\\(.*\\).*");
    }

    // Feature: web-layer, Property 6 (cont.): Non-domain exception ErrorResponse structure
    // **Validates: Requirements 17.1, 17.2, 17.3**
    @Property(tries = 100)
    void nonDomainExceptionErrorResponseHasRequiredFieldsAndNoSensitiveInfo(
            @ForAll("nonDomainExceptions") Exception exception) {

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(exception, request);

        ErrorResponse body = response.getBody();

        // 1. Body must not be null
        assertThat(body).isNotNull();

        // 2. All required fields must be present and non-null
        assertThat(body.status()).isGreaterThan(0);
        assertThat(body.error()).isNotNull().isNotBlank();
        assertThat(body.message()).isNotNull().isNotBlank();
        assertThat(body.timestamp()).isNotNull();

        // 3. Message must NOT contain stack traces, class names, or file paths
        String message = body.message();
        assertThat(message).doesNotContain("at org.");
        assertThat(message).doesNotContain(".java:");
        assertThat(message).doesNotContain("Exception:");
        assertThat(message).doesNotMatch(".*\\.(java|class|jar).*");
        assertThat(message).doesNotMatch(".*at [a-zA-Z]+\\.[a-zA-Z]+\\..*\\(.*\\).*");
    }

    // --- Providers ---

    @Provide
    Arbitrary<RuntimeException> domainExceptions() {
        Arbitrary<String> messages = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(100);
        return messages.flatMap(msg -> Arbitraries.of(
                new ResourceNotFoundException(msg),
                new AuthenticationException(msg),
                new ForbiddenException(msg),
                new ValidationException(msg),
                new PredictionLockedException(msg),
                new DuplicatePredictionException(msg),
                new IllegalStateTransitionException(msg),
                new ExternalApiException(msg, null)
        ));
    }

    @Provide
    Arbitrary<Exception> nonDomainExceptions() {
        Arbitrary<String> messages = Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(100);
        return messages.map(msg -> (Exception) new RuntimeException(msg));
    }

    // --- Helpers ---

    private ResponseEntity<ErrorResponse> invokeHandler(RuntimeException exception) {
        if (exception instanceof ResourceNotFoundException ex) {
            return handler.handleNotFound(ex, request);
        } else if (exception instanceof AuthenticationException ex) {
            return handler.handleAuth(ex, request);
        } else if (exception instanceof ForbiddenException ex) {
            return handler.handleForbidden(ex, request);
        } else if (exception instanceof ValidationException ex) {
            return handler.handleValidation(ex, request);
        } else if (exception instanceof PredictionLockedException ex) {
            return handler.handleLocked(ex, request);
        } else if (exception instanceof DuplicatePredictionException ex) {
            return handler.handleDuplicate(ex, request);
        } else if (exception instanceof IllegalStateTransitionException ex) {
            return handler.handleTransition(ex, request);
        } else if (exception instanceof ExternalApiException ex) {
            return handler.handleExternal(ex, request);
        }
        throw new IllegalArgumentException("Unknown exception type: " + exception.getClass());
    }

    private int expectedStatusFor(RuntimeException exception) {
        if (exception instanceof ResourceNotFoundException) return HttpStatus.NOT_FOUND.value();
        if (exception instanceof AuthenticationException) return HttpStatus.UNAUTHORIZED.value();
        if (exception instanceof ForbiddenException) return HttpStatus.FORBIDDEN.value();
        if (exception instanceof ValidationException) return HttpStatus.BAD_REQUEST.value();
        if (exception instanceof PredictionLockedException) return HttpStatus.CONFLICT.value();
        if (exception instanceof DuplicatePredictionException) return HttpStatus.CONFLICT.value();
        if (exception instanceof IllegalStateTransitionException) return HttpStatus.UNPROCESSABLE_CONTENT.value();
        if (exception instanceof ExternalApiException) return HttpStatus.BAD_GATEWAY.value();
        throw new IllegalArgumentException("Unknown exception type: " + exception.getClass());
    }
}
