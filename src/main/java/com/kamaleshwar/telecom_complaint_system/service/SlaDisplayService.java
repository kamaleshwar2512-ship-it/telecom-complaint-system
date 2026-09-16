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
 *
 * <p>The deadline every method here works from is resolved live by {@link SlaEvaluationService}
 * from the SLA rule that currently matches the complaint's category and priority - never from a
 * value frozen onto the complaint at creation. Editing an SLA rule therefore changes what this
 * service reports for existing complaints on the very next page render: a complaint 48 hours old
 * reads BREACHED under a 48-hour rule and ON TRACK the moment that rule is raised to 90 hours.
 */
@Service
public class SlaDisplayService {

    /** How many hours before the deadline a complaint is considered "at risk" / approaching breach. */
    private final int atRiskThresholdHours;

    private final SlaEvaluationService slaEvaluationService;

    public SlaDisplayService(@Value("${sla.at-risk-threshold-hours:4}") int atRiskThresholdHours,
                              SlaEvaluationService slaEvaluationService) {
        this.atRiskThresholdHours = atRiskThresholdHours;
        this.slaEvaluationService = slaEvaluationService;
    }

    public int getAtRiskThresholdHours() {
        return atRiskThresholdHours;
    }

    /**
     * The deadline this complaint is currently measured against. Exposed for the templates, which
     * previously read {@code complaint.slaDeadline} directly and must now go through the live
     * calculation for the countdown and progress meter to follow rule edits.
     */
    public LocalDateTime currentDeadline(Complaint complaint) {
        return slaEvaluationService.currentDeadline(complaint);
    }

    /** The resolution target in hours currently configured for this complaint, or {@code null} if no rule matches. */
    public Integer currentTargetHours(Complaint complaint) {
        return slaEvaluationService.currentResolutionHours(complaint).orElse(null);
    }

    public String remainingTime(Complaint complaint) {
        return SlaCountdownFormatter.format(currentDeadline(complaint), LocalDateTime.now());
    }

    public boolean isBreached(Complaint complaint) {
        LocalDateTime deadline = currentDeadline(complaint);
        return deadline != null && SlaCountdownFormatter.isBreached(deadline, LocalDateTime.now());
    }

    public boolean isApproaching(Complaint complaint) {
        LocalDateTime deadline = currentDeadline(complaint);
        return deadline != null
                && SlaCountdownFormatter.isApproaching(deadline, LocalDateTime.now(), atRiskThresholdHours);
    }

    /**
     * "green" | "amber" | "red" | "none" (resolved/closed complaints are not time-pressured).
     *
     * <p>Derived purely from the currently applicable deadline. The complaint's {@code breached} /
     * {@code approachingNotified} flags are deliberately <em>not</em> consulted here: they are
     * idempotency markers for {@link SlaMonitoringService}, and letting them win would pin a
     * complaint to red for good even after its SLA rule had been relaxed past the elapsed time.
     */
    public String urgencyLevel(Complaint complaint) {
        if (isClosedOut(complaint)) {
            return "none";
        }
        if (isBreached(complaint)) {
            return "red";
        }
        if (isApproaching(complaint)) {
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
