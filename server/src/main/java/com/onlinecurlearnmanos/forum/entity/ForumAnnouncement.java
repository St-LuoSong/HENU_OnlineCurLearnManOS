package com.onlinecurlearnmanos.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师重要公示。
 *
 * <p>与普通帖子分开建模：教师只能对自己负责的云班级范围发布，管理员可以发布全局公示，
 * 学生只读。{@code classId} 为空表示全局公示（仅管理员可创建）。</p>
 */
@Data
@TableName("forum_announcement")
public class ForumAnnouncement {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_HIDDEN = "HIDDEN";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long authorId;

    private Long classId;

    private Long subjectId;

    private String title;

    private String content;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}

