package com.onlinecurlearnmanos.chat.dto;

import java.time.LocalDateTime;

public record KnowledgeFavoriteResponse(
        Long id,
        Long messageId,
        Long sessionId,
        Long subjectId,
        String subjectName,
        Long knowledgePointId,
        String knowledgePointName,
        String question,
        String answer,
        String note,
        LocalDateTime createdAt
) {
}
