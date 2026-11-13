package com.onlinecurlearnmanos.auth.service;

import com.onlinecurlearnmanos.auth.dto.AuthResponse;
import com.onlinecurlearnmanos.auth.dto.LoginRequest;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.security.JwtService;
import com.onlinecurlearnmanos.security.TokenRevocationService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 双登录入口隔离（审计 D3）：普通端只接受学生 / 教师，管理端只接受 ADMIN。
 * 角色隔离必须在服务端完成，不能只靠前端隐藏入口。
 */
@ExtendWith(MockitoExtension.class)
class AuthPortalIsolationTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserService userService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private AuthService authService;

    @Test
    void staffPortalAcceptsStudentWithMatchingRole() {
        UserPrincipal principal = stubAuthenticated("STUDENT");
        given(jwtService.generateToken(principal)).willReturn("student-token");

        AuthResponse response = authService.login(
                new LoginRequest("2024001", "Passw0rd123", "STUDENT")
        );

        assertThat(response.accessToken()).isEqualTo("student-token");
        assertThat(response.pendingReview()).isFalse();
    }

    @Test
    void staffPortalRejectsAdminAccount() {
        stubAuthenticated("ADMIN");

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("admin", "123456", null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("管理端入口");
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void staffPortalRejectsRoleMismatch() {
        stubAuthenticated("STUDENT");

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("2024001", "Passw0rd123", "TEACHER")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("教师账号");
    }

    @Test
    void staffPortalRejectsAdminAsExpectedRole() {
        stubAuthenticated("STUDENT");

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("2024001", "Passw0rd123", "ADMIN")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void adminPortalAcceptsAdmin() {
        UserPrincipal principal = stubAuthenticated("ADMIN");
        given(jwtService.generateToken(principal)).willReturn("admin-token");

        AuthResponse response = authService.loginAdmin(new LoginRequest("admin", "123456", null));

        assertThat(response.accessToken()).isEqualTo("admin-token");
        assertThat(response.user().roles()).containsExactly("ADMIN");
    }

    @Test
    void adminPortalRejectsStudent() {
        stubAuthenticated("STUDENT");

        assertThatThrownBy(() -> authService.loginAdmin(
                new LoginRequest("2024001", "Passw0rd123", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有管理员账号");
        verify(jwtService, never()).generateToken(any());
    }

    private UserPrincipal stubAuthenticated(String role) {
        UserPrincipal principal = new UserPrincipal(
                31L,
                "user-" + role,
                "n/a",
                "用户" + role,
                null,
                1,
                List.of(role)
        );
        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
        return principal;
    }
}
