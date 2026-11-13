package com.onlinecurlearnmanos.forum.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ForumLikeMapper {

    @Select("""
            SELECT COUNT(*)
            FROM forum_like
            WHERE post_id = #{postId}
              AND user_id = #{userId}
            """)
    long count(@Param("postId") Long postId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO forum_like (post_id, user_id)
            VALUES (#{postId}, #{userId})
            """)
    int insert(@Param("postId") Long postId, @Param("userId") Long userId);

    @Delete("""
            DELETE FROM forum_like
            WHERE post_id = #{postId}
              AND user_id = #{userId}
            """)
    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 批量查询当前用户已点赞的帖子，用于列表页消除逐帖 N+1。
     */
    @Select({
            "<script>",
            "SELECT post_id FROM forum_like",
            "WHERE user_id = #{userId}",
            "AND post_id IN",
            "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<Long> selectLikedPostIds(
            @Param("userId") Long userId,
            @Param("postIds") Collection<Long> postIds
    );
}
