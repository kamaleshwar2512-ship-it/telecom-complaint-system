package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.ComplaintUpdate;
import com.kamaleshwar.telecom_complaint_system.entity.User;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintUpdateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Reads and appends to a complaint's activity timeline. */
@Service
public class ComplaintUpdateService {

    private final ComplaintUpdateRepository complaintUpdateRepository;

    public ComplaintUpdateService(ComplaintUpdateRepository complaintUpdateRepository) {
        this.complaintUpdateRepository = complaintUpdateRepository;
    }

    @Transactional
    public ComplaintUpdate addEntry(Complaint complaint, User agent, String note) {
        ComplaintUpdate update = new ComplaintUpdate(complaint, agent, note, LocalDateTime.now());
        return complaintUpdateRepository.save(update);
    }

    /** Adds a system-generated entry (no agent), used by automatic SLA escalation. */
    @Transactional
    public ComplaintUpdate addSystemEntry(Complaint complaint, String note) {
        return addEntry(complaint, null, note);
    }

    public List<ComplaintUpdate> timelineFor(Complaint complaint) {
        return complaintUpdateRepository.findByComplaintOrderByTimestampAsc(complaint);
    }
}
