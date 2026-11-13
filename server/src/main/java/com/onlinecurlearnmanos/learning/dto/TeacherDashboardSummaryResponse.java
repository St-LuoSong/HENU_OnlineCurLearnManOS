package com.onlinecurlearnmanos.learning.dto;

import java.util.List;

public record TeacherDashboardSummaryResponse(
        long classCount,
        long studentCount,
        long pendingSubmissionCount,
        long gradedSubmissionCount,
        double averageScore,
        List<TeacherClassSummaryResponse> classes,
        List<RiskStudentResponse> riskStudents
) {
}
