package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.SlaRule;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import com.kamaleshwar.telecom_complaint_system.repository.SlaRuleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The single place where a complaint's SLA target is resolved.
 *
 * <p>A complaint does not own its SLA target. It owns only the facts the target is derived from
 * (category, priority and creation time); the target itself always comes from the SLA rule that
 * currently matches that category/priority pair:
 *
 * <pre>
 *   Complaint -> category + priority -> matching SlaRule -> CURRENT resolutionHours
 *   deadline  =  createdAt + CURRENT resolutionHours
 * </pre>
 *
 * <p>Because the lookup happens at read time, editing an SLA rule immediately changes the deadline -
 * and therefore the SLA status - of every existing complaint matching that rule, with no per-complaint
 * edit and no migration. Nothing is memoized here, deliberately: a cached rule table would be a window
 * in which an edited rule had not taken effect yet, which is precisely the behaviour this class exists
 * to prevent. The lookup is a single indexed read of a table with one row per category/priority pair.
 *
 * <p>{@link Complaint#getSlaDeadline()} is kept in step with this calculation (see
 * {@link #syncStoredDeadline(Complaint)}) purely as a persisted cache, so the indexed
 * {@code ORDER BY sla_deadline} repository queries keep working; it is never the source of truth for
 * SLA status.
 *
 * <p>Every SLA read path - {@link SlaDisplayService} for the UI, {@link SlaMonitoringService} for the
 * scheduled checker, {@link ComplaintService} at creation - funnels through this class, so the
 * calculation is written down exactly once.
 */
@Service
public class SlaEvaluationService {

    private final SlaRuleRepository slaRuleRepository;

    public SlaEvaluationService(SlaRuleRepository slaRuleRepository) {
        this.slaRuleRepository = slaRuleRepository;
    }

    /** The resolution target, in hours, currently configured for this complaint's category/priority. */
    public Optional<Integer> currentResolutionHours(Complaint complaint) {
        if (complaint == null) {
            return Optional.empty();
        }
        return currentResolutionHours(complaint.getCategory(), complaint.getPriority());
    }

    public Optional<Integer> currentResolutionHours(ComplaintCategory category, Priority priority) {
        if (category == null || priority == null) {
            return Optional.empty();
        }
        return slaRuleRepository.findByCategoryAndPriority(category, priority)
                .map(SlaRule::getResolutionHours);
    }

    /**
     * The deadline this complaint is measured against right now: its creation time plus the
     * resolution target of the SLA rule that currently matches it.
     *
     * <p>If no rule matches (for example the SLA monitor escalated the complaint to a priority that
     * has no rule configured), the last known deadline stored on the complaint is used, so an
     * incomplete rule table degrades gracefully instead of losing the complaint's SLA entirely.
     */
    public LocalDateTime currentDeadline(Complaint complaint) {
        if (complaint == null) {
            return null;
        }
        return currentDeadline(complaint.getCategory(), complaint.getPriority(), complaint.getCreatedAt())
                .orElseGet(complaint::getSlaDeadline);
    }

    /**
     * The one and only SLA deadline formula: {@code createdAt + the current rule's resolutionHours}.
     * Shared by the live status calculation and by {@link ComplaintService} at creation time, so the
     * arithmetic is never written down twice.
     *
     * @return empty when no rule currently covers this category/priority pair
     */
    public Optional<LocalDateTime> currentDeadline(ComplaintCategory category, Priority priority,
                                                    LocalDateTime createdAt) {
        if (createdAt == null) {
            return Optional.empty();
        }
        return currentResolutionHours(category, priority).map(hours -> deadlineFrom(createdAt, hours));
    }

    /**
     * Applies a resolution target to a creation time. The single expression of the SLA deadline rule -
     * {@link ComplaintService} uses it at creation time with the rule it has already loaded, rather
     * than repeating the arithmetic. Change how a target maps onto a deadline (business hours,
     * calendars, pauses) here and every caller follows.
     */
    public LocalDateTime deadlineFrom(LocalDateTime createdAt, int resolutionHours) {
        return createdAt.plusHours(resolutionHours);
    }

    /**
     * Refreshes the cached {@code sla_deadline} column from the currently applicable rule and
     * backfills {@code original_sla_deadline} for complaints created before that column existed.
     *
     * <p>Mutates the passed entity only; persisting is the caller's job (callers are transactional,
     * so a managed entity is flushed automatically).
     *
     * @return {@code true} if the stored deadline actually moved
     */
    public boolean syncStoredDeadline(Complaint complaint) {
        LocalDateTime current = currentDeadline(complaint);
        if (current == null) {
            return false;
        }
        if (complaint.getOriginalSlaDeadline() == null) {
            // First time we touch this complaint: whatever deadline it already carries is, by
            // definition, the one it was created with.
            complaint.setOriginalSlaDeadline(
                    complaint.getSlaDeadline() != null ? complaint.getSlaDeadline() : current);
        }
        if (current.equals(complaint.getSlaDeadline())) {
            return false;
        }
        complaint.setSlaDeadline(current);
        return true;
    }
}
