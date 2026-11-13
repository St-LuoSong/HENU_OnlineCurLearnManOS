package com.onlinecurlearnmanos.cloudclass.dto;

import java.time.LocalDateTime;

public record CloudClassResponse(
        Long id,
        Long subjectId,
        String subjectName,
        String name,
        String description,
        Long teacherId,
        String teacherName,
        String classCode,
        String academicYear,
        String semester,
        Integer maxMembers,
        String status,
        boolean joinEnabled,
        long memberCount,
        long taskCount,
        long resourceCount,
        LocalDateTime createdAt,
        long myPendingTaskCount,
        long myOverdueTaskCount,
        long myGradedTaskCount,
        Double myAverageScore
) {
}
