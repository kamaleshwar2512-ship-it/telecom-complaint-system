package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.ComplaintForm;
import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.SlaRule;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Role;
import com.kamaleshwar.telecom_complaint_system.exception.ComplaintNotFoundException;
import com.kamaleshwar.telecom_complaint_system.exception.InvalidStatusTransitionException;
import com.kamaleshwar.telecom_complaint_system.exception.SlaRuleNotFoundException;
import com.kamaleshwar.telecom_complaint_system.exception.UnauthorizedComplaintAccessException;
import com.kamaleshwar.telecom_complaint_system.repository.SlaRuleRepository;
import com.kamaleshwar.telecom_complaint_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-style tests: a real Spring context backed by the in-memory H2 test database
 * (see src/test/resources/application.properties), rolled back after every test.
 */
@SpringBootTest
@Transactional
class ComplaintServiceTest {

    @Autowired
    private ComplaintService complaintService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SlaRuleRepository slaRuleRepository;

    private User customer;
    private User agent;

    @BeforeEach
    void setUp() {
        customer = userRepository.save(new User("Test Customer", "customer@test.com", "hashed", Role.CUSTOMER, null));
        agent = userRepository.save(new User("Test Agent", "agent@test.com", "hashed", Role.AGENT, null));
        slaRuleRepository.save(new SlaRule(ComplaintCategory.NETWORK, Priority.HIGH, 24));
    }

    @Test
    void createComplaintCalculatesDeadlineFromMatchingSlaRule() {
        ComplaintForm form = new ComplaintForm();
        form.setCategory(ComplaintCategory.NETWORK);
        form.setPriority(Priority.HIGH);
        form.setDescription("No signal at all since this morning.");

        LocalDateTime before = LocalDateTime.now();
        Complaint complaint = complaintService.createComplaint(customer, form);

        assertEquals(ComplaintStatus.OPEN, complaint.getStatus());
        long hoursUntilDeadline = Duration.between(before, complaint.getSlaDeadline()).toMinutes() / 60;
        assertTrue(hoursUntilDeadline >= 23 && hoursUntilDeadline <= 24, "expected ~24h SLA window");
    }

    @Test
    void createComplaintThrowsWhenNoMatchingSlaRuleExists() {
        ComplaintForm form = new ComplaintForm();
        form.setCategory(ComplaintCategory.BILLING); // no rule seeded for BILLING in this test
        form.setPriority(Priority.LOW);
        form.setDescription("Question about my last invoice.");

        assertThrows(SlaRuleNotFoundException.class, () -> complaintService.createComplaint(customer, form));
    }

    @Test
    void agentCanProgressAssignedComplaintThroughToResolution() {
        Complaint complaint = createComplaint();
        complaintService.assign(complaint.getId(), agent.getId());

        complaintService.startProgress(complaint.getId(), agent);
        Complaint resolved = complaintService.resolve(complaint.getId(), agent, "Replaced faulty equipment.");

        assertEquals(ComplaintStatus.RESOLVED, resolved.getStatus());
        assertTrue(resolved.getResolvedAt() != null);
    }

    @Test
    void agentCannotActOnAComplaintNotAssignedToThem() {
        Complaint complaint = createComplaint(); // not assigned to anyone
        assertThrows(UnauthorizedComplaintAccessException.class,
                () -> complaintService.startProgress(complaint.getId(), agent));
    }

    @Test
    void cannotResolveAnAlreadyClosedComplaint() {
        Complaint complaint = createComplaint();
        complaintService.assign(complaint.getId(), agent.getId());
        complaintService.resolve(complaint.getId(), agent, "Fixed it.");
        complaintService.close(complaint.getId(), agent);

        assertThrows(InvalidStatusTransitionException.class,
                () -> complaintService.resolve(complaint.getId(), agent, "Trying again"));
    }

    @Test
    void customerCanReopenAResolvedComplaintButNotAnOpenOne() {
        Complaint complaint = createComplaint();
        complaintService.assign(complaint.getId(), agent.getId());
        complaintService.resolve(complaint.getId(), agent, "Fixed it.");

        Complaint reopened = complaintService.reopen(complaint.getId(), customer);
        assertEquals(ComplaintStatus.OPEN, reopened.getStatus());

        assertThrows(InvalidStatusTransitionException.class, () -> complaintService.reopen(complaint.getId(), customer));
    }

    @Test
    void customerCannotAccessAnotherCustomersComplaint() {
        Complaint complaint = createComplaint();
        User otherCustomer = userRepository.save(new User("Other Customer", "other@test.com", "hashed", Role.CUSTOMER, null));

        assertThrows(ComplaintNotFoundException.class, () -> complaintService.getForCustomer(complaint.getId(), otherCustomer));
        // the owner, meanwhile, can access it fine
        assertEquals(complaint.getId(), complaintService.getForCustomer(complaint.getId(), customer).getId());
    }

    private Complaint createComplaint() {
        ComplaintForm form = new ComplaintForm();
        form.setCategory(ComplaintCategory.NETWORK);
        form.setPriority(Priority.HIGH);
        form.setDescription("No signal at all since this morning.");
        return complaintService.createComplaint(customer, form);
    }
}
