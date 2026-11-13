package com.onlinecurlearnmanos.security;

import com.onlinecurlearnmanos.security.entity.RevokedToken;
import com.onlinecurlearnmanos.security.mapper.RevokedTokenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * JWT 吊销名单。
 *
 * <p>单实例下每次请求多一次主键索引查询；后续如需降延迟，可在此加一层本地缓存或换成 Redis。
 * 过期记录由定时任务清理，表不会无限增长。</p>
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenMapper mapper;

    /**
     * 吊销指定 jti；重复吊销与并发重复写入都视为成功。
     */
    public boolean revoke(String jti, String username, Instant expiresAt, String reason) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        if (isRevoked(jti)) {
            return false;
        }
        RevokedToken entity = new RevokedToken();
        entity.setJti(jti.trim());
        entity.setUsername(StringUtils.hasText(username) ? username.trim() : null);
        entity.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        entity.setExpiresAt(expiresAt == null
                ? LocalDateTime.now().plusDays(1)
                : LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
        entity.setRevokedAt(LocalDateTime.now());
        try {
            mapper.insert(entity);
            return true;
        } catch (DataIntegrityViolationException exception) {
            log.debug("Token {} already revoked", jti);
            return false;
        }
    }

    public boolean isRevoked(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        return mapper.selectCount(new LambdaQueryWrapper<RevokedToken>()
                .eq(RevokedToken::getJti, jti.trim())) > 0;
    }

    @Scheduled(
            fixedDelayString = "${app.security.revocation-purge-interval-ms:3600000}",
            initialDelayString = "${app.security.revocation-purge-initial-delay-ms:600000}"
    )
    public int purgeExpired() {
        int removed = mapper.delete(new LambdaQueryWrapper<RevokedToken>()
                .lt(RevokedToken::getExpiresAt, LocalDateTime.now()));
        if (removed > 0) {
            log.info("Purged {} expired revoked tokens", removed);
        }
        return removed;
    }
}
