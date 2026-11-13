package com.onlinecurlearnmanos.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ForumAnnouncementResponse(
        Long id,
        Long authorId,
        String authorName,
        List<String> authorBadges,
        Long classId,
        String className,
        Long subjectId,
        String subjectName,
        String title,
        String content,
        String status,
        boolean canEdit,
        boolean canDelete,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

