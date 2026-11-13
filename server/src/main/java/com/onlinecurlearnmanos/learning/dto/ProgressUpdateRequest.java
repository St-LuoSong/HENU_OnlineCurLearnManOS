package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProgressUpdateRequest(
        @NotNull(message = "学科不能为空")
        Long subjectId,

        @NotNull(message = "知识点不能为空")
        Long knowledgePointId,

        @NotBlank(message = "理解等级不能为空")
        String currentGrade,

        @Min(value = 0, message = "尝试次数不能小于 0")
        Integer attemptCount,

        @Min(value = 0, message = "正确次数不能小于 0")
        Integer correctCount,

        Boolean mastered,

        Boolean transferPassed
) {
}
