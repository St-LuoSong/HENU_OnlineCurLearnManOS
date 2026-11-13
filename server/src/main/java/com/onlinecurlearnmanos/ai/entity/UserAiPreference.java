package com.onlinecurlearnmanos.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_ai_preference")
public class UserAiPreference {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long providerId;

    private Long modelId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
