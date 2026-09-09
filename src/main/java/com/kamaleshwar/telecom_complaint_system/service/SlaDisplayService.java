package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.util.SlaCountdownFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Turns a complaint's SLA deadline into what the UI needs: a human-readable countdown and an
 * urgency level (green/amber/red) for badges. Used both by Thymeleaf templates (as a Spring bean,
 * e.g. {@code ${@slaDisplayService.remainingTime(complaint)}}) and by {@link SlaMonitoringService},
 * so the same at-risk threshold drives both the on-screen color and the automatic escalation.
 */
@Service
public class SlaDisplayService {

    /** How many hours before the deadline a complaint is considered "at risk" / approaching breach. */
    private final int atRiskThresholdHours;

    public SlaDisplayService(@Value("${sla.at-risk-threshold-hours:4}") int atRiskThresholdHours) {
        this.atRiskThresholdHours = atRiskThresholdHours;
    }

    public int getAtRiskThresholdHours() {
        return atRiskThresholdHours;
    }

    public String remainingTime(Complaint complaint) {
        return SlaCountdownFormatter.format(complaint.getSlaDeadline(), LocalDateTime.now());
    }

    public boolean isBreached(Complaint complaint) {
        return SlaCountdownFormatter.isBreached(complaint.getSlaDeadline(), LocalDateTime.now());
    }

    public boolean isApproaching(Complaint complaint) {
        return SlaCountdownFormatter.isApproaching(complaint.getSlaDeadline(), LocalDateTime.now(), atRiskThresholdHours);
    }

    /** "green" | "amber" | "red" | "none" (resolved/closed complaints are not time-pressured). */
    public String urgencyLevel(Complaint complaint) {
        if (isClosedOut(complaint)) {
            return "none";
        }
        if (complaint.isBreached() || isBreached(complaint)) {
            return "red";
        }
        if (complaint.isApproachingNotified() || isApproaching(complaint)) {
            return "amber";
        }
        return "green";
    }

    /** Bootstrap badge class matching {@link #urgencyLevel(Complaint)}. */
    public String badgeClass(Complaint complaint) {
        return switch (urgencyLevel(complaint)) {
            case "red" -> "bg-danger";
            case "amber" -> "bg-warning text-dark";
            case "green" -> "bg-success";
            default -> "bg-secondary";
        };
    }

    public String urgencyLabel(Complaint complaint) {
        return switch (urgencyLevel(complaint)) {
            case "red" -> "Breached";
            case "amber" -> "At Risk";
            case "green" -> "On Track";
            default -> "Closed";
        };
    }

    private boolean isClosedOut(Complaint complaint) {
        return complaint.getStatus() == ComplaintStatus.RESOLVED || complaint.getStatus() == ComplaintStatus.CLOSED;
    }
}
