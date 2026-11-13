package com.onlinecurlearnmanos.learning.dto;

/** 教师概览的风险学生条目：逾期未交或平均分偏低。 */
public record RiskStudentResponse(
        Long classId,
        String className,
        Long studentId,
        String displayName,
        long overdueTaskCount,
        long pendingTaskCount,
        double averageScore
) {
}

