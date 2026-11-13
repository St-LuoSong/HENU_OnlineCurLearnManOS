package com.onlinecurlearnmanos.learning.dto;

public record DashboardOverviewResponse(
        String role,
        StudentDashboardSummaryResponse student,
        TeacherDashboardSummaryResponse teacher
) {
}
