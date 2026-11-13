package com.onlinecurlearnmanos.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String providerCode;

    private String model;

    private Integer totalTokens;

    private LocalDateTime createdAt;
}
