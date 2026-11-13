package com.onlinecurlearnmanos.practice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GeneratedPracticeAnswer(
        @JsonProperty("is_correct")
        @JsonAlias("isCorrect")
        Boolean correct,

        String grade,

        String feedback,

        @JsonProperty("error_type")
        @JsonAlias("errorType")
        String errorType,

        @JsonProperty("error_description")
        @JsonAlias("errorDescription")
        String errorDescription,

        @JsonProperty("improvement_suggestion")
        @JsonAlias("improvementSuggestion")
        String improvementSuggestion
) {
}
