package com.kamaleshwar.telecom_complaint_system.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaCountdownFormatterTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    void formatShowsHoursAndMinutesRemaining() {
        LocalDateTime deadline = now.plusHours(23).plusMinutes(15);
        assertEquals("23h 15m remaining", SlaCountdownFormatter.format(deadline, now));
    }

    @Test
    void formatShowsBreachedOncePastDeadline() {
        LocalDateTime deadline = now.minusMinutes(1);
        assertEquals("BREACHED", SlaCountdownFormatter.format(deadline, now));
    }

    @Test
    void formatShowsBreachedExactlyAtDeadline() {
        assertEquals("BREACHED", SlaCountdownFormatter.format(now, now));
    }

    @Test
    void isBreachedIsFalseBeforeDeadline() {
        assertFalse(SlaCountdownFormatter.isBreached(now.plusMinutes(1), now));
    }

    @Test
    void isBreachedIsTrueAtOrAfterDeadline() {
        assertTrue(SlaCountdownFormatter.isBreached(now, now));
        assertTrue(SlaCountdownFormatter.isBreached(now.minusMinutes(1), now));
    }

    @Test
    void isApproachingIsTrueWithinThresholdButNotYetBreached() {
        LocalDateTime deadline = now.plusHours(2);
        assertTrue(SlaCountdownFormatter.isApproaching(deadline, now, 4));
    }

    @Test
    void isApproachingIsFalseWhenPlentyOfTimeRemains() {
        LocalDateTime deadline = now.plusHours(10);
        assertFalse(SlaCountdownFormatter.isApproaching(deadline, now, 4));
    }

    @Test
    void isApproachingIsFalseOnceAlreadyBreached() {
        LocalDateTime deadline = now.minusHours(1);
        assertFalse(SlaCountdownFormatter.isApproaching(deadline, now, 4));
    }
}
