package com.onlinecurlearnmanos.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛消息已了解状态。消息内容本身由回复和教师公示派生，避免重复存储。
 */
@Data
@TableName("forum_message_ack")
public class ForumMessageAck {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String messageType;

    private Long sourceId;

    private LocalDateTime acknowledgedAt;
}
