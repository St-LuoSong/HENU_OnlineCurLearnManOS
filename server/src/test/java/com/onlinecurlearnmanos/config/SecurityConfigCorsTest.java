package com.onlinecurlearnmanos.config;

import com.onlinecurlearnmanos.common.web.RestAccessDeniedHandler;
import com.onlinecurlearnmanos.common.web.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 跨域策略回归：必须按配置的白名单放行，不允许再退化成允许任意来源携带凭证。
 */
class SecurityConfigCorsTest {

    @Test
    void restrictsAllowedOriginsToConfiguredWhitelist() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(RestAuthenticationEntryPoint.class),
                mock(RestAccessDeniedHandler.class)
        );
        Field allowedOrigins = SecurityConfig.class.getDeclaredField("allowedOrigins");
        allowedOrigins.setAccessible(true);
        allowedOrigins.set(securityConfig, List.of("http://localhost:5173"));

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/v1/health")
        );

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOriginPatterns())
                .containsExactly("http://localhost:5173");
        assertThat(configuration.getAllowedMethods())
                .contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
