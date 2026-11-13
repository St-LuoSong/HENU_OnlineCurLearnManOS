package com.onlinecurlearnmanos.security;

import com.onlinecurlearnmanos.auth.service.AuthService;
import com.onlinecurlearnmanos.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 登出实现：带 Bearer token 调用时按 jti 写吊销名单；缺失 / 非法 / 过期 token 不报错也不再吊销。
 */
class JwtLogoutTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 120);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);
    private final AuthService authService = new AuthService(
            mock(AuthenticationManager.class),
            jwtService,
            mock(UserService.class),
            mock(UserDetailsService.class),
            revocationService
    );

    @Test
    void generatedTokensCarryUniqueJtiAndExpiry() {
        UserPrincipal principal = principal();

        String first = jwtService.generateToken(principal);
        String second = jwtService.generateToken(principal);

        assertThat(jwtService.extractJti(first)).isNotBlank();
        assertThat(jwtService.extractJti(first)).isNotEqualTo(jwtService.extractJti(second));
        assertThat(jwtService.extractExpiration(first)).isAfter(Instant.now());
    }

    @Test
    void logoutRevokesCurrentToken() {
        UserPrincipal principal = principal();
        String token = jwtService.generateToken(principal);

        authService.logout("Bearer " + token);

        verify(revocationService).revoke(
                eq(jwtService.extractJti(token)),
                eq("2024001"),
                any(Instant.class),
                eq("LOGOUT")
        );
    }

    @Test
    void logoutIgnoresMissingOrForeignAuthorizationHeader() {
        authService.logout(null);
        authService.logout("Basic abcdef");
        authService.logout("Bearer    ");

        verifyNoInteractions(revocationService);
    }

    @Test
    void logoutIgnoresMalformedToken() {
        authService.logout("Bearer not-a-real-jwt");

        verifyNoInteractions(revocationService);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(31L, "2024001", "n/a", "张三", null, 1, List.of("STUDENT"));
    }
}
