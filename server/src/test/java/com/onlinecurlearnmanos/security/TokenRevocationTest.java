package com.onlinecurlearnmanos.security;

import com.onlinecurlearnmanos.security.entity.RevokedToken;
import com.onlinecurlearnmanos.security.mapper.RevokedTokenMapper;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * JWT 登出失效（审计 C4）：吊销名单要幂等写入，认证过滤器必须拒绝已吊销的 token，
 * 过期记录要被定时清理。
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationTest {

    private static final String JTI = "jti-20260920";

    @Mock
    private RevokedTokenMapper revokedTokenMapper;

    @InjectMocks
    private TokenRevocationService tokenRevocationService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(RevokedToken.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokeWritesTokenOnceWithExpiry() {
        given(revokedTokenMapper.selectCount(any())).willReturn(0L);
        Instant expiresAt = Instant.now().plusSeconds(3600);

        boolean revoked = tokenRevocationService.revoke(JTI, "2024001", expiresAt, "LOGOUT");

        assertThat(revoked).isTrue();
        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenMapper).insert(captor.capture());
        RevokedToken saved = captor.getValue();
        assertThat(saved.getJti()).isEqualTo(JTI);
        assertThat(saved.getUsername()).isEqualTo("2024001");
        assertThat(saved.getReason()).isEqualTo("LOGOUT");
        assertThat(saved.getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
        assertThat(saved.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeSkipsTokenThatIsAlreadyRevoked() {
        given(revokedTokenMapper.selectCount(any())).willReturn(1L);

        boolean revoked = tokenRevocationService.revoke(JTI, "2024001", Instant.now(), "LOGOUT");

        assertThat(revoked).isFalse();
        verify(revokedTokenMapper, never()).insert(any(RevokedToken.class));
    }

    @Test
    void revokeToleratesConcurrentDuplicateInsert() {
        given(revokedTokenMapper.selectCount(any())).willReturn(0L);
        given(revokedTokenMapper.insert(any(RevokedToken.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        boolean revoked = tokenRevocationService.revoke(JTI, null, Instant.now(), "LOGOUT");

        assertThat(revoked).isFalse();
    }

    @Test
    void blankJtiIsNeverTreatedAsRevoked() {
        assertThat(tokenRevocationService.isRevoked(null)).isFalse();
        assertThat(tokenRevocationService.isRevoked("  ")).isFalse();
        verify(revokedTokenMapper, never()).selectCount(any());
    }

    @Test
    void purgeRemovesExpiredEntries() {
        given(revokedTokenMapper.delete(any())).willReturn(3);

        assertThat(tokenRevocationService.purgeExpired()).isEqualTo(3);
    }

    @Test
    void authenticationFilterRejectsRevokedToken() throws Exception {
        UserPrincipal principal = principal();
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        TokenRevocationService revocationService = mock(TokenRevocationService.class);
        given(jwtService.extractUsername("token")).willReturn("2024001");
        given(userDetailsService.loadUserByUsername("2024001")).willReturn(principal);
        given(jwtService.isTokenValid("token", principal)).willReturn(true);
        given(jwtService.extractJti("token")).willReturn(JTI);
        given(revocationService.isRevoked(JTI)).willReturn(true);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userDetailsService, revocationService);

        filter.doFilter(bearerRequest("token"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticationFilterAcceptsTokenThatIsNotRevoked() throws Exception {
        UserPrincipal principal = principal();
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        TokenRevocationService revocationService = mock(TokenRevocationService.class);
        given(jwtService.extractUsername("token")).willReturn("2024001");
        given(userDetailsService.loadUserByUsername("2024001")).willReturn(principal);
        given(jwtService.isTokenValid("token", principal)).willReturn(true);
        given(jwtService.extractJti("token")).willReturn(JTI);
        given(revocationService.isRevoked(anyString())).willReturn(false);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userDetailsService, revocationService);

        filter.doFilter(bearerRequest("token"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("2024001");
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void authenticationFilterRejectsTokenWithStaleTokenVersion() throws Exception {
        // 用户改密后 sys_user.token_version 自增，旧 token 中的 tv 不再匹配，必须立即失效。
        UserPrincipal principal = principalWithTokenVersion(0);
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        TokenRevocationService revocationService = mock(TokenRevocationService.class);
        given(jwtService.extractUsername("token")).willReturn("2024001");
        given(userDetailsService.loadUserByUsername("2024001")).willReturn(principal);
        given(jwtService.isTokenValid("token", principal)).willReturn(true);
        given(jwtService.extractTokenVersion("token")).willReturn(5L);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userDetailsService, revocationService);

        filter.doFilter(bearerRequest("token"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(revocationService, never()).isRevoked(anyString());
    }

    @Test
    void authenticationFilterAcceptsTokenWithMatchingTokenVersion() throws Exception {
        UserPrincipal principal = principalWithTokenVersion(5);
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        TokenRevocationService revocationService = mock(TokenRevocationService.class);
        given(jwtService.extractUsername("token")).willReturn("2024001");
        given(userDetailsService.loadUserByUsername("2024001")).willReturn(principal);
        given(jwtService.isTokenValid("token", principal)).willReturn(true);
        given(jwtService.extractTokenVersion("token")).willReturn(5L);
        given(jwtService.extractJti("token")).willReturn(JTI);
        given(revocationService.isRevoked(JTI)).willReturn(false);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userDetailsService, revocationService);

        filter.doFilter(bearerRequest("token"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private UserPrincipal principal() {
        return new UserPrincipal(31L, "2024001", "n/a", "张三", null, 1, List.of("STUDENT"));
    }

    private UserPrincipal principalWithTokenVersion(int tokenVersion) {
        return new UserPrincipal(
                31L, "2024001", "n/a", "张三", null, 1, List.of("STUDENT"), tokenVersion);
    }
}
