package com.onlinecurlearnmanos.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgePointSaveRequest(
        @NotNull(message = "学科不能为空")
        Long subjectId,

        @NotBlank(message = "知识点编码不能为空")
        @Size(max = 64, message = "知识点编码不能超过 64 个字符")
        String code,

        @NotBlank(message = "知识点名称不能为空")
        @Size(max = 128, message = "知识点名称不能超过 128 个字符")
        String name,

        @NotBlank(message = "知识点标题不能为空")
        @Size(max = 255, message = "知识点标题不能超过 255 个字符")
        String title,

        @NotBlank(message = "知识点内容不能为空")
        String content,

        @Size(max = 16, message = "来源类型不能超过 16 个字符")
        String sourceType,

        @Size(max = 512, message = "来源地址不能超过 512 个字符")
        String sourceUrl,

        List<@Size(max = 1000, message = "要点不能超过 1000 个字符") String> keyPoints,

        List<@Size(max = 1000, message = "常见误区不能超过 1000 个字符") String> commonMistakes,

        List<@Size(max = 1000, message = "提示不能超过 1000 个字符") String> intuitionPumps
) {
}
