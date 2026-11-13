package com.onlinecurlearnmanos.forum.mapper;

import com.onlinecurlearnmanos.forum.entity.ForumReply;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ForumReplyMapper extends BaseMapper<ForumReply> {

    @Update("""
            UPDATE forum_reply
            SET status = #{status}, deleted = #{deleted}
            WHERE id = #{id}
            """)
    int updateStatusAndDeleted(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("deleted") int deleted
    );
}
