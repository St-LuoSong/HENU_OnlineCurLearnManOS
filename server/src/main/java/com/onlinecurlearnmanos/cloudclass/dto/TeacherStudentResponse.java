package com.onlinecurlearnmanos.cloudclass.dto;

public record TeacherStudentResponse(
        Long classId,
        String className,
        Long studentId,
        String loginId,
        String displayName,
        String status,
        long totalTaskCount,
        long submittedTaskCount,
        long pendingTaskCount,
        long overdueTaskCount,
        long gradedCount,
        double averageScore
) {
}
