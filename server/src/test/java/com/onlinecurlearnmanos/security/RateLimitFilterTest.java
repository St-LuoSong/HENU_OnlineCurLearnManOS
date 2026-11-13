package com.onlinecurlearnmanos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流契约：登录 / 注册按来源 IP 限制，AI 接口按登录用户限制，互不干扰，超出后返回 429。
 */
class RateLimitFilterTest {

    private static final long START_AT = 1_700_000_000_000L;

    private final AtomicLong now = new AtomicLong(START_AT);
    private final RateLimitFilter filter =
            new RateLimitFilter(new ObjectMapper(), 2, 3, now::get);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksLoginAfterThresholdForSameClient() throws Exception {
        assertThat(call(loginRequest("10.0.0.1"), null).getStatus()).isEqualTo(200);
        assertThat(call(loginRequest("10.0.0.1"), null).getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call(loginRequest("10.0.0.1"), null);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void countsEachClientSeparately() throws Exception {
        call(loginRequest("10.0.0.1"), null);
        call(loginRequest("10.0.0.1"), null);

        assertThat(call(loginRequest("10.0.0.2"), null).getStatus()).isEqualTo(200);
    }

    @Test
    void limitsAiCallsPerAuthenticatedUser() throws Exception {
        assertThat(call(aiRequest("10.0.0.1"), "2024001").getStatus()).isEqualTo(200);
        assertThat(call(aiRequest("10.0.0.1"), "2024001").getStatus()).isEqualTo(200);
        assertThat(call(aiRequest("10.0.0.1"), "2024001").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call(aiRequest("10.0.0.1"), "2024001");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(call(aiRequest("10.0.0.1"), "2024002").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesUnthrottledPathsAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cloud-classes");
        request.setRemoteAddr("10.0.0.9");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(call(request, null).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void resetsCounterAfterWindowElapses() throws Exception {
        call(loginRequest("10.0.0.1"), null);
        call(loginRequest("10.0.0.1"), null);
        assertThat(call(loginRequest("10.0.0.1"), null).getStatus()).isEqualTo(429);

        now.addAndGet(61_000L);

        assertThat(call(loginRequest("10.0.0.1"), null).getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest loginRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private MockHttpServletRequest aiRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private MockHttpServletResponse call(MockHttpServletRequest request, String username)
            throws Exception {
        if (username == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(username, "test", List.of())
            );
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
