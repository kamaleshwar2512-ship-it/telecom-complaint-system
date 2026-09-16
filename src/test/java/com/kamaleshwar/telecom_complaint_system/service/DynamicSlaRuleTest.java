package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.SlaRuleForm;
import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.SlaRule;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Role;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintRepository;
import com.kamaleshwar.telecom_complaint_system.repository.SlaRuleRepository;
import com.kamaleshwar.telecom_complaint_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SLA target a complaint is judged against is not frozen at creation: it is resolved, every time
 * it is needed, from the SLA rule that currently matches the complaint's category and priority.
 * These tests pin that down - an existing complaint's SLA status must follow a rule edit, in both
 * directions, without the complaint itself being touched.
 */
@SpringBootTest
@Transactional
class DynamicSlaRuleTest {

    @Autowired
    private SlaRuleService slaRuleService;
    @Autowired
    private SlaEvaluationService slaEvaluationService;
    @Autowired
    private SlaDisplayService slaDisplayService;
    @Autowired
    private SlaMonitoringService slaMonitoringService;
    @Autowired
    private ComplaintRepository complaintRepository;
    @Autowired
    private SlaRuleRepository slaRuleRepository;
    @Autowired
    private UserRepository userRepository;

    private User customer;
    private SlaRule dataCritical;

    @BeforeEach
    void setUp() {
        customer = userRepository.save(new User("Rule Test Customer", "dynamic-sla@test.com", "hashed", Role.CUSTOMER, null));
        dataCritical = slaRuleRepository.save(new SlaRule(ComplaintCategory.DATA, Priority.CRITICAL, 48));
    }

    @Test
    void relaxingTheRuleMovesAnExistingBreachedComplaintBackOnTrack() {
        Complaint complaint = complaintCreatedHoursAgo(48);
        assertEquals("red", slaDisplayService.urgencyLevel(complaint), "48h elapsed against a 48h target is a breach");

        slaMonitoringService.checkSlaDeadlines();
        assertTrue(complaintRepository.findById(complaint.getId()).orElseThrow().isBreached());

        updateRuleTo(90);

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElseThrow();
        assertEquals(90, slaDisplayService.currentTargetHours(reloaded));
        assertEquals("green", slaDisplayService.urgencyLevel(reloaded), "48h elapsed against a 90h target is on track");
        assertEquals("On Track", slaDisplayService.urgencyLabel(reloaded));
        assertFalse(reloaded.isBreached(), "the stale breach marker must be cleared so breach reports agree with the badge");
        assertEquals(ComplaintStatus.OPEN, reloaded.getStatus(),
                "back inside the current target, the SLA monitor's escalation is withdrawn");
    }

    @Test
    void tighteningTheRuleBreachesAnExistingComplaint() {
        Complaint complaint = complaintCreatedHoursAgo(10);
        assertEquals("green", slaDisplayService.urgencyLevel(complaint));

        updateRuleTo(5);

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElseThrow();
        assertEquals(5, slaDisplayService.currentTargetHours(reloaded));
        assertEquals("red", slaDisplayService.urgencyLevel(reloaded));
        assertTrue(reloaded.isBreached(), "an existing complaint must be escalated when a rule edit puts it past its target");
        assertEquals(ComplaintStatus.ESCALATED, reloaded.getStatus(),
                "past the current target, the Status column must read ESCALATED");
    }

    @Test
    void statusFollowsTheRuleInBothDirectionsAcrossSuccessiveEdits() {
        Complaint complaint = complaintCreatedHoursAgo(48);

        updateRuleTo(90);
        assertEquals(ComplaintStatus.OPEN,
                complaintRepository.findById(complaint.getId()).orElseThrow().getStatus());

        updateRuleTo(10);
        assertEquals(ComplaintStatus.ESCALATED,
                complaintRepository.findById(complaint.getId()).orElseThrow().getStatus());

        updateRuleTo(200);
        assertEquals(ComplaintStatus.OPEN,
                complaintRepository.findById(complaint.getId()).orElseThrow().getStatus());
    }

    @Test
    void anAgentsInProgressStatusIsKeptWhileTheComplaintIsInsideItsTarget() {
        Complaint complaint = complaintCreatedHoursAgo(10);
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        complaintRepository.save(complaint);

        updateRuleTo(90); // comfortably inside the target

        assertEquals(ComplaintStatus.IN_PROGRESS,
                complaintRepository.findById(complaint.getId()).orElseThrow().getStatus(),
                "a rule edit must not discard an agent telling us they have picked the complaint up");

        updateRuleTo(2); // now past the target

        assertEquals(ComplaintStatus.ESCALATED,
                complaintRepository.findById(complaint.getId()).orElseThrow().getStatus(),
                "a breached complaint still escalates even if an agent was working it");
    }

    @Test
    void theComplaintItselfIsNotRewritten_onlyItsDerivedDeadline() {
        Complaint complaint = complaintCreatedHoursAgo(48);
        LocalDateTime createdAt = complaint.getCreatedAt();
        LocalDateTime originalDeadline = complaint.getSlaDeadline();

        updateRuleTo(90);

        Complaint reloaded = complaintRepository.findById(complaint.getId()).orElseThrow();
        assertEquals(createdAt, reloaded.getCreatedAt());
        assertEquals(ComplaintCategory.DATA, reloaded.getCategory());
        assertEquals(Priority.CRITICAL, reloaded.getPriority());
        assertEquals(createdAt.plusHours(90), reloaded.getSlaDeadline(), "the cached deadline follows the current rule");
        assertEquals(originalDeadline, reloaded.getOriginalSlaDeadline(), "the creation-time target is kept for audit");
    }

    @Test
    void rulesAreMatchedByCategoryAndPriority_othersAreUnaffected() {
        slaRuleRepository.save(new SlaRule(ComplaintCategory.NETWORK, Priority.HIGH, 72));
        Complaint networkHigh = complaintRepository.save(newComplaint(ComplaintCategory.NETWORK, Priority.HIGH, 10, 72));

        updateRuleTo(1); // DATA / CRITICAL only

        Complaint reloaded = complaintRepository.findById(networkHigh.getId()).orElseThrow();
        assertEquals(72, slaDisplayService.currentTargetHours(reloaded), "a different category/priority keeps its own rule");
        assertEquals("green", slaDisplayService.urgencyLevel(reloaded));
    }

    @Test
    void aComplaintWithNoMatchingRuleFallsBackToItsLastKnownDeadline() {
        Complaint orphan = complaintRepository.save(newComplaint(ComplaintCategory.OTHER, Priority.LOW, 2, 12));

        assertNull(slaDisplayService.currentTargetHours(orphan), "no rule is configured for OTHER / LOW");
        assertEquals(orphan.getSlaDeadline(), slaEvaluationService.currentDeadline(orphan));
        assertEquals("green", slaDisplayService.urgencyLevel(orphan));
    }

    private Complaint complaintCreatedHoursAgo(int hoursAgo) {
        return complaintRepository.save(newComplaint(ComplaintCategory.DATA, Priority.CRITICAL, hoursAgo, 48));
    }

    private Complaint newComplaint(ComplaintCategory category, Priority priority, int hoursAgo, int targetHours) {
        LocalDateTime createdAt = LocalDateTime.now().minusHours(hoursAgo);
        return new Complaint(customer, category, "Dynamic SLA test complaint", priority,
                createdAt, createdAt.plusHours(targetHours));
    }

    /** Edits the DATA/CRITICAL rule the way an admin would, through the same service the controller uses. */
    private void updateRuleTo(int resolutionHours) {
        SlaRuleForm form = new SlaRuleForm();
        form.setId(dataCritical.getId());
        form.setCategory(ComplaintCategory.DATA);
        form.setPriority(Priority.CRITICAL);
        form.setResolutionHours(resolutionHours);
        slaRuleService.save(form);
    }
}
