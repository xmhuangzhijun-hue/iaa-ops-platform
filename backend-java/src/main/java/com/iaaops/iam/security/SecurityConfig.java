package com.iaaops.iam.security;

import com.iaaops.shared.error.ErrorCode;
import com.iaaops.shared.error.ErrorResponse;
// Spring Boot 4 默认 Jackson 3：databind 的包名是 tools.jackson.databind，注解仍在 com.fasterxml.jackson.annotation
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    Clock loginClock() {
        return Clock.systemUTC();
    }

    // 认证与授权的装配属于 iam 模块：共享内核不反向依赖业务模块。

    /** 公开路径：健康检查、指标口径目录与登录换发；其余一律要令牌。 */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/health", "/api/v1/metrics", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                write(response, objectMapper, ErrorCode.AUTH_REQUIRED, "需要登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                write(response, objectMapper, ErrorCode.FORBIDDEN, "没有该操作的权限")))
                .build();
    }

    /**
     * Argon2id，参数与现有 Python 侧一致，保证既有账号口令可直接校验：
     * 盐 16 字节、哈希 32 字节、并行 1、内存 64 MiB、迭代 3。
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 1 << 16, 3);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of("http://127.0.0.1:5173", "http://localhost:5173"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code, String title)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, title, null, null));
    }
}
