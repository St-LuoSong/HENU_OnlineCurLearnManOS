package com.onlinecurlearnmanos.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String avatarUrl;

    private String department;

    private String major;

    private String gradeLevel;

    private String phone;

    private String bio;

    private String teacherTitle;

    private String researchArea;

    private Integer notifyEmail;

    private Integer notifyForumReply;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
