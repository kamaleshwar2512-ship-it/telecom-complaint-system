package com.kamaleshwar.telecom_complaint_system.controller;

import com.kamaleshwar.telecom_complaint_system.dto.ComplaintForm;
import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.exception.InvalidStatusTransitionException;
import com.kamaleshwar.telecom_complaint_system.exception.SlaRuleNotFoundException;
import com.kamaleshwar.telecom_complaint_system.service.ComplaintService;
import com.kamaleshwar.telecom_complaint_system.service.ComplaintUpdateService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Everything a logged-in CUSTOMER can do: raise complaints, track them, view history, reopen. */
@Controller
@RequestMapping("/customer")
public class CustomerController {

    private final ComplaintService complaintService;
    private final ComplaintUpdateService complaintUpdateService;
    private final UserService userService;

    public CustomerController(ComplaintService complaintService, ComplaintUpdateService complaintUpdateService,
                               UserService userService) {
        this.complaintService = complaintService;
        this.complaintUpdateService = complaintUpdateService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User customer = currentUser(authentication);
        List<Complaint> complaints = complaintService.findForCustomer(customer);

        model.addAttribute("totalComplaints", complaints.size());
        model.addAttribute("openCount", complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.OPEN || c.getStatus() == ComplaintStatus.IN_PROGRESS).count());
        model.addAttribute("resolvedCount", complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED || c.getStatus() == ComplaintStatus.CLOSED).count());
        model.addAttribute("escalatedCount", complaints.stream().filter(c -> c.getStatus() == ComplaintStatus.ESCALATED).count());
        model.addAttribute("complaints", complaints.stream().limit(5).toList());
        return "customer/dashboard";
    }

    @GetMapping("/complaints")
    public String listComplaints(Authentication authentication, Model model) {
        model.addAttribute("complaints", complaintService.findForCustomer(currentUser(authentication)));
        return "customer/complaints";
    }

    @GetMapping("/complaints/new")
    public String newComplaintForm(Model model) {
        model.addAttribute("complaintForm", new ComplaintForm());
        return "customer/complaint-form";
    }

    @PostMapping("/complaints")
    public String createComplaint(@Valid @ModelAttribute("complaintForm") ComplaintForm form, BindingResult bindingResult,
                                   Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "customer/complaint-form";
        }
        try {
            Complaint complaint = complaintService.createComplaint(currentUser(authentication), form);
            redirectAttributes.addFlashAttribute("successMessage", "Complaint submitted successfully.");
            return "redirect:/customer/complaints/" + complaint.getId();
        } catch (SlaRuleNotFoundException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "customer/complaint-form";
        }
    }

    @GetMapping("/complaints/{id}")
    public String complaintDetail(@PathVariable Long id, Authentication authentication, Model model) {
        Complaint complaint = complaintService.getForCustomer(id, currentUser(authentication));
        model.addAttribute("complaint", complaint);
        model.addAttribute("timeline", complaintUpdateService.timelineFor(complaint));
        return "customer/complaint-detail";
    }

    @PostMapping("/complaints/{id}/reopen")
    public String reopen(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            complaintService.reopen(id, currentUser(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Complaint reopened.");
        } catch (InvalidStatusTransitionException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/complaints/" + id;
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }
}
