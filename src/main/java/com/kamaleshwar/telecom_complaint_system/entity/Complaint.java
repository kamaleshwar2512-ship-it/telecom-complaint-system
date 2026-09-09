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
 * <p>{@link #approachingNotified} and {@link #breached} are idempotency flags: the scheduled
 * {@code SlaMonitoringService} sets each of them at most once, so re-running the scheduler never
 * re-escalates or re-flags a complaint that has already been handled.
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

    @Column(name = "sla_deadline", nullable = false)
    private LocalDateTime slaDeadline;

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
    }
}
