package com.onlinecurlearnmanos.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("forum_report")
public class ForumReport {

    /** 举报状态：待处理。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 举报状态：已处理（内容被编辑 / 隐藏 / 删除）。 */
    public static final String STATUS_RESOLVED = "RESOLVED";
    /** 举报状态：已驳回（内容无需处置）。 */
    public static final String STATUS_DISMISSED = "DISMISSED";

    /** 目标类型：帖子。 */
    public static final String TARGET_POST = "POST";
    /** 目标类型：回复。 */
    public static final String TARGET_REPLY = "REPLY";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String targetType;

    private Long targetId;

    private Long reporterId;

    private String reason;

    private String detail;

    private String status;

    private Long handlerId;

    private String handleAction;

    private String handleNote;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
