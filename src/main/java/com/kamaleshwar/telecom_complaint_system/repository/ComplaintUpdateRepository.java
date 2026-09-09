package com.kamaleshwar.telecom_complaint_system.repository;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.ComplaintUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintUpdateRepository extends JpaRepository<ComplaintUpdate, Long> {

    List<ComplaintUpdate> findByComplaintOrderByTimestampAsc(Complaint complaint);
}
