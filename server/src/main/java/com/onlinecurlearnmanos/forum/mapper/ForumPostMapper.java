package com.onlinecurlearnmanos.forum.mapper;

import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ForumPostMapper extends BaseMapper<ForumPost> {

    /**
     * 隐藏 / 恢复内容。逻辑删除过滤会挡住普通更新，因此这里用原生 SQL 同时写 status 与 deleted。
     */
    @Update("""
            UPDATE forum_post
            SET status = #{status}, deleted = #{deleted}
            WHERE id = #{id}
            """)
    int updateStatusAndDeleted(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("deleted") int deleted
    );
}
