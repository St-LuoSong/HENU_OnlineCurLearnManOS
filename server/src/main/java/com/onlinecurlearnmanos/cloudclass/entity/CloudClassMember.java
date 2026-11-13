package com.onlinecurlearnmanos.cloudclass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_class_member")
public class CloudClassMember {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long classId;

    private Long studentId;

    private String status;

    private LocalDateTime joinedAt;
}
