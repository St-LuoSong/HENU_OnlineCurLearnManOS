package com.onlinecurlearnmanos.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_resource_attachment")
public class CloudResourceAttachment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long classResourceId;

    private Long fileId;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
