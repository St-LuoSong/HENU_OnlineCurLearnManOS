package com.onlinecurlearnmanos.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_message")
public class PracticeMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String role;

    private String messageType;

    private String content;

    private Long questionId;

    private String providerCode;

    private String model;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
