package com.onlinecurlearnmanos.learning.dto;

public record InteractionBreakdownResponse(
        int aiQuestions,
        int practiceAnswers,
        int resourceViews,
        int forumActivities,
        int classSubmissions
) {

    public int total() {
        return aiQuestions + practiceAnswers + resourceViews + forumActivities + classSubmissions;
    }
}
