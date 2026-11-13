package com.onlinecurlearnmanos.admin.dto;

public record AdminDashboardSummaryResponse(
        long totalUsers,
        long studentUsers,
        long teacherUsers,
        long pendingTeachers,
        long activeCloudClasses,
        long llmCallsToday,
        long llmFailuresToday,
        long reservedPointTransactions,
        long configuredProviders,
        long enabledModels
) {
}
