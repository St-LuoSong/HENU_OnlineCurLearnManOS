package com.onlinecurlearnmanos.cloudclass.dto;

public record CloudClassStatisticsResponse(
        long memberCount,
        long taskCount,
        long submissionCount,
        long gradedCount,
        double averageScore
) {
}
