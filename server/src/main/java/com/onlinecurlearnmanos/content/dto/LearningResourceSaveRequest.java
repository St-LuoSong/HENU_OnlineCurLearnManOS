package com.onlinecurlearnmanos.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LearningResourceSaveRequest(
        @NotNull(message = "学科不能为空")
        Long subjectId,

        @NotBlank(message = "资源标题不能为空")
        @Size(max = 255, message = "资源标题不能超过 255 个字符")
        String title,

        @Size(max = 1000, message = "资源描述不能超过 1000 个字符")
        String description,

        @NotBlank(message = "资源类型不能为空")
        @Size(max = 32, message = "资源类型不能超过 32 个字符")
        String resourceType,

        String content,

        @Size(max = 512, message = "外部地址不能超过 512 个字符")
        String externalUrl,

        List<Long> knowledgePointIds
) {
}
