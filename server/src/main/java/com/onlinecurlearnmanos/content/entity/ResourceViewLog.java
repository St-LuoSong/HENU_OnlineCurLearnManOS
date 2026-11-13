package com.onlinecurlearnmanos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("resource_view_log")
public class ResourceViewLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    private LocalDate viewDate;

    private LocalDateTime viewedAt;
}
