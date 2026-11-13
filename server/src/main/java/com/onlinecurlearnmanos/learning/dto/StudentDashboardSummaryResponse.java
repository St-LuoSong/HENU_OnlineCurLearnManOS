package com.onlinecurlearnmanos.learning.dto;

import java.util.List;

public record StudentDashboardSummaryResponse(
        DashboardSummaryResponse summary,
        List<String> teacherNames,
        List<String> classNames,
        InteractionOverviewResponse interaction,
        List<RecentScoreResponse> recentScores,
        List<StudentCourseCardResponse> myCourses
) {
}
