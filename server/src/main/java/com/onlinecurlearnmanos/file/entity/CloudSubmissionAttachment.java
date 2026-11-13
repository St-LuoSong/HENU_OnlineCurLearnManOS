package com.onlinecurlearnmanos.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_submission_attachment")
public class CloudSubmissionAttachment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long submissionId;

    private Long fileId;

    private Integer versionNo;

    private LocalDateTime createdAt;
}
