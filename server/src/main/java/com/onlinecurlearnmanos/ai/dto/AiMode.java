package com.onlinecurlearnmanos.ai.dto;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;

public enum AiMode {
    STANDARD,
    EXPERT;

    public static AiMode parse(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        try {
            return AiMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 模式不合法");
        }
    }
}
