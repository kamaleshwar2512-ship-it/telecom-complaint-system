package com.kamaleshwar.telecom_complaint_system.util;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Pure, framework-free helpers for turning an SLA deadline into something a human can read.
 * Kept separate from any Spring bean so it is trivial to unit test.
 */
public final class SlaCountdownFormatter {

    private SlaCountdownFormatter() {
    }

    /**
     * Renders the time remaining until {@code deadline} as, e.g. "23h 15m remaining",
     * or "BREACHED" once {@code now} is past the deadline.
     */
    public static String format(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            return "N/A";
        }
        if (!now.isBefore(deadline)) {
            return "BREACHED";
        }
        Duration remaining = Duration.between(now, deadline);
        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();
        return hours + "h " + minutes + "m remaining";
    }

    /** True once {@code now} has reached or passed the deadline. */
    public static boolean isBreached(LocalDateTime deadline, LocalDateTime now) {
        return !now.isBefore(deadline);
    }

    /**
     * True once {@code now} is within {@code atRiskThresholdHours} of the deadline but has not
     * passed it yet.
     */
    public static boolean isApproaching(LocalDateTime deadline, LocalDateTime now, int atRiskThresholdHours) {
        if (isBreached(deadline, now)) {
            return false;
        }
        LocalDateTime atRiskFrom = deadline.minusHours(atRiskThresholdHours);
        return !now.isBefore(atRiskFrom);
    }
}
