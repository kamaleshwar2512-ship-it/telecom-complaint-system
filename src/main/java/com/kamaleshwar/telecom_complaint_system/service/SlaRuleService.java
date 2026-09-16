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

/**
 * CRUD for the database-driven SLA rules that every complaint's SLA target is resolved from.
 *
 * <p>A rule is not a snapshot taken at complaint-creation time: {@link SlaEvaluationService} reads it
 * on every SLA status calculation, so saving a rule here changes the target of every complaint
 * matching its category/priority - the SLA badge and countdown on every screen follow immediately,
 * with nothing to migrate. {@link #save(SlaRuleForm)} additionally asks {@link SlaMonitoringService}
 * to re-evaluate the affected complaints straight away, so the stored breach markers behind the
 * breach report and the dashboard counters agree with what those screens already show.
 */
@Service
public class SlaRuleService {

    private final SlaRuleRepository slaRuleRepository;
    private final SlaMonitoringService slaMonitoringService;

    public SlaRuleService(SlaRuleRepository slaRuleRepository, SlaMonitoringService slaMonitoringService) {
        this.slaRuleRepository = slaRuleRepository;
        this.slaMonitoringService = slaMonitoringService;
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

        ComplaintCategory previousCategory = rule.getCategory();
        Priority previousPriority = rule.getPriority();

        rule.setCategory(form.getCategory());
        rule.setPriority(form.getPriority());
        rule.setResolutionHours(form.getResolutionHours());
        SlaRule saved = slaRuleRepository.save(rule);

        // The stored targets and breach markers of existing complaints are now out of date.
        slaMonitoringService.recalculateForRule(saved.getCategory(), saved.getPriority());
        if (previousCategory != null && previousPriority != null
                && (previousCategory != saved.getCategory() || previousPriority != saved.getPriority())) {
            // The rule was re-pointed at a different pair; complaints under the old pair lose it.
            slaMonitoringService.recalculateForRule(previousCategory, previousPriority);
        }
        return saved;
    }
}
