package com.onlinecurlearnmanos.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("forum_moderation_log")
public class ForumModerationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String targetType;

    private Long targetId;

    private Long reportId;

    private Long operatorId;

    private String action;

    private String reason;

    private String beforeSummary;

    private String afterSummary;

    private Integer patrol;

    private LocalDateTime createdAt;
}
