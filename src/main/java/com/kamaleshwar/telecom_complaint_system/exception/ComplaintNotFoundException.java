package com.kamaleshwar.telecom_complaint_system.exception;

/** Thrown when a complaint id does not exist. */
public class ComplaintNotFoundException extends RuntimeException {

    public ComplaintNotFoundException(Long complaintId) {
        super("No complaint found with id " + complaintId);
    }
}
