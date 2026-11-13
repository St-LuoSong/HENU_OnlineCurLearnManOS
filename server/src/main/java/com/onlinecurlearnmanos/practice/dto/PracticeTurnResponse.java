package com.onlinecurlearnmanos.practice.dto;

import java.util.List;

public record PracticeTurnResponse(
        PracticeSessionResponse session,
        List<PracticeMessageResponse> messages,
        PracticeAnswerResponse answer
) {
}
