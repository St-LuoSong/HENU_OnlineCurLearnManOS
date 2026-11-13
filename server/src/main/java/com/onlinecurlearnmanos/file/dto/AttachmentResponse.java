package com.onlinecurlearnmanos.file.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        Long fileId,
        String originalName,
        String extension,
        String mimeType,
        Long sizeBytes,
        Integer sortOrder,
        Integer versionNo,
        Long uploadedBy,
        String uploadedByName,
        LocalDateTime createdAt,
        boolean canDelete
) {
}
