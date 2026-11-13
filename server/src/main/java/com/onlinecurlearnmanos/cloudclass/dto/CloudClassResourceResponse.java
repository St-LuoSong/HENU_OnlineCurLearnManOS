package com.onlinecurlearnmanos.cloudclass.dto;

import java.time.LocalDateTime;

public record CloudClassResourceResponse(
        Long id,
        String title,
        String resourceType,
        String content,
        String externalUrl,
        String publisherName,
        LocalDateTime createdAt
) {
}
