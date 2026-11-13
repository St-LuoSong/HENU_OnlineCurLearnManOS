package com.onlinecurlearnmanos.auth.dto;

public record AuthResponse(
        String accessToken,
        UserResponse user,
        boolean pendingReview
) {
}
