package com.onlinecurlearnmanos.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已吊销的 JWT（按 jti 记录）。
 *
 * <p>登出后把当前 token 写进这张表，认证过滤器每次请求校验一次，
 * 从而让"登出"立即生效，而不是等 token 自然过期。</p>
 */
@Data
@TableName("revoked_token")
public class RevokedToken {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String jti;

    private String username;

    private String reason;

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;
}
