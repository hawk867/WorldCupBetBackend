package org.danielesteban.worldcupbetbackend.service.exception;

public class ExternalApiException extends RuntimeException {

    private final int statusCode;

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public ExternalApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
