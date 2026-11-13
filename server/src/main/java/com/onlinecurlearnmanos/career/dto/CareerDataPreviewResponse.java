package com.onlinecurlearnmanos.career.dto;

import java.util.List;

public record CareerDataPreviewResponse(
        boolean consentGranted,
        String consentVersion,
        String privacyNotice,
        boolean includeLearningRecords,
        boolean includeGoals,
        boolean includeGrades,
        int learningRecordCount,
        int learningMinutes,
        int masteredPointCount,
        int goalCount,
        int taskCount,
        int completedTaskCount,
        int gradedSubmissionCount,
        Double averageScore,
        List<String> subjects,
        List<String> goalTitles,
        List<String> sources
) {
}
