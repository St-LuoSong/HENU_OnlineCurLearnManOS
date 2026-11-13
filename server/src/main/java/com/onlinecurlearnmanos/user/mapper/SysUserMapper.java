package com.onlinecurlearnmanos.user.mapper;

import com.onlinecurlearnmanos.user.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Update("""
            UPDATE sys_user
            SET status = 0,
                deleted = 1,
                token_version = token_version + 1,
                account_deleted_at = #{deletedAt},
                account_deleted_by = #{operatorId},
                account_delete_reason = #{reason}
            WHERE id = #{userId}
              AND deleted = 0
            """)
    int markAccountDeleted(
            @Param("userId") Long userId,
            @Param("operatorId") Long operatorId,
            @Param("reason") String reason,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Select("SELECT * FROM sys_user WHERE id = #{id} LIMIT 1")
    SysUser selectByIdIncludingDeleted(@Param("id") Long id);

    @Select({
            "<script>",
            "SELECT * FROM sys_user WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<SysUser> selectByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);
}
