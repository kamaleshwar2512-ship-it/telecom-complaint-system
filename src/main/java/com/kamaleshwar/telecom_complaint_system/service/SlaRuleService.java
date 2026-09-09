package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.SlaRuleForm;
import com.kamaleshwar.telecom_complaint_system.entity.SlaRule;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import com.kamaleshwar.telecom_complaint_system.repository.SlaRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** CRUD for the database-driven SLA rules that {@link ComplaintService} looks up when a complaint is created. */
@Service
public class SlaRuleService {

    private final SlaRuleRepository slaRuleRepository;

    public SlaRuleService(SlaRuleRepository slaRuleRepository) {
        this.slaRuleRepository = slaRuleRepository;
    }

    public List<SlaRule> findAll() {
        return slaRuleRepository.findAllByOrderByCategoryAscPriorityAsc();
    }

    public Optional<SlaRule> findByCategoryAndPriority(ComplaintCategory category, Priority priority) {
        return slaRuleRepository.findByCategoryAndPriority(category, priority);
    }

    @Transactional
    public SlaRule save(SlaRuleForm form) {
        SlaRule rule = form.getId() != null
                ? slaRuleRepository.findById(form.getId())
                        .orElseThrow(() -> new IllegalArgumentException("SLA rule not found: " + form.getId()))
                : new SlaRule();

        // Enforce one rule per category/priority combination, whether creating or editing.
        Optional<SlaRule> existing = slaRuleRepository.findByCategoryAndPriority(form.getCategory(), form.getPriority());
        if (existing.isPresent() && !existing.get().getId().equals(form.getId())) {
            throw new IllegalArgumentException(
                    "A rule for " + form.getCategory() + " / " + form.getPriority() + " already exists");
        }

        rule.setCategory(form.getCategory());
        rule.setPriority(form.getPriority());
        rule.setResolutionHours(form.getResolutionHours());
        return slaRuleRepository.save(rule);
    }
}
