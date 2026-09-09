package com.kamaleshwar.telecom_complaint_system.repository;

import com.kamaleshwar.telecom_complaint_system.entity.SlaRule;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaRuleRepository extends JpaRepository<SlaRule, Long> {

    Optional<SlaRule> findByCategoryAndPriority(ComplaintCategory category, Priority priority);

    List<SlaRule> findAllByOrderByCategoryAscPriorityAsc();

    boolean existsByCategoryAndPriority(ComplaintCategory category, Priority priority);
}
