package com.onlinecurlearnmanos.auth.service;

import com.onlinecurlearnmanos.auth.dto.AuthResponse;
import com.onlinecurlearnmanos.auth.dto.LoginRequest;
import com.onlinecurlearnmanos.auth.dto.RegisterRequest;
import com.onlinecurlearnmanos.auth.dto.UserResponse;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.security.JwtService;
import com.onlinecurlearnmanos.security.TokenRevocationService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.service.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.JwtException;
import java.time.Instant;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserService userService,
            UserDetailsService userDetailsService,
            TokenRevocationService tokenRevocationService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        SysUser user = userService.register(
                request.username().trim(),
                request.password(),
                request.displayName().trim(),
                normalizeEmail(request.email()),
                request.role()
        );
        UserPrincipal principal = loadPrincipal(user.getUsername());
        if (!principal.isEnabled()) {
            // 教师自助注册后进入待审核状态，此时不签发可用的登录凭证。
            return new AuthResponse(null, UserResponse.from(principal), true);
        }
        return createAuthResponse(principal);
    }

    /**
     * 普通端登录（学生 / 教师）。管理员账号被拒绝，必须走 {@link #loginAdmin}。
     */
    public AuthResponse login(LoginRequest request) {
        UserPrincipal principal = authenticate(request);
        if (principal.getRoles().contains("ADMIN")) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "管理员账号请使用管理端入口登录"
            );
        }
        String expectedRole = normalizeExpectedRole(request.role());
        if (expectedRole != null && !principal.getRoles().contains(expectedRole)) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "该账号不是" + roleLabel(expectedRole) + "，请重新选择身份"
            );
        }
        return createAuthResponse(principal);
    }

    /**
     * 管理端登录：只接受 ADMIN 账号。
     */
    public AuthResponse loginAdmin(LoginRequest request) {
        UserPrincipal principal = authenticate(request);
        if (!principal.getRoles().contains("ADMIN")) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "只有管理员账号可以使用管理端入口登录"
            );
        }
        return createAuthResponse(principal);
    }

    private UserPrincipal authenticate(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username().trim(),
                            request.password()
                    )
            );
        } catch (DisabledException exception) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "账号不可用：已被停用或等待管理员审核"
            );
        } catch (LockedException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号已被锁定，请联系管理员");
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "学号/工号或密码不正确");
        }
        return (UserPrincipal) authentication.getPrincipal();
    }

    private String normalizeExpectedRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String normalized = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"STUDENT".equals(normalized) && !"TEACHER".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录身份只能是学生或教师");
        }
        return normalized;
    }

    private String roleLabel(String role) {
        return "TEACHER".equals(role) ? "教师账号" : "学生账号";
    }

    public UserResponse currentUser(UserPrincipal principal) {
        return UserResponse.from(principal);
    }

    /**
     * 登出：把当前 token 的 jti 写入吊销名单，使旧凭证立即失效。
     * token 缺失、已过期或非法时直接视为已登出，不报错。
     */
    public void logout(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }
        String token = authorization.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            String jti = jwtService.extractJti(token);
            Instant expiresAt = jwtService.extractExpiration(token);
            tokenRevocationService.revoke(
                    jti,
                    jwtService.extractUsername(token),
                    expiresAt,
                    "LOGOUT"
            );
        } catch (JwtException | IllegalArgumentException ignored) {
            // 过期或非法 token 本来就不能再用，无需吊销
        }
    }

    private AuthResponse createAuthResponse(UserPrincipal principal) {
        return new AuthResponse(
                jwtService.generateToken(principal),
                UserResponse.from(principal),
                false
        );
    }

    private UserPrincipal loadPrincipal(String username) {
        return (UserPrincipal) userDetailsService.loadUserByUsername(username);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }
}
