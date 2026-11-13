package com.onlinecurlearnmanos.point.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("point_transaction")
public class PointTransaction {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private Long userId;

    private String type;

    private Integer amount;

    private Integer balanceAfter;

    private String status;

    private String idempotencyKey;

    private String referenceType;

    private String referenceId;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
