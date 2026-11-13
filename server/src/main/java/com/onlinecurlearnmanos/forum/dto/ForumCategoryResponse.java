package com.onlinecurlearnmanos.forum.dto;

public record ForumCategoryResponse(
        Long id,
        String code,
        String name,
        String description
) {
}
