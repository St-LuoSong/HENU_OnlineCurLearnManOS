package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDateTime;

/** 首页最近成绩条目，来源包括练习套题、学习记录和云班级任务。 */
public record RecentScoreResponse(
        String sourceType,
        Long sourceId,
        String title,
        String subjectName,
        Double score,
        Double maxScore,
        LocalDateTime recordedAt
) {
}
