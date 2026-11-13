package com.onlinecurlearnmanos.content.dto;

public record ResourceAttachmentSummary(
        Long fileId,
        String originalName,
        String extension,
        String mimeType,
        long sizeBytes
) {
}
