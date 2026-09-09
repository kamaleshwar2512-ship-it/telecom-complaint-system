package com.kamaleshwar.telecom_complaint_system.exception;

/** Thrown when the service layer is asked to move a complaint into a status it cannot reach from its current one. */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
