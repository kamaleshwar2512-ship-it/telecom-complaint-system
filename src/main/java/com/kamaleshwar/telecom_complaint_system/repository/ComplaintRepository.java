package com.kamaleshwar.telecom_complaint_system.repository;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByCustomerOrderByCreatedAtDesc(User customer);

    Optional<Complaint> findByIdAndCustomer(Long id, User customer);

    List<Complaint> findByAssignedAgentOrderBySlaDeadlineAsc(User assignedAgent);

    List<Complaint> findByStatusNotInOrderBySlaDeadlineAsc(List<ComplaintStatus> excludedStatuses);

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    List<Complaint> findByCategoryOrderByCreatedAtDesc(ComplaintCategory category);

    List<Complaint> findByBreachedTrueOrderBySlaDeadlineAsc();

    List<Complaint> findAllByOrderByCreatedAtDesc();

    long countByStatus(ComplaintStatus status);

    long countByBreachedTrue();
}
