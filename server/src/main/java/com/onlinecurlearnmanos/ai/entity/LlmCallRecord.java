package com.onlinecurlearnmanos.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_call_record")
public class LlmCallRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String providerCode;

    private String model;

    private String accessMode;

    private String requestId;

    private String idempotencyKey;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private String status;

    private String errorCode;

    private String errorMessage;

    private String responseContent;

    private Long durationMs;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
