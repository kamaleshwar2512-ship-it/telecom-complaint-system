package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.ComplaintUpdate;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Role;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintRepository;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintUpdateRepository;
import com.kamaleshwar.telecom_complaint_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the automatic SLA escalation core: at-risk detection, breach detection, and -
 * critically - that running the scheduled check repeatedly never re-escalates or duplicates
 * timeline entries for a complaint that has already been flagged.
 */
@SpringBootTest
@Transactional
class SlaMonitoringServiceTest {

    @Autowired
    private SlaMonitoringService slaMonitoringService;
    @Autowired
    private ComplaintRepository complaintRepository;
    @Autowired
    private ComplaintUpdateRepository complaintUpdateRepository;
    @Autowired
    private UserRepository userRepository;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = userRepository.save(new User("Test Customer", "sla-test@test.com", "hashed", Role.CUSTOMER, null));
    }

    @Test
    void approachingDeadlineIsEscalatedAndPriorityRaised() {
        Complaint complaint = save(Priority.MEDIUM, LocalDateTime.now().minusHours(20), LocalDateTime.now().plusHours(1));

        slaMonitoringService.checkSlaDeadlines();

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElseThrow();
        assertTrue(reloaded.isApproachingNotified());
        assertFalse(reloaded.isBreached());
        assertEquals(ComplaintStatus.ESCALATED, reloaded.getStatus());
        assertEquals(Priority.HIGH, reloaded.getPriority());
    }

    @Test
    void breachedDeadlineIsEscalatedAndPriorityRaisedToCritical() {
        Complaint complaint = save(Priority.MEDIUM, LocalDateTime.now().minusHours(30), LocalDateTime.now().minusHours(1));

        slaMonitoringService.checkSlaDeadlines();

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElseThrow();
        assertTrue(reloaded.isBreached());
        assertEquals(ComplaintStatus.ESCALATED, reloaded.getStatus());
        assertEquals(Priority.CRITICAL, reloaded.getPriority());
    }

    @Test
    void repeatedSchedulerRunsDoNotDuplicateEscalationOrTimelineEntries() {
        Complaint complaint = save(Priority.MEDIUM, LocalDateTime.now().minusHours(30), LocalDateTime.now().minusHours(1));

        slaMonitoringService.checkSlaDeadlines();
        slaMonitoringService.checkSlaDeadlines();
        slaMonitoringService.checkSlaDeadlines();

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElseThrow();
        assertEquals(Priority.CRITICAL, reloaded.getPriority(), "priority must not be bumped past CRITICAL repeatedly");

        List<ComplaintUpdate> timeline = complaintUpdateRepository.findByComplaintOrderByTimestampAsc(reloaded);
        long breachEntries = timeline.stream().filter(u -> u.getNote().contains("breached")).count();
        assertEquals(1, breachEntries, "the breach should only be recorded once, not once per scheduler run");
    }

    @Test
    void resolvedComplaintsAreNotTouchedByTheScheduler() {
        Complaint complaint = save(Priority.MEDIUM, LocalDateTime.now().minusHours(30), LocalDateTime.now().minusHours(1));
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(complaint);

        slaMonitoringService.checkSlaDeadlines();

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElseThrow();
        assertFalse(reloaded.isBreached(), "a resolved complaint should never be flagged as breached");
        assertEquals(ComplaintStatus.RESOLVED, reloaded.getStatus());
    }

    private Complaint save(Priority priority, LocalDateTime createdAt, LocalDateTime slaDeadline) {
        Complaint complaint = new Complaint(customer, ComplaintCategory.NETWORK, "Test complaint", priority, createdAt, slaDeadline);
        return complaintRepository.save(complaint);
    }
}
