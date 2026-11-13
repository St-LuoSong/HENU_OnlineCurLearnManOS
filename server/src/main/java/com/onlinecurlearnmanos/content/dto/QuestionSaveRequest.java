package com.onlinecurlearnmanos.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuestionSaveRequest(
        @NotNull(message = "学科不能为空")
        Long subjectId,

        @NotNull(message = "知识点不能为空")
        Long knowledgePointId,

        @NotBlank(message = "题目类型不能为空")
        String questionType,

        @NotNull(message = "难度不能为空")
        @Min(value = 1, message = "难度不能小于 1")
        @Max(value = 5, message = "难度不能大于 5")
        Integer difficulty,

        @NotBlank(message = "题目内容不能为空")
        String content,

        @NotBlank(message = "正确答案不能为空")
        String correctAnswer,

        String explanation,

        Boolean transfer,

        List<@Valid QuestionOptionRequest> options
) {
}
