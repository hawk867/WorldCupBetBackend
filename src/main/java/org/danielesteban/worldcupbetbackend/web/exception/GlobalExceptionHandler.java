package org.danielesteban.worldcupbetbackend.web.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.danielesteban.worldcupbetbackend.service.exception.AuthenticationException;
import org.danielesteban.worldcupbetbackend.service.exception.DuplicatePredictionException;
import org.danielesteban.worldcupbetbackend.service.exception.ExternalApiException;
import org.danielesteban.worldcupbetbackend.service.exception.ForbiddenException;
import org.danielesteban.worldcupbetbackend.service.exception.IllegalStateTransitionException;
import org.danielesteban.worldcupbetbackend.service.exception.PredictionLockedException;
import org.danielesteban.worldcupbetbackend.service.exception.ResourceNotFoundException;
import org.danielesteban.worldcupbetbackend.service.exception.ValidationException;
import org.danielesteban.worldcupbetbackend.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                         HttpServletRequest request) {
        log.warn("Resource not found: {} [{}]", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex,
                                                     HttpServletRequest request) {
        log.warn("Authentication failed: [{}]", request.getRequestURI());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex,
                                                          HttpServletRequest request) {
        log.warn("Access denied: {} [{}]", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex,
                                                           HttpServletRequest request) {
        log.warn("Validation error: {} [{}]", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PredictionLockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(PredictionLockedException ex,
                                                       HttpServletRequest request) {
        log.warn("Prediction locked: {} [{}]", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicatePredictionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicatePredictionException ex,
                                                          HttpServletRequest request) {
        log.warn("Duplicate prediction: {} [{}]", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleTransition(IllegalStateTransitionException ex,
                                                           HttpServletRequest request) {
        log.warn("Invalid state transition: {} [{}]", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternal(ExternalApiException ex,
                                                         HttpServletRequest request) {
        log.error("External API error: {} [{}]", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_GATEWAY, "External service error");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                       HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {} [{}]", message, request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                        HttpServletRequest request) {
        log.error("Unexpected error [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                Instant.now()
        );
        return ResponseEntity.status(status).body(body);
    }
}
