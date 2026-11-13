package com.onlinecurlearnmanos.assistant.dto;

import java.time.LocalDateTime;

/**
 * AI 学习助手统一会话条目。sourceType 用于区分底层专业引擎，sessionMode 用于前端筛选。
 */
public record AssistantSessionResponse(
        Long id,
        String sourceType,
        String sessionMode,
        Long subjectId,
        String subjectName,
        Long knowledgePointId,
        String knowledgePointName,
        String title,
        String state,
        String currentGrade,
        long messageCount,
        String preview,
        boolean transferQuestion,
        Long currentQuestionId,
        LocalDateTime updatedAt
) {
}
