package com.onlinecurlearnmanos.content.mapper;

import com.onlinecurlearnmanos.content.entity.ResourceViewLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

public interface ResourceViewLogMapper extends BaseMapper<ResourceViewLog> {

    @Insert("""
            INSERT INTO resource_view_log (user_id, resource_id, view_date, viewed_at)
            VALUES (#{userId}, #{resourceId}, #{viewDate}, NOW(3))
            ON DUPLICATE KEY UPDATE viewed_at = NOW(3)
            """)
    int recordView(
            @Param("userId") Long userId,
            @Param("resourceId") Long resourceId,
            @Param("viewDate") LocalDate viewDate
    );
}
