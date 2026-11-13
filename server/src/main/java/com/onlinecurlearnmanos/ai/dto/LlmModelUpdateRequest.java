package com.onlinecurlearnmanos.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LlmModelUpdateRequest(
        @NotBlank(message = "模型展示名称不能为空")
        @Size(max = 128, message = "模型展示名称不能超过 128 个字符")
        String displayName,

        @jakarta.validation.constraints.Pattern(
                regexp = "STANDARD|EXPERT",
                message = "模型分级只能是 STANDARD 或 EXPERT"
        )
        String tier,

        @NotNull(message = "积分价格不能为空")
        @Min(value = 1, message = "积分价格不能小于 1")
        @Max(value = 10000, message = "积分价格不能大于 10000")
        Integer pointCost,

        Boolean enabled
) {
}
