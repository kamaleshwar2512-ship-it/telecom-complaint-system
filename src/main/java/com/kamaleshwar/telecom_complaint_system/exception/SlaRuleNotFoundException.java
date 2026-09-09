package com.kamaleshwar.telecom_complaint_system.exception;

import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;

/** Thrown when a complaint is being created but no SLA rule exists for its category/priority combination. */
public class SlaRuleNotFoundException extends RuntimeException {

    public SlaRuleNotFoundException(ComplaintCategory category, Priority priority) {
        super("No SLA rule is configured for category " + category + " and priority " + priority
                + ". Please ask an administrator to add one.");
    }
}
