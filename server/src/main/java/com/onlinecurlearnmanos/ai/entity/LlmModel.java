package com.onlinecurlearnmanos.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_model")
public class LlmModel {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long providerId;

    private String modelCode;

    private String displayName;

    private String tier;

    private Integer pointCost;

    private Integer enabled;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
