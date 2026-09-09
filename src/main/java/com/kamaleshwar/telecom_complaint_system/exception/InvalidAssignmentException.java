package com.kamaleshwar.telecom_complaint_system.exception;

/** Thrown when a complaint is assigned/reassigned to a user who is not a valid agent. */
public class InvalidAssignmentException extends RuntimeException {

    public InvalidAssignmentException(String message) {
        super(message);
    }
}
