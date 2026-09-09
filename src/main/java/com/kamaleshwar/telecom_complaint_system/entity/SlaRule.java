package com.kamaleshwar.telecom_complaint_system.entity;

import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines how many hours a complaint of a given category/priority combination has
 * to be resolved. This is what {@link Complaint#getSlaDeadline()} is calculated from
 * at creation time.
 */
@Entity
@Table(name = "sla_rules", uniqueConstraints = @UniqueConstraint(columnNames = {"category", "priority"}))
@Getter
@Setter
@NoArgsConstructor
public class SlaRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    /** How many hours after creation a complaint of this category/priority must be resolved by. */
    @Column(nullable = false)
    private Integer resolutionHours;

    public SlaRule(ComplaintCategory category, Priority priority, Integer resolutionHours) {
        this.category = category;
        this.priority = priority;
        this.resolutionHours = resolutionHours;
    }
}
