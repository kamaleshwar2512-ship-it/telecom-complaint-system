package com.kamaleshwar.telecom_complaint_system.dto;

import java.util.List;

/**
 * View model for the admin dashboard: summary counters plus parallel label/value lists
 * ready to hand straight to Chart.js.
 */
public record DashboardStats(
        long totalComplaints,
        long openCount,
        long inProgressCount,
        long escalatedCount,
        long resolvedCount,
        long closedCount,
        long breachedCount,
        List<String> categoryLabels,
        List<Long> categoryCounts,
        List<String> statusLabels,
        List<Long> statusCounts,
        List<String> avgResolutionLabels,
        List<Double> avgResolutionHours
) {
}
