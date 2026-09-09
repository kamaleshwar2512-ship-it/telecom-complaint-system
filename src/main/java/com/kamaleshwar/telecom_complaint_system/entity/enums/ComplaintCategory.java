package com.kamaleshwar.telecom_complaint_system.entity.enums;

/** The kind of problem a customer is reporting. Used to look up the applicable SlaRule. */
public enum ComplaintCategory {
    NETWORK,
    BILLING,
    SIM,
    DATA,
    OTHER
}
