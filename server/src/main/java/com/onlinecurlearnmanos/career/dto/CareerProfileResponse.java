package com.onlinecurlearnmanos.career.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CareerProfileResponse(
        Long id,
        boolean consentGranted,
        String consentVersion,
        LocalDateTime consentedAt,
        LocalDateTime revokedAt,
        boolean includeLearningRecords,
        boolean includeGoals,
        boolean includeGrades,
        String major,
        String gradeLevel,
        Integer weeklyHours,
        List<String> interestTags,
        String strengthNotes,
        String careerGoal,
        LocalDateTime updatedAt
) {
}
