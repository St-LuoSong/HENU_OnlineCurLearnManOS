package com.onlinecurlearnmanos.learning.dto;

public record DashboardSummaryResponse(
        long activeGoals,
        long completedGoals,
        long pendingTasks,
        long dueThisWeek,
        long masteredPoints
) {
}
