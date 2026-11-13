package com.onlinecurlearnmanos.forum.dto;

import java.time.LocalDateTime;

/**
 * 论坛消息统一视图。回复消息可携带 postId / replyId 用于定位，公示消息只携带 sourceId。
 */
public record ForumMessageResponse(
        String type,
        Long sourceId,
        Long postId,
        Long replyId,
        String title,
        String content,
        Long authorId,
        String authorName,
        LocalDateTime createdAt,
        boolean acknowledged
) {
}
