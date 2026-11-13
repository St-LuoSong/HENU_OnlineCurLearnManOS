package com.onlinecurlearnmanos.forum.mapper;

import com.onlinecurlearnmanos.forum.entity.ForumAnnouncement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 逻辑删除过滤会挡住普通更新，治理动作走原生 SQL 同时写 status 与 deleted。 */
@Mapper
public interface ForumAnnouncementMapper extends BaseMapper<ForumAnnouncement> {

    @Update("""
            UPDATE forum_announcement
            SET status = #{status}, deleted = #{deleted}
            WHERE id = #{id}
            """)
    int updateStatusAndDeleted(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("deleted") int deleted
    );
}

