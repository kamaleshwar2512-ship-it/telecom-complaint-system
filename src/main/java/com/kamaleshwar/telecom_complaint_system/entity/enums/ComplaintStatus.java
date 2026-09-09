package com.kamaleshwar.telecom_complaint_system.entity.enums;

/**
 * Lifecycle states of a complaint.
 * Typical flow: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED.
 * OPEN/IN_PROGRESS can also move to ESCALATED automatically when SLA risk is detected.
 * A RESOLVED complaint can be reopened back to OPEN by the customer.
 */
public enum ComplaintStatus {
    OPEN,
    IN_PROGRESS,
    ESCALATED,
    RESOLVED,
    CLOSED
}
