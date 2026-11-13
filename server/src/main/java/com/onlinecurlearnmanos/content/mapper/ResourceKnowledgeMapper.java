package com.onlinecurlearnmanos.content.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ResourceKnowledgeMapper {

    @Select("""
            SELECT knowledge_point_id
            FROM resource_knowledge
            WHERE resource_id = #{resourceId}
            ORDER BY knowledge_point_id
            """)
    List<Long> selectKnowledgePointIds(@Param("resourceId") Long resourceId);

    @Insert("""
            INSERT INTO resource_knowledge (resource_id, knowledge_point_id)
            VALUES (#{resourceId}, #{knowledgePointId})
            """)
    int insert(
            @Param("resourceId") Long resourceId,
            @Param("knowledgePointId") Long knowledgePointId
    );

    @Delete("""
            DELETE FROM resource_knowledge
            WHERE resource_id = #{resourceId}
            """)
    int deleteByResourceId(@Param("resourceId") Long resourceId);

    @Select("""
            SELECT COUNT(*)
            FROM resource_knowledge
            WHERE knowledge_point_id = #{knowledgePointId}
            """)
    Long countByKnowledgePointId(@Param("knowledgePointId") Long knowledgePointId);
}
