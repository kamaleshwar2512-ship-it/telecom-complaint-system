package com.kamaleshwar.telecom_complaint_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One entry in a complaint's activity timeline: creation, a status change, an agent note,
 * an assignment change, an automatic SLA escalation, or a resolution.
 * {@link #agent} is null for system-generated entries (e.g. automatic escalation).
 */
@Entity
@Table(name = "complaint_updates", indexes = @Index(name = "idx_update_complaint", columnList = "complaint_id"))
@Getter
@Setter
@NoArgsConstructor
public class ComplaintUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    /** The agent (or admin) who made this update. Null for customer actions and automated system entries. */
    @ManyToOne
    @JoinColumn(name = "agent_id")
    private User agent;

    @Column(nullable = false, length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public ComplaintUpdate(Complaint complaint, User agent, String note, LocalDateTime timestamp) {
        this.complaint = complaint;
        this.agent = agent;
        this.note = note;
        this.timestamp = timestamp;
    }
}
