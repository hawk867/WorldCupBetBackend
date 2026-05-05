package org.danielesteban.worldcupbetbackend.service.exception;

public class DuplicatePredictionException extends RuntimeException {

    public DuplicatePredictionException(String message) {
        super(message);
    }
}
