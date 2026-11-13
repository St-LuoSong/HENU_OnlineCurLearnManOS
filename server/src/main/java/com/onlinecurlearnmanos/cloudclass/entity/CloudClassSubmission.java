package com.onlinecurlearnmanos.cloudclass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloud_class_submission")
public class CloudClassSubmission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long studentId;

    private Long learningRecordId;

    private String content;

    private BigDecimal score;

    private String feedback;

    private String status;

    private Integer currentVersion;

    private LocalDateTime submittedAt;

    private LocalDateTime gradedAt;

    private LocalDateTime updatedAt;
}
