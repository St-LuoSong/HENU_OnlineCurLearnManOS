package com.onlinecurlearnmanos.file.dto;

import java.time.LocalDateTime;

public record FileAssetResponse(
        Long id,
        String originalName,
        String extension,
        String mimeType,
        Long sizeBytes,
        String sha256,
        String purpose,
        String status,
        Long ownerId,
        String ownerName,
        Integer downloadCount,
        LocalDateTime lastDownloadedAt,
        LocalDateTime createdAt,
        boolean previewable,
        boolean canDelete
) {
}
