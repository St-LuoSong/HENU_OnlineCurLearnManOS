package com.onlinecurlearnmanos.career.dto;

import jakarta.validation.constraints.Size;

public record CareerAnalysisGenerateRequest(
        @Size(max = 2000, message = "补充关注点不能超过 2000 个字符")
        String focus
) {
}
