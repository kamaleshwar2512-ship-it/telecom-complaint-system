package com.kamaleshwar.telecom_complaint_system.controller;

import com.kamaleshwar.telecom_complaint_system.dto.ComplaintNoteForm;
import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.exception.InvalidStatusTransitionException;
import com.kamaleshwar.telecom_complaint_system.exception.UnauthorizedComplaintAccessException;
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

/** Everything a logged-in AGENT can do: work their assigned queue, update status, add notes, resolve. */
@Controller
@RequestMapping("/agent")
public class AgentController {

    private final ComplaintService complaintService;
    private final ComplaintUpdateService complaintUpdateService;
    private final UserService userService;

    public AgentController(ComplaintService complaintService, ComplaintUpdateService complaintUpdateService,
                            UserService userService) {
        this.complaintService = complaintService;
        this.complaintUpdateService = complaintUpdateService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User agent = currentUser(authentication);
        List<Complaint> assigned = complaintService.findAssignedTo(agent);
        model.addAttribute("complaints", assigned);
        model.addAttribute("escalatedCount", assigned.stream().filter(c -> c.getStatus() == ComplaintStatus.ESCALATED).count());
        model.addAttribute("openCount", assigned.stream().filter(c -> c.getStatus() == ComplaintStatus.OPEN || c.getStatus() == ComplaintStatus.IN_PROGRESS).count());
        return "agent/dashboard";
    }

    @GetMapping("/complaints/{id}")
    public String complaintDetail(@PathVariable Long id, Model model) {
        Complaint complaint = complaintService.getByIdOrThrow(id);
        model.addAttribute("complaint", complaint);
        model.addAttribute("timeline", complaintUpdateService.timelineFor(complaint));
        model.addAttribute("noteForm", new ComplaintNoteForm());
        return "agent/complaint-detail";
    }

    @PostMapping("/complaints/{id}/start")
    public String start(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        return withErrorHandling(id, redirectAttributes, () -> complaintService.startProgress(id, currentUser(authentication)));
    }

    @PostMapping("/complaints/{id}/note")
    public String addNote(@PathVariable Long id, @Valid @ModelAttribute("noteForm") ComplaintNoteForm form,
                           BindingResult bindingResult, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Complaint complaint = complaintService.getByIdOrThrow(id);
            model.addAttribute("complaint", complaint);
            model.addAttribute("timeline", complaintUpdateService.timelineFor(complaint));
            return "agent/complaint-detail";
        }
        try {
            complaintService.addNote(id, currentUser(authentication), form.getNote());
            redirectAttributes.addFlashAttribute("successMessage", "Note added.");
        } catch (UnauthorizedComplaintAccessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/agent/complaints/" + id;
    }

    @PostMapping("/complaints/{id}/resolve")
    public String resolve(@PathVariable Long id, @Valid @ModelAttribute("noteForm") ComplaintNoteForm form,
                           BindingResult bindingResult, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Complaint complaint = complaintService.getByIdOrThrow(id);
            model.addAttribute("complaint", complaint);
            model.addAttribute("timeline", complaintUpdateService.timelineFor(complaint));
            model.addAttribute("errorMessage", "Please enter a resolution note.");
            return "agent/complaint-detail";
        }
        return withErrorHandling(id, redirectAttributes, () -> complaintService.resolve(id, currentUser(authentication), form.getNote()));
    }

    private String withErrorHandling(Long id, RedirectAttributes redirectAttributes, Runnable action) {
        try {
            action.run();
        } catch (InvalidStatusTransitionException | UnauthorizedComplaintAccessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/agent/complaints/" + id;
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }
}
