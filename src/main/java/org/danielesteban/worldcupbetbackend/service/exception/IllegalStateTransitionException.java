package org.danielesteban.worldcupbetbackend.service.exception;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }

    public IllegalStateTransitionException(String currentState, String targetState) {
        super("Invalid state transition from " + currentState + " to " + targetState);
    }
}
