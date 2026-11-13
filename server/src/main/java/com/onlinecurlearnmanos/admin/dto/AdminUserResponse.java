package com.onlinecurlearnmanos.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserResponse(
        Long id,
        String loginId,
        String displayName,
        String email,
        boolean enabled,
        Integer status,
        List<String> roles,
        LocalDateTime createdAt
) {
}
