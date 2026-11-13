package com.onlinecurlearnmanos.growth.dto;

import com.onlinecurlearnmanos.career.dto.CareerAnalysisResponse;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;

import java.util.List;

public record GrowthWorkspaceResponse(
        GrowthSummaryResponse summary,
        GrowthCycleResponse cycle,
        List<GrowthCycleResponse> cycles,
        GrowthDirectionResponse direction,
        String currentStage,
        LearningPlanResponse currentPlan,
        GrowthReviewResponse currentReview,
        List<GrowthReviewResponse> reviews,
        List<GoalResponse> goals,
        List<LearningPlanResponse> plans,
        List<TaskResponse> todayTasks,
        List<TaskResponse> upcomingTasks,
        List<GrowthCalendarEventResponse> calendarEvents,
        List<CareerAnalysisResponse> recentAnalyses,
        List<GrowthAdviceResponse> advice
) {
}
