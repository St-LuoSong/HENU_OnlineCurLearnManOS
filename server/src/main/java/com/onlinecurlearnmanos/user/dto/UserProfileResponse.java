package com.onlinecurlearnmanos.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long userId,
        String loginId,
        String displayName,
        String email,
        String role,
        List<String> roles,
        String avatarUrl,
        String department,
        String major,
        String gradeLevel,
        String phone,
        String bio,
        String teacherTitle,
        String researchArea,
        boolean notifyEmail,
        boolean notifyForumReply,
        LocalDateTime updatedAt
) {
}
