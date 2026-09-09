package com.kamaleshwar.telecom_complaint_system.exception;

/** Thrown when a logged-in user tries to view or act on a complaint that is not theirs. */
public class UnauthorizedComplaintAccessException extends RuntimeException {

    public UnauthorizedComplaintAccessException(String message) {
        super(message);
    }
}
