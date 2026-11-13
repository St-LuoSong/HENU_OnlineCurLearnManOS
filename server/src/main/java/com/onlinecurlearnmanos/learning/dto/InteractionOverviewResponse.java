package com.onlinecurlearnmanos.learning.dto;

import java.util.List;

public record InteractionOverviewResponse(
        int totalThisWeek,
        int todayCount,
        int activeDaysThisWeek,
        int practiceAccuracy,
        InteractionBreakdownResponse breakdown,
        List<InteractionTrendPointResponse> trend
) {
}
