package com.onlinecurlearnmanos.practice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PracticeSetAnswersRequest(
        @NotEmpty(message = "答案不能为空")
        List<@Valid PracticeAnswerItemRequest> answers
) {
}
