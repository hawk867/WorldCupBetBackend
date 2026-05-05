package org.danielesteban.worldcupbetbackend.service.exception;

public class PredictionLockedException extends RuntimeException {

    public PredictionLockedException(String message) {
        super(message);
    }
}
