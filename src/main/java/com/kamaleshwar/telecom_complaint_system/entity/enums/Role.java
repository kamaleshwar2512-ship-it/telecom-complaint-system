package com.kamaleshwar.telecom_complaint_system.entity.enums;

/**
 * The three user roles supported by the system. Spring Security authorities
 * are derived from this enum as "ROLE_" + name(), e.g. ROLE_CUSTOMER.
 */
public enum Role {
    CUSTOMER,
    AGENT,
    ADMIN
}
