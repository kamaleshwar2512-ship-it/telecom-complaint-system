package com.kamaleshwar.telecom_complaint_system.config;

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
import com.kamaleshwar.telecom_complaint_system.service.ComplaintUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Seeds a handful of demo users, all SLA rules, and a few sample complaints so the application
 * is demonstrable immediately after first start. Only runs once: if any user already exists
 * (e.g. on every restart after the first), it does nothing, so it never interferes with real usage.
 *
 * <p>Disabled in the test profile ({@code app.demo-data.enabled=false}, see
 * src/test/resources/application.properties) so tests start from a clean, predictable database.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    /** Demo-only password shared by every seeded account. Never used for real accounts - always hashed before storage. */
    private static final String DEMO_PASSWORD = "Password123";

    private final UserRepository userRepository;
    private final SlaRuleRepository slaRuleRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintUpdateService complaintUpdateService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataLoader(UserRepository userRepository, SlaRuleRepository slaRuleRepository,
                           ComplaintRepository complaintRepository, ComplaintUpdateService complaintUpdateService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.slaRuleRepository = slaRuleRepository;
        this.complaintRepository = complaintRepository;
        this.complaintUpdateService = complaintUpdateService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Demo data already present - skipping seed");
            return;
        }
        log.info("Seeding demo data (users, SLA rules, sample complaints)");

        User admin = save(new User("Alice Admin", "admin@telecom.com", DEMO_PASSWORD, Role.ADMIN, "9000000001"));
        User agent1 = save(new User("Raj Agent", "agent1@telecom.com", DEMO_PASSWORD, Role.AGENT, "9000000002"));
        User agent2 = save(new User("Priya Agent", "agent2@telecom.com", DEMO_PASSWORD, Role.AGENT, "9000000003"));
        User customer1 = save(new User("John Customer", "customer1@telecom.com", DEMO_PASSWORD, Role.CUSTOMER, "9000000004"));
        User customer2 = save(new User("Meena Customer", "customer2@telecom.com", DEMO_PASSWORD, Role.CUSTOMER, "9000000005"));

        seedSlaRules();
        seedSampleComplaints(customer1, customer2, agent1, agent2);

        log.info("Demo data seed complete");
    }

    private User save(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    private void seedSlaRules() {
        // Hours-to-resolve per category/priority. Deliberately short for LOW/MEDIUM Network & SIM
        // rules is avoided here - these are realistic-looking targets an operator might actually use.
        int[][] hours = {
                // LOW, MEDIUM, HIGH, CRITICAL
                {72, 48, 24, 4},   // NETWORK
                {120, 72, 48, 24}, // BILLING
                {48, 24, 12, 4},   // SIM
                {72, 48, 24, 8},   // DATA
                {120, 72, 48, 24}  // OTHER
        };
        ComplaintCategory[] categories = ComplaintCategory.values();
        Priority[] priorities = {Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.CRITICAL};

        for (int c = 0; c < categories.length; c++) {
            for (int p = 0; p < priorities.length; p++) {
                slaRuleRepository.save(new SlaRule(categories[c], priorities[p], hours[c][p]));
            }
        }
    }

    private void seedSampleComplaints(User customer1, User customer2, User agent1, User agent2) {
        LocalDateTime now = LocalDateTime.now();

        // 1) Freshly created, plenty of time left (on track / green).
        createDemoComplaint(customer1, ComplaintCategory.DATA, Priority.LOW,
                "My mobile data has been slower than usual for the past two days.",
                now.minusHours(1), now.plusHours(71), ComplaintStatus.OPEN, null, null, false, false);

        // 2) Assigned and in progress, deadline getting close (amber / at risk).
        Complaint atRisk = createDemoComplaint(customer1, ComplaintCategory.SIM, Priority.HIGH,
                "My SIM card stopped working after I traveled to another city.",
                now.minusHours(10), now.plusHours(2), ComplaintStatus.IN_PROGRESS, agent1, null, false, false);
        complaintUpdateService.addEntry(atRisk, agent1, "Investigating with the network team.");

        // 3) Already breached, left for the scheduler to pick up and escalate on its next run.
        createDemoComplaint(customer2, ComplaintCategory.NETWORK, Priority.CRITICAL,
                "Complete network outage in my area since last night, no calls or data at all.",
                now.minusHours(8), now.minusHours(4), ComplaintStatus.OPEN, null, null, false, false);

        // 4) Already escalated by the "scheduler" (seeded pre-flagged) so the queue shows an example immediately.
        Complaint escalated = createDemoComplaint(customer2, ComplaintCategory.BILLING, Priority.HIGH,
                "I was charged twice for last month's bill and need a refund.",
                now.minusHours(50), now.minusHours(2), ComplaintStatus.ESCALATED, agent2, null, false, true);
        complaintUpdateService.addSystemEntry(escalated,
                "SLA deadline breached. Complaint automatically escalated and priority raised to CRITICAL.");

        // 5) Resolved complaint, to populate average-resolution-time metrics.
        Complaint resolved = createDemoComplaint(customer1, ComplaintCategory.OTHER, Priority.MEDIUM,
                "General enquiry about switching to a family data plan.",
                now.minusHours(96), now.minusHours(24), ComplaintStatus.RESOLVED, agent1, now.minusHours(50), false, false);
        complaintUpdateService.addEntry(resolved, agent1, "Resolved: Explained the family plan options and applied the requested change.");
    }

    private Complaint createDemoComplaint(User customer, ComplaintCategory category, Priority priority, String description,
                                           LocalDateTime createdAt, LocalDateTime slaDeadline, ComplaintStatus status,
                                           User assignedAgent, LocalDateTime resolvedAt, boolean approachingNotified, boolean breached) {
        Complaint complaint = new Complaint(customer, category, description, priority, createdAt, slaDeadline);
        complaint.setStatus(status);
        complaint.setAssignedAgent(assignedAgent);
        complaint.setResolvedAt(resolvedAt);
        complaint.setApproachingNotified(approachingNotified);
        complaint.setBreached(breached);
        complaint = complaintRepository.save(complaint);
        complaintUpdateService.addSystemEntry(complaint, "Complaint created. SLA deadline set.");
        return complaint;
    }
}
