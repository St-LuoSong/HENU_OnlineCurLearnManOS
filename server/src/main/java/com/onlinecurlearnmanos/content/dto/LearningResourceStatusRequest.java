package com.onlinecurlearnmanos.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LearningResourceStatusRequest(
        @NotBlank(message = "资源状态不能为空")
        @Pattern(regexp = "DRAFT|PUBLISHED", message = "资源状态只能是 DRAFT 或 PUBLISHED")
        String status
) {
}
