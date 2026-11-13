package com.onlinecurlearnmanos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${security.jwt.secret:}") String secret,
            @Value("${security.jwt.expiration-minutes:120}") long expirationMinutes
    ) {
        if (secret == null) {
            throw new IllegalStateException("JWT_SECRET 必须配置且长度不少于 32 个字符");
        }
        String normalizedSecret = secret.trim();
        if (normalizedSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 必须配置且长度不少于 32 个字符");
        }
        this.signingKey = Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subject(principal.getUsername())
                .claim("uid", principal.getId())
                .claim("roles", principal.getRoles())
                .claim("tv", principal.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    public Instant extractExpiration(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration == null ? null : expiration.toInstant();
    }

    /**
     * 会话版本。历史 token 没有该声明时按 0 处理（与用户默认 token_version 一致）。
     */
    public long extractTokenVersion(String token) {
        Object value = parseClaims(token).get("tv");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = parseClaims(token);
        return userDetails.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
