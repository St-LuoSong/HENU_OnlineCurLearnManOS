package com.onlinecurlearnmanos.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码变更申请。用户提交后进入 PENDING，由管理员审批；只保存新密码的 BCrypt 哈希。
 */
@Data
@TableName("password_change_request")
public class PasswordChangeRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String newPasswordHash;

    private String status;

    private LocalDateTime requestedAt;

    private Long reviewerId;

    private LocalDateTime reviewedAt;

    private String reviewNote;
}
