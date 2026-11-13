package com.onlinecurlearnmanos.growth.dto;

import jakarta.validation.constraints.Size;

public record GrowthReviewSaveRequest(
        @Size(max = 2000, message = "复盘备注不能超过 2000 个字符")
        String note
) {
}
