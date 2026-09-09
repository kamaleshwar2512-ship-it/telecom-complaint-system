package com.kamaleshwar.telecom_complaint_system.service;

import com.kamaleshwar.telecom_complaint_system.dto.DashboardStats;
import com.kamaleshwar.telecom_complaint_system.entity.Complaint;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintCategory;
import com.kamaleshwar.telecom_complaint_system.entity.enums.ComplaintStatus;
import com.kamaleshwar.telecom_complaint_system.repository.ComplaintRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates complaint data for the admin dashboard. All aggregation happens here, in Java,
 * on the server - the view and the browser only ever receive already-summarized numbers.
 */
@Service
public class DashboardService {

    private final ComplaintRepository complaintRepository;

    public DashboardService(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    public DashboardStats getStats() {
        List<Complaint> all = complaintRepository.findAllByOrderByCreatedAtDesc();

        Map<ComplaintStatus, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(Complaint::getStatus, Collectors.counting()));

        Map<ComplaintCategory, Long> byCategory = all.stream()
                .collect(Collectors.groupingBy(Complaint::getCategory, Collectors.counting()));

        Map<ComplaintCategory, Double> avgResolutionHoursByCategory = all.stream()
                .filter(c -> c.getResolvedAt() != null)
                .collect(Collectors.groupingBy(Complaint::getCategory,
                        Collectors.averagingDouble(this::resolutionHours)));

        long breachedCount = all.stream().filter(Complaint::isBreached).count();

        List<String> categoryLabels = List.of(ComplaintCategory.values()).stream().map(Enum::name).toList();
        List<Long> categoryCounts = categoryLabels.stream()
                .map(name -> categoryCount(byCategory, ComplaintCategory.valueOf(name)))
                .toList();

        List<String> statusLabels = List.of(ComplaintStatus.values()).stream().map(Enum::name).toList();
        List<Long> statusCounts = statusLabels.stream()
                .map(name -> byStatus.getOrDefault(ComplaintStatus.valueOf(name), 0L))
                .toList();

        List<Double> avgResolutionHours = categoryLabels.stream()
                .map(name -> avgResolutionHoursByCategory.getOrDefault(ComplaintCategory.valueOf(name), 0.0))
                .map(hours -> Math.round(hours * 10.0) / 10.0)
                .toList();

        return new DashboardStats(
                all.size(),
                byStatus.getOrDefault(ComplaintStatus.OPEN, 0L),
                byStatus.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(ComplaintStatus.ESCALATED, 0L),
                byStatus.getOrDefault(ComplaintStatus.RESOLVED, 0L),
                byStatus.getOrDefault(ComplaintStatus.CLOSED, 0L),
                breachedCount,
                categoryLabels,
                categoryCounts,
                statusLabels,
                statusCounts,
                categoryLabels,
                avgResolutionHours
        );
    }

    private long categoryCount(Map<ComplaintCategory, Long> byCategory, ComplaintCategory category) {
        return byCategory.getOrDefault(category, 0L);
    }

    private double resolutionHours(Complaint complaint) {
        return Duration.between(complaint.getCreatedAt(), complaint.getResolvedAt()).toMinutes() / 60.0;
    }
}
