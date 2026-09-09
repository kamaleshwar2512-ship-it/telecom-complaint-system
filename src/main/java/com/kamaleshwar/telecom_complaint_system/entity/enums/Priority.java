package com.kamaleshwar.telecom_complaint_system.entity.enums;

/** Complaint priority, from least to most urgent. Combined with category to look up an SlaRule. */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * Returns the next priority level up, used by the SLA monitor to bump priority
     * when a complaint is escalated. CRITICAL is the ceiling - it stays CRITICAL.
     */
    public Priority escalateOneLevel() {
        return switch (this) {
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH -> CRITICAL;
            case CRITICAL -> CRITICAL;
        };
    }
}
