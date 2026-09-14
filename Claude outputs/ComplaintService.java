package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.ComplaintForm;
import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.SlaRule;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Role;
import com.kamaleshwar.telecom_complaint_system.exception.ComplaintNotFoundException;
import com.kamaleshwar.telecom_complaint_system.exception.InvalidAssignmentException;
import com.kamaleshwar.telecom_complaint_system.exception.InvalidStatusTransitionException;
import com.kamaleshwar.telecom_complaint_system.exception.SlaRuleNotFoundException;
import com.kamaleshwar.telecom_complaint_system.exception.UnauthorizedComplaintAccessException;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintRepository;
import com.kamaleshwar.telecom_complaint_system.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Business rules for the complaint lifecycle: creation (with SLA deadline calculation),
 * assignment, status transitions, notes and resolution. Status-transition rules live here,
 * not in the controllers, so every entry point (customer, agent, admin, the SLA scheduler)
 * goes through the same checks.
 */
@Service
public class ComplaintService {

    /** Which statuses a complaint may move to from each current status. */
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ComplaintStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ComplaintStatus.OPEN, EnumSet.of(ComplaintStatus.IN_PROGRESS, ComplaintStatus.ESCALATED, ComplaintStatus.RESOLVED));
        ALLOWED_TRANSITIONS.put(ComplaintStatus.IN_PROGRESS, EnumSet.of(ComplaintStatus.ESCALATED, ComplaintStatus.RESOLVED));
        ALLOWED_TRANSITIONS.put(ComplaintStatus.ESCALATED, EnumSet.of(ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED));
        ALLOWED_TRANSITIONS.put(ComplaintStatus.RESOLVED, EnumSet.of(ComplaintStatus.CLOSED, ComplaintStatus.OPEN));
        ALLOWED_TRANSITIONS.put(ComplaintStatus.CLOSED, EnumSet.noneOf(ComplaintStatus.class));
    }

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final SlaRuleService slaRuleService;
    private final ComplaintUpdateService complaintUpdateService;
    private final SlaDisplayService slaDisplayService;

    public ComplaintService(ComplaintRepository complaintRepository, UserRepository userRepository,
                             SlaRuleService slaRuleService, ComplaintUpdateService complaintUpdateService,
                             SlaDisplayService slaDisplayService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.slaRuleService = slaRuleService;
        this.complaintUpdateService = complaintUpdateService;
        this.slaDisplayService = slaDisplayService;
    }

    /** Creates a complaint, looking up the matching SLA rule and calculating the deadline once, at creation time. */
    @Transactional
    public Complaint createComplaint(User customer, ComplaintForm form) {
        SlaRule rule = slaRuleService.findByCategoryAndPriority(form.getCategory(), form.getPriority())
                .orElseThrow(() -> new SlaRuleNotFoundException(form.getCategory(), form.getPriority()));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(rule.getResolutionHours());

        Complaint complaint = new Complaint(customer, form.getCategory(), form.getDescription(),
                form.getPriority(), now, deadline);
        complaint = complaintRepository.save(complaint);

        complaintUpdateService.addSystemEntry(complaint, "Complaint created. SLA deadline: "
                + rule.getResolutionHours() + " hours from now.");
        return complaint;
    }

    public Complaint getByIdOrThrow(Long id) {
        return complaintRepository.findById(id).orElseThrow(() -> new ComplaintNotFoundException(id));
    }

    /** Returns a complaint only if it belongs to this customer; never reveals whether it exists otherwise. */
    public Complaint getForCustomer(Long id, User customer) {
        return complaintRepository.findByIdAndCustomer(id, customer)
                .orElseThrow(() -> new ComplaintNotFoundException(id));
    }

    public List<Complaint> findForCustomer(User customer) {
        return complaintRepository.findByCustomerOrderByCreatedAtDesc(customer);
    }

    public List<Complaint> findAssignedTo(User agent) {
        return complaintRepository.findByAssignedAgentOrderBySlaDeadlineAsc(agent);
    }

    /**
     * The agent's queue, ordered so the complaints that most need attention surface first:
     * breached, then approaching their deadline, then by priority, then by soonest deadline.
     * A view-layer sort over the (small, per-agent) result of {@link #findAssignedTo(User)} -
     * no schema or query change needed.
     */
    public List<Complaint> findAssignedToSortedByUrgency(User agent) {
        Comparator<Complaint> byUrgency = Comparator
                .comparing((Complaint c) -> !slaDisplayService.isBreached(c) && !c.isBreached())
                .thenComparing(c -> !slaDisplayService.isApproaching(c) && !c.isApproachingNotified())
                .thenComparing(Complaint::getPriority, Comparator.reverseOrder())
                .thenComparing(Complaint::getSlaDeadline);
        return findAssignedTo(agent).stream().sorted(byUrgency).toList();
    }

    public List<Complaint> findAll() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Complaint> findBreached() {
        return complaintRepository.findByBreachedTrueOrderBySlaDeadlineAsc();
    }

    /** Assigns or reassigns a complaint to an agent. Admin-only action, enforced at the controller/security layer. */
    @Transactional
    public Complaint assign(Long complaintId, Long agentId) {
        Complaint complaint = getByIdOrThrow(complaintId);
        User agent = userRepository.findById(agentId)
                .filter(u -> u.getRole() == Role.AGENT)
                .orElseThrow(() -> new InvalidAssignmentException("Selected user is not a valid agent"));

        User previousAgent = complaint.getAssignedAgent();
        complaint.setAssignedAgent(agent);
        complaint = complaintRepository.save(complaint);

        String note = previousAgent == null
                ? "Assigned to agent " + agent.getName()
                : "Reassigned from " + previousAgent.getName() + " to " + agent.getName();
        complaintUpdateService.addSystemEntry(complaint, note);
        return complaint;
    }

    /** Agent marks a complaint as being actively worked on. */
    @Transactional
    public Complaint startProgress(Long complaintId, User agent) {
        Complaint complaint = getOwnedByAgent(complaintId, agent);
        changeStatus(complaint, ComplaintStatus.IN_PROGRESS);
        complaintUpdateService.addEntry(complaint, agent, "Started working on this complaint");
        return complaint;
    }

    /** Agent adds a note without changing status. */
    @Transactional
    public void addNote(Long complaintId, User agent, String note) {
        Complaint complaint = getOwnedByAgent(complaintId, agent);
        complaintUpdateService.addEntry(complaint, agent, note);
    }

    /** Agent resolves a complaint, recording the resolution note and timestamp. */
    @Transactional
    public Complaint resolve(Long complaintId, User agent, String resolutionNote) {
        Complaint complaint = getOwnedByAgent(complaintId, agent);
        changeStatus(complaint, ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(LocalDateTime.now());
        complaint = complaintRepository.save(complaint);
        complaintUpdateService.addEntry(complaint, agent, "Resolved: " + resolutionNote);
        return complaint;
    }

    /** Agent or admin closes a resolved complaint. */
    @Transactional
    public Complaint close(Long complaintId, User actor) {
        Complaint complaint = getByIdOrThrow(complaintId);
        changeStatus(complaint, ComplaintStatus.CLOSED);
        complaint = complaintRepository.save(complaint);
        complaintUpdateService.addEntry(complaint, actor, "Complaint closed");
        return complaint;
    }

    /** Customer reopens their own resolved complaint. The original SLA deadline is not recalculated. */
    @Transactional
    public Complaint reopen(Long complaintId, User customer) {
        Complaint complaint = getForCustomer(complaintId, customer);
        changeStatus(complaint, ComplaintStatus.OPEN);
        complaint.setResolvedAt(null);
        complaint = complaintRepository.save(complaint);
        complaintUpdateService.addEntry(complaint, null, "Reopened by customer");
        return complaint;
    }

    private void changeStatus(Complaint complaint, ComplaintStatus target) {
        Set<ComplaintStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(complaint.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStatusTransitionException(
                    "Cannot move complaint from " + complaint.getStatus() + " to " + target);
        }
        complaint.setStatus(target);
    }

    /** Loads a complaint and verifies it is assigned to this agent. */
    private Complaint getOwnedByAgent(Long complaintId, User agent) {
        Complaint complaint = getByIdOrThrow(complaintId);
        if (complaint.getAssignedAgent() == null || !complaint.getAssignedAgent().getId().equals(agent.getId())) {
            throw new UnauthorizedComplaintAccessException("This complaint is not assigned to you");
        }
        return complaint;
    }
}
