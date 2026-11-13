package com.onlinecurlearnmanos.user.mapper;

import com.onlinecurlearnmanos.user.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Collection;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT id
            FROM sys_role
            WHERE code = #{code}
              AND deleted = 0
            LIMIT 1
            """)
    Long selectIdByCode(@Param("code") String code);

    @Select("""
            SELECT r.code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.deleted = 0
            ORDER BY r.id
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 批量查询一批用户中拥有指定角色的人，用于论坛作者角色与教师标记，
     * 避免逐条内容查询角色造成 N+1。
     */
    @Select({
            "<script>",
            "SELECT DISTINCT ur.user_id FROM sys_user_role ur",
            "INNER JOIN sys_role r ON r.id = ur.role_id",
            "WHERE r.deleted = 0 AND r.code = #{roleCode}",
            "AND ur.user_id IN",
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<Long> selectUserIdsByRoleCode(
            @Param("userIds") Collection<Long> userIds,
            @Param("roleCode") String roleCode
    );

    @Select("""
            SELECT COUNT(DISTINCT ur.user_id)
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            INNER JOIN sys_user u ON u.id = ur.user_id
            WHERE r.code = #{code}
              AND r.deleted = 0
              AND u.deleted = 0
            """)
    long countUsersByRoleCode(@Param("code") String code);

    @Select("""
            SELECT COUNT(DISTINCT ur.user_id)
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            INNER JOIN sys_user u ON u.id = ur.user_id
            WHERE r.code = 'TEACHER'
              AND r.deleted = 0
              AND u.deleted = 0
              AND u.status = 2
            """)
    long countPendingTeachers();

    @Select("""
            SELECT COUNT(DISTINCT ur.user_id)
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            INNER JOIN sys_user u ON u.id = ur.user_id
            WHERE r.code = 'ADMIN'
              AND r.deleted = 0
              AND u.deleted = 0
              AND u.status = 1
              AND u.id <> #{excludeUserId}
            """)
    long countOtherActiveAdmins(@Param("excludeUserId") Long excludeUserId);
}
