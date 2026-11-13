package com.onlinecurlearnmanos.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_audit_log")
public class AdminAuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String action;

    private String targetType;

    private String targetId;

    private String detail;

    private LocalDateTime createdAt;
}
