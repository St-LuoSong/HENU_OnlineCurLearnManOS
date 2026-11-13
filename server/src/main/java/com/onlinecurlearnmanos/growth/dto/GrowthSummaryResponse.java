package com.onlinecurlearnmanos.growth.dto;

public record GrowthSummaryResponse(
        long activeGoals,
        long completedGoals,
        long openTasks,
        long overdueTasks,
        long dueToday,
        long dueThisWeek,
        long activePlans,
        long totalPlanItems,
        long completedPlanItems,
        long careerAnalysisCount,
        boolean careerConsentGranted
) {
}
