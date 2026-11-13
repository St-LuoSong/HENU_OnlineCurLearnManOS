package com.onlinecurlearnmanos.content.dto;

import java.util.List;
import java.time.LocalDateTime;

public record LearningResourceResponse(
        Long id,
        Long subjectId,
        String subjectName,
        String title,
        String description,
        String resourceType,
        String content,
        String externalUrl,
        Long publisherId,
        String status,
        List<Long> knowledgePointIds,
        List<String> knowledgePointNames,
        long attachmentCount,
        ResourceAttachmentSummary primaryAttachment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
