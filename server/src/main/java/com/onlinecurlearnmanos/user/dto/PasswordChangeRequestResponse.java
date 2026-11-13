package com.onlinecurlearnmanos.user.dto;

import java.time.LocalDateTime;

public record PasswordChangeRequestResponse(
        Long id,
        Long userId,
        String loginId,
        String displayName,
        String role,
        String status,
        LocalDateTime requestedAt,
        Long reviewerId,
        String reviewerName,
        LocalDateTime reviewedAt,
        String reviewNote
) {
}
