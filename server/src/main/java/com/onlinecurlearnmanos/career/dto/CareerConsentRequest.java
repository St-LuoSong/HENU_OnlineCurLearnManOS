package com.onlinecurlearnmanos.career.dto;

public record CareerConsentRequest(
        boolean granted,
        boolean includeLearningRecords,
        boolean includeGoals,
        boolean includeGrades
) {
}
