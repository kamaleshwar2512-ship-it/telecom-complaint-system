package com.kamaleshwar.telecom_complaint_system.controller;

import com.kamaleshwar.telecom_complaint_system.dto.SlaRuleForm;
import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.SlaRule;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.exception.InvalidAssignmentException;
import com.kamaleshwar.telecom_complaint_system.service.ComplaintService;
import com.kamaleshwar.telecom_complaint_system.service.ComplaintUpdateService;
import com.kamaleshwar.telecom_complaint_system.service.DashboardService;
import com.kamaleshwar.telecom_complaint_system.service.SlaRuleService;
import com.kamaleshwar.telecom_complaint_system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Everything a logged-in ADMIN can do: dashboard, manage SLA rules, assign/reassign complaints, breach reports. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ComplaintService complaintService;
    private final ComplaintUpdateService complaintUpdateService;
    private final SlaRuleService slaRuleService;
    private final DashboardService dashboardService;
    private final UserService userService;

    public AdminController(ComplaintService complaintService, ComplaintUpdateService complaintUpdateService,
                            SlaRuleService slaRuleService, DashboardService dashboardService, UserService userService) {
        this.complaintService = complaintService;
        this.complaintUpdateService = complaintUpdateService;
        this.slaRuleService = slaRuleService;
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.getStats());
        return "admin/dashboard";
    }

    @GetMapping("/complaints")
    public String listComplaints(@RequestParam(defaultValue = "all") String filter, Model model) {
        List<Complaint> complaints = switch (filter) {
            case "open" -> complaintService.findAll().stream()
                    .filter(c -> c.getStatus() == ComplaintStatus.OPEN || c.getStatus() == ComplaintStatus.IN_PROGRESS)
                    .toList();
            case "resolved" -> complaintService.findAll().stream()
                    .filter(c -> c.getStatus() == ComplaintStatus.RESOLVED || c.getStatus() == ComplaintStatus.CLOSED)
                    .toList();
            case "escalated" -> complaintService.findAll().stream()
                    .filter(c -> c.getStatus() == ComplaintStatus.ESCALATED)
                    .toList();
            case "breached" -> complaintService.findBreached();
            default -> complaintService.findAll();
        };
        model.addAttribute("complaints", complaints);
        model.addAttribute("filter", filter);
        return "admin/complaints";
    }

    @GetMapping("/complaints/{id}")
    public String complaintDetail(@PathVariable Long id, Model model) {
        Complaint complaint = complaintService.getByIdOrThrow(id);
        model.addAttribute("complaint", complaint);
        model.addAttribute("timeline", complaintUpdateService.timelineFor(complaint));
        model.addAttribute("agents", userService.findAgents());
        return "admin/complaint-detail";
    }

    @PostMapping("/complaints/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam Long agentId, RedirectAttributes redirectAttributes) {
        try {
            complaintService.assign(id, agentId);
            redirectAttributes.addFlashAttribute("successMessage", "Complaint assigned.");
        } catch (InvalidAssignmentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/complaints/" + id;
    }

    @PostMapping("/complaints/{id}/close")
    public String close(@PathVariable Long id, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            complaintService.close(id, userService.getByEmail(authentication.getName()));
            redirectAttributes.addFlashAttribute("successMessage", "Complaint closed.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/complaints/" + id;
    }

    @GetMapping("/sla-rules")
    public String listSlaRules(Model model) {
        model.addAttribute("rules", slaRuleService.findAll());
        return "admin/sla-rules";
    }

    @GetMapping("/sla-rules/new")
    public String newSlaRuleForm(Model model) {
        model.addAttribute("slaRuleForm", new SlaRuleForm());
        return "admin/sla-rule-form";
    }

    @GetMapping("/sla-rules/{id}/edit")
    public String editSlaRuleForm(@PathVariable Long id, Model model) {
        SlaRule rule = slaRuleService.findAll().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("SLA rule not found: " + id));
        SlaRuleForm form = new SlaRuleForm();
        form.setId(rule.getId());
        form.setCategory(rule.getCategory());
        form.setPriority(rule.getPriority());
        form.setResolutionHours(rule.getResolutionHours());
        model.addAttribute("slaRuleForm", form);
        return "admin/sla-rule-form";
    }

    @PostMapping("/sla-rules")
    public String saveSlaRule(@Valid @ModelAttribute("slaRuleForm") SlaRuleForm form, BindingResult bindingResult,
                               Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "admin/sla-rule-form";
        }
        try {
            slaRuleService.save(form);
            redirectAttributes.addFlashAttribute("successMessage", "SLA rule saved.");
            return "redirect:/admin/sla-rules";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/sla-rule-form";
        }
    }
}
