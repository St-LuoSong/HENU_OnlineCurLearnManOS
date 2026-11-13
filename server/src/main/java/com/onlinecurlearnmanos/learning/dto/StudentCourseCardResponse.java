package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDateTime;

public record StudentCourseCardResponse(
        Long subjectId,
        String subjectName,
        String className,
        String teacherName,
        int masteryPercent,
        int masteredPoints,
        int totalPoints,
        long pendingTaskCount,
        Double averageScore,
        LocalDateTime lastActivityAt,
        String nextAction
) {
}
