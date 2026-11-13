package com.onlinecurlearnmanos.cloudclass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloud_class_submission_version")
public class CloudClassSubmissionVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long submissionId;

    private Long taskId;

    private Long studentId;

    private Integer versionNo;

    private String content;

    private Integer isLate;

    private BigDecimal score;

    private String feedback;

    private String status;

    private LocalDateTime submittedAt;

    private LocalDateTime gradedAt;
}
