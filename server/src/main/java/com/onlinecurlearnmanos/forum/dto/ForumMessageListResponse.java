package com.onlinecurlearnmanos.forum.dto;

import java.util.List;

public record ForumMessageListResponse(
        List<ForumMessageResponse> records,
        int unreadCount
) {
}
