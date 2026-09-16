package com.kamaleshwar.telecom_complaint_system.entity;

import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A single customer complaint and everything needed to track it against its SLA deadline.
 *
 * <p>A complaint deliberately does <em>not</em> own its SLA target. It owns only the facts the
 * target is derived from - {@link #category}, {@link #priority} and {@link #createdAt} - and the
 * target itself is resolved at read time from whichever SLA rule currently matches that
 * category/priority pair (see {@code SlaEvaluationService}). Editing an SLA rule therefore changes
 * the SLA status of every existing complaint it matches, with no per-complaint edit.
 *
 * <p>{@link #approachingNotified} and {@link #breached} are idempotency flags: the scheduled
 * {@code SlaMonitoringService} sets each of them at most once per condition, so re-running the
 * scheduler never re-escalates a complaint that has already been handled. They record
 * <em>"the escalation for this condition has already been performed"</em>, not
 * <em>"this complaint is breached"</em> - the live status always comes from the current rule.
 */
@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaint_status", columnList = "status"),
        @Index(name = "idx_complaint_sla_deadline", columnList = "sla_deadline")
})
@Getter
@Setter
@NoArgsConstructor
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintCategory category;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Cached copy of the <em>currently applicable</em> deadline, i.e. {@link #createdAt} plus the
     * resolution target of the SLA rule that matches this complaint right now.
     *
     * <p>This is a derived value, not a source of truth: {@code SlaEvaluationService} recomputes it
     * from the live rule and writes it back whenever the rule changes. It is persisted (and indexed)
     * only so the {@code ORDER BY sla_deadline} repository queries and the SLA monitor's sweep stay
     * cheap. Never read it to decide SLA status - go through {@code SlaEvaluationService} or
     * {@code SlaDisplayService} instead.
     */
    @Column(name = "sla_deadline", nullable = false)
    private LocalDateTime slaDeadline;

    /**
     * Historical record of the deadline this complaint was raised under, kept for audit: it is set
     * once, at creation, and never recalculated when an SLA rule changes. Nullable because
     * complaints created before this column existed are backfilled lazily, on the first SLA
     * recalculation that touches them.
     */
    @Column(name = "original_sla_deadline")
    private LocalDateTime originalSlaDeadline;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    /** Set once the SLA monitor has already escalated this complaint for approaching its deadline. */
    @Column(name = "approaching_notified", nullable = false)
    private boolean approachingNotified = false;

    /** Set once the SLA monitor has already flagged this complaint as having breached its deadline. */
    @Column(name = "breached", nullable = false)
    private boolean breached = false;

    public Complaint(User customer, ComplaintCategory category, String description,
                      Priority priority, LocalDateTime createdAt, LocalDateTime slaDeadline) {
        this.customer = customer;
        this.category = category;
        this.description = description;
        this.priority = priority;
        this.status = ComplaintStatus.OPEN;
        this.createdAt = createdAt;
        this.slaDeadline = slaDeadline;
        this.originalSlaDeadline = slaDeadline;
    }
}
