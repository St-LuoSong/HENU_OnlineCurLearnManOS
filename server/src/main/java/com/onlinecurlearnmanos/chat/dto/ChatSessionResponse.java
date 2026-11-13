package com.onlinecurlearnmanos.chat.dto;

import java.time.LocalDateTime;

public record ChatSessionResponse(
        Long id,
        Long subjectId,
        String subjectName,
        Long knowledgePointId,
        String knowledgePointName,
        String title,
        String topic,
        String status,
        long messageCount,
        String lastMessage,
        LocalDateTime updatedAt
) {
}
