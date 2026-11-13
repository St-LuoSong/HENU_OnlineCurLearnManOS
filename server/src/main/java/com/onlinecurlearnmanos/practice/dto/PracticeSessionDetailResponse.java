package com.onlinecurlearnmanos.practice.dto;

import java.util.List;

public record PracticeSessionDetailResponse(
        PracticeSessionResponse session,
        List<PracticeMessageResponse> messages
) {
}
