package com.onlinecurlearnmanos.forum.mapper;

import com.onlinecurlearnmanos.forum.entity.ForumMessageAck;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ForumMessageAckMapper extends BaseMapper<ForumMessageAck> {

    /**
     * 同一用户对同一来源只保留一条已了解记录，重复点击不会产生重复行。
     */
    @Insert("""
            INSERT INTO forum_message_ack (user_id, message_type, source_id, acknowledged_at)
            VALUES (#{userId}, #{messageType}, #{sourceId}, CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE acknowledged_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(
            @Param("userId") Long userId,
            @Param("messageType") String messageType,
            @Param("sourceId") Long sourceId
    );
}
