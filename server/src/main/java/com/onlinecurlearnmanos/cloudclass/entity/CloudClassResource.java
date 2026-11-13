package com.onlinecurlearnmanos.cloudclass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_class_resource")
public class CloudClassResource {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long classId;

    private Long publisherId;

    private String title;

    private String resourceType;

    private String content;

    private String externalUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
