package com.onlinecurlearnmanos.learning.dto;

public record TeacherClassSummaryResponse(
        Long classId,
        String className,
        String subjectName,
        long memberCount,
        long taskCount,
        long pendingSubmissionCount,
        long gradedSubmissionCount,
        double averageScore
) {
}
