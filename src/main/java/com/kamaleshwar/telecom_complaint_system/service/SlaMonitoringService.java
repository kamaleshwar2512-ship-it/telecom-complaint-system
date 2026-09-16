package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The automated core of the application. A scheduled job (see {@link #checkSlaDeadlines()}) walks
 * every complaint that is not yet resolved/closed, re-resolves its SLA target from the rule that
 * currently applies, and escalates anything that is at risk of, or has already, breached.
 *
 * <p>Idempotency: {@link Complaint#isApproachingNotified()} and {@link Complaint#isBreached()} mark
 * that the escalation for a given condition has already been performed, so priority is never bumped
 * twice and no duplicate timeline entries are created. They are markers, not status - the live
 * status always comes from {@link SlaDisplayService}, which reads the current rule.
 *
 * <p>Dynamic recalculation: because the target is resolved from the rule rather than frozen onto the
 * complaint, relaxing a rule can move a complaint back inside its deadline. When that happens this
 * service clears the now-stale markers ({@link #clearStaleMarkers}) and hands the status back
 * ({@link #deEscalateIfBackInsideTarget}), so the complaint drops out of the breach report, the
 * dashboard counters and the ESCALATED filter, and so a later breach of the <em>new</em> deadline can
 * still escalate. The cached {@code sla_deadline} column is rewritten in the same pass, keeping the
 * indexed {@code ORDER BY sla_deadline} queries meaningful.
 *
 * <p>Status therefore follows the recalculated deadline in both directions: past the current target a
 * complaint is ESCALATED, back inside it the escalation is withdrawn and it is OPEN again. Both happen
 * on the scheduled sweep and, for the complaints a rule edit affects, the moment that rule is saved.
 */
@Service
public class SlaMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitoringService.class);

    private static final List<ComplaintStatus> CLOSED_OUT_STATUSES = List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);

    private static final DateTimeFormatter TIMELINE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final ComplaintRepository complaintRepository;
    private final SlaDisplayService slaDisplayService;
    private final SlaEvaluationService slaEvaluationService;
    private final ComplaintUpdateService complaintUpdateService;

    public SlaMonitoringService(ComplaintRepository complaintRepository, SlaDisplayService slaDisplayService,
                                 SlaEvaluationService slaEvaluationService,
                                 ComplaintUpdateService complaintUpdateService) {
        this.complaintRepository = complaintRepository;
        this.slaDisplayService = slaDisplayService;
        this.slaEvaluationService = slaEvaluationService;
        this.complaintUpdateService = complaintUpdateService;
    }

    /**
     * Runs periodically (interval configured via {@code sla.monitoring.fixed-rate-ms}, default 5 minutes).
     * Intentionally thin: it just loops and delegates each complaint's evaluation to {@link #evaluate(Complaint)}.
     * The whole batch runs in one transaction (this method is the public entry point the scheduler
     * calls, so {@code @Transactional} here is honored by Spring's proxy - unlike on a private/self-invoked method).
     */
    @Scheduled(fixedRateString = "${sla.monitoring.fixed-rate-ms:300000}")
    @Transactional
    public void checkSlaDeadlines() {
        List<Complaint> activeComplaints = complaintRepository.findByStatusNotInOrderBySlaDeadlineAsc(CLOSED_OUT_STATUSES);
        log.debug("SLA monitor: checking {} active complaint(s)", activeComplaints.size());
        for (Complaint complaint : activeComplaints) {
            evaluate(complaint);
        }
    }

    /**
     * Re-evaluates every open complaint governed by one category/priority rule. Called by
     * {@link SlaRuleService} right after a rule is saved so the breach report and dashboard counters
     * reflect the new target immediately, rather than waiting for the next scheduled sweep. (The SLA
     * status shown on screen is computed live and does not depend on this running at all.)
     *
     * <p>Only complaints that are still open are revisited; ones already resolved or closed keep the
     * breach record they earned under the rule in force at the time.
     */
    @Transactional
    public int recalculateForRule(ComplaintCategory category, Priority priority) {
        List<Complaint> affected = complaintRepository
                .findByCategoryAndPriorityAndStatusNotIn(category, priority, CLOSED_OUT_STATUSES);
        for (Complaint complaint : affected) {
            evaluate(complaint);
        }
        log.info("SLA rule {}/{} changed: re-evaluated {} open complaint(s)", category, priority, affected.size());
        return affected.size();
    }

    void evaluate(Complaint complaint) {
        // 1. Pull the target from the rule that applies right now and refresh the cached column.
        LocalDateTime previousDeadline = complaint.getSlaDeadline();
        boolean dirty = slaEvaluationService.syncStoredDeadline(complaint);
        if (dirty && previousDeadline != null) {
            complaintUpdateService.addSystemEntry(complaint,
                    "SLA target recalculated from the current SLA rule for " + complaint.getCategory() + " / "
                            + complaint.getPriority() + " (" + slaEvaluationService.currentResolutionHours(complaint)
                            .map(h -> h + " hour(s)").orElse("no matching rule") + "). Deadline moved from "
                            + format(previousDeadline) + " to " + format(complaint.getSlaDeadline()) + ".");
        }

        // 2. Decide the live state from that target, not from the stored markers.
        boolean breachedNow = slaDisplayService.isBreached(complaint);
        boolean approachingNow = slaDisplayService.isApproaching(complaint);

        dirty |= clearStaleMarkers(complaint, breachedNow, approachingNow);

        // 3. Escalate, at most once per condition.
        if (!complaint.isBreached() && breachedNow) {
            escalateForBreach(complaint);
            return;
        }
        if (!complaint.isApproachingNotified() && approachingNow) {
            escalateForApproachingDeadline(complaint);
            return;
        }

        // 4. Nothing is escalating it any more - hand the status back.
        dirty |= deEscalateIfBackInsideTarget(complaint, breachedNow, approachingNow);

        if (dirty) {
            complaintRepository.save(complaint);
        }
    }

    /**
     * The mirror image of {@link #escalateStatusIfOpenOrInProgress}: once the current SLA rule puts a
     * complaint back inside its target, the escalation that the SLA monitor applied is withdrawn and
     * the complaint returns to OPEN. Relaxing a rule therefore moves the Status column, not just the
     * SLA badge, and it does so as soon as the rule is saved because {@link #recalculateForRule} runs
     * this same evaluation synchronously.
     *
     * <p>Only ESCALATED is reverted, and only to OPEN. ESCALATED is a status nothing but this service
     * ever sets, so withdrawing it cannot discard a human decision - whereas IN_PROGRESS is an agent
     * telling us they have picked the complaint up, and a rule edit is no reason to forget that. A
     * complaint being actively worked therefore keeps IN_PROGRESS while it is inside its target, and
     * is still escalated by the branches above if it later breaches.
     *
     * @return {@code true} if the status was changed
     */
    private boolean deEscalateIfBackInsideTarget(Complaint complaint, boolean breachedNow, boolean approachingNow) {
        if (breachedNow || approachingNow) {
            return false;
        }
        if (complaint.getStatus() != ComplaintStatus.ESCALATED) {
            return false;
        }
        complaint.setStatus(ComplaintStatus.OPEN);
        complaintUpdateService.addSystemEntry(complaint,
                "Back inside the SLA target currently configured for " + complaint.getCategory() + " / "
                        + complaint.getPriority() + ". Status returned to OPEN.");
        log.info("Complaint {} is back inside its SLA target and was de-escalated to OPEN", complaint.getId());
        return true;
    }

    /**
     * Drops escalation markers that the current rule no longer justifies - the case where an SLA rule
     * was relaxed and a complaint that had breached is back inside its deadline.
     *
     * @return {@code true} if anything was cleared
     */
    private boolean clearStaleMarkers(Complaint complaint, boolean breachedNow, boolean approachingNow) {
        boolean cleared = false;
        if (complaint.isBreached() && !breachedNow) {
            complaint.setBreached(false);
            cleared = true;
            complaintUpdateService.addSystemEntry(complaint,
                    "No longer in breach: the complaint is back inside the SLA target currently configured for "
                            + complaint.getCategory() + " / " + complaint.getPriority() + ".");
            log.info("Complaint {} is no longer breached under the current SLA rule", complaint.getId());
        }
        if (complaint.isApproachingNotified() && !approachingNow && !breachedNow) {
            complaint.setApproachingNotified(false);
            cleared = true;
        }
        return cleared;
    }

    private void escalateForBreach(Complaint complaint) {
        complaint.setBreached(true);
        complaint.setPriority(Priority.CRITICAL);
        escalateStatusIfOpenOrInProgress(complaint);
        // Priority selects the rule, so raising it can select a different target: refresh the mirror
        // in the same pass, or the countdown on screen would disagree with the badge until the next sweep.
        slaEvaluationService.syncStoredDeadline(complaint);
        complaintRepository.save(complaint);
        complaintUpdateService.addSystemEntry(complaint,
                "SLA deadline breached. Complaint automatically escalated and priority raised to CRITICAL.");
        log.info("Complaint {} breached its SLA deadline and was escalated", complaint.getId());
    }

    private void escalateForApproachingDeadline(Complaint complaint) {
        complaint.setApproachingNotified(true);
        Priority previousPriority = complaint.getPriority();
        complaint.setPriority(previousPriority.escalateOneLevel());
        escalateStatusIfOpenOrInProgress(complaint);
        // See escalateForBreach: the new priority may map to a different rule, so re-mirror the target.
        slaEvaluationService.syncStoredDeadline(complaint);
        complaintRepository.save(complaint);

        String priorityNote = previousPriority != complaint.getPriority()
                ? " and priority raised to " + complaint.getPriority() + "."
                : ".";
        complaintUpdateService.addSystemEntry(complaint,
                "Approaching SLA deadline (within " + slaDisplayService.getAtRiskThresholdHours()
                        + " hour(s)). Complaint automatically escalated" + priorityNote);
        log.info("Complaint {} is approaching its SLA deadline and was escalated", complaint.getId());
    }

    private void escalateStatusIfOpenOrInProgress(Complaint complaint) {
        if (complaint.getStatus() == ComplaintStatus.OPEN || complaint.getStatus() == ComplaintStatus.IN_PROGRESS) {
            complaint.setStatus(ComplaintStatus.ESCALATED);
        }
    }

    private static String format(LocalDateTime moment) {
        return moment == null ? "n/a" : TIMELINE_FORMAT.format(moment);
    }
}
