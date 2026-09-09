package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The automated core of the application. A scheduled job (see {@link #checkSlaDeadlines()}) walks
 * every complaint that is not yet resolved/closed and escalates anything that is at risk of, or has
 * already, breached its SLA deadline.
 *
 * <p>Idempotency: {@link Complaint#isApproachingNotified()} and {@link Complaint#isBreached()} are
 * each set exactly once. Every scheduler run re-checks all open complaints, but a complaint that has
 * already been flagged for a given condition is skipped for that condition on later runs, so priority
 * is never bumped twice and no duplicate timeline entries are created.
 */
@Service
public class SlaMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitoringService.class);

    private static final List<ComplaintStatus> CLOSED_OUT_STATUSES = List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);

    private final ComplaintRepository complaintRepository;
    private final SlaDisplayService slaDisplayService;
    private final ComplaintUpdateService complaintUpdateService;

    public SlaMonitoringService(ComplaintRepository complaintRepository, SlaDisplayService slaDisplayService,
                                 ComplaintUpdateService complaintUpdateService) {
        this.complaintRepository = complaintRepository;
        this.slaDisplayService = slaDisplayService;
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

    void evaluate(Complaint complaint) {
        if (!complaint.isBreached() && slaDisplayService.isBreached(complaint)) {
            escalateForBreach(complaint);
        } else if (!complaint.isApproachingNotified() && slaDisplayService.isApproaching(complaint)) {
            escalateForApproachingDeadline(complaint);
        }
    }

    private void escalateForBreach(Complaint complaint) {
        complaint.setBreached(true);
        complaint.setPriority(Priority.CRITICAL);
        escalateStatusIfOpenOrInProgress(complaint);
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
}
