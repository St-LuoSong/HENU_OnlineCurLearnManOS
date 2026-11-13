package com.onlinecurlearnmanos.security;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 轻量内存限流：登录 / 注册按来源 IP 计数，AI 接口按登录用户计数（未登录时退回 IP）。
 *
 * <p>固定 60 秒窗口即可挡住脚本撞库与刷接口；单实例部署够用，多实例部署需要把计数器换成
 * Redis 之类的共享存储。窗口计数超过阈值时返回 429 与统一错误信封。</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final long SWEEP_INTERVAL_MILLIS = 300_000L;
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ADMIN_LOGIN_PATH = "/api/v1/auth/admin/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String AI_PATH_PREFIX = "/api/v1/ai";

    private final ObjectMapper objectMapper;
    private final int authPerMinute;
    private final int aiPerMinute;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepAt = new AtomicLong();

    public RateLimitFilter(ObjectMapper objectMapper, int authPerMinute, int aiPerMinute) {
        this(objectMapper, authPerMinute, aiPerMinute, System::currentTimeMillis);
    }

    RateLimitFilter(
            ObjectMapper objectMapper,
            int authPerMinute,
            int aiPerMinute,
            LongSupplier clock
    ) {
        this.objectMapper = objectMapper;
        this.authPerMinute = authPerMinute;
        this.aiPerMinute = aiPerMinute;
        this.clock = clock;
        this.lastSweepAt.set(clock.getAsLong());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String scope = scopeOf(request);
        if (scope == null) {
            filterChain.doFilter(request, response);
            return;
        }
        int limit = "ai".equals(scope) ? aiPerMinute : authPerMinute;
        if (limit > 0 && !acquire(scope + ":" + clientKey(scope, request), limit)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String scopeOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        if (LOGIN_PATH.equals(path) || ADMIN_LOGIN_PATH.equals(path) || REGISTER_PATH.equals(path)) {
            return "auth";
        }
        return path.startsWith(AI_PATH_PREFIX) ? "ai" : null;
    }

    private String clientKey(String scope, HttpServletRequest request) {
        if ("ai".equals(scope)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getName() != null
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                return "user:" + authentication.getName();
            }
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private boolean acquire(String key, int limit) {
        long now = clock.getAsLong();
        Window window = windows.compute(key, (currentKey, existing) -> {
            if (existing == null || now - existing.startedAt >= WINDOW_MILLIS) {
                return new Window(now);
            }
            existing.count++;
            return existing;
        });
        sweepIfNeeded(now);
        return window.count <= limit;
    }

    private void sweepIfNeeded(long now) {
        long previousSweep = lastSweepAt.get();
        if (now - previousSweep < SWEEP_INTERVAL_MILLIS
                || !lastSweepAt.compareAndSet(previousSweep, now)) {
            return;
        }
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
    }

    private void reject(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.TOO_MANY_REQUESTS;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(errorCode.getCode(), errorCode.getMessage())
        );
    }

    private static final class Window {

        private final long startedAt;
        private int count = 1;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
