package com.onlinecurlearnmanos.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ForumPostSummaryResponse(
        Long id,
        Long categoryId,
        String categoryName,
        Long authorId,
        String authorName,
        String authorRole,
        List<String> authorBadges,
        String title,
        String contentPreview,
        int viewCount,
        int likeCount,
        int replyCount,
        boolean likedByMe,
        boolean canEdit,
        boolean canDelete,
        boolean canReport,
        List<ForumMediaResponse> media,
        LocalDateTime createdAt
) {
}
