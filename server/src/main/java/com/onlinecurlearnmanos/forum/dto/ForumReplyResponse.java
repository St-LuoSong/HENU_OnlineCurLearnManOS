package com.onlinecurlearnmanos.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ForumReplyResponse(
        Long id,
        Long postId,
        Long authorId,
        String authorName,
        String authorRole,
        List<String> authorBadges,
        String content,
        boolean canEdit,
        boolean canDelete,
        boolean canReport,
        LocalDateTime createdAt
) {
}
