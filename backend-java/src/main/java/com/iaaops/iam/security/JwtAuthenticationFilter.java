package com.iaaops.iam.security;

import com.iaaops.iam.AuthService;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.shared.error.ErrorCode;
import com.iaaops.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    /** 临时口令状态下仍可访问的路径。 */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED =
            Set.of("/api/v1/auth/me", "/api/v1/auth/password", "/api/v1/auth/logout");

    private final JwtService jwtService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    JwtAuthenticationFilter(JwtService jwtService, AuthService authService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<String> subject = jwtService.resolveSubject(header.substring(BEARER.length()));
        if (subject.isEmpty()) {
            writeProblem(response, ErrorCode.AUTH_REQUIRED, "令牌无效或已过期");
            return;
        }
        Optional<CurrentUser> user = subject.flatMap(authService::loadCurrentUser);
        if (user.isEmpty()) {
            writeProblem(response, ErrorCode.AUTH_REQUIRED, "账号不存在或已停用");
            return;
        }

        CurrentUser current = user.get();
        if (current.mustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(request.getRequestURI())) {
            writeProblem(response, ErrorCode.PASSWORD_CHANGE_REQUIRED, "请先修改临时口令");
            return;
        }

        List<SimpleGrantedAuthority> authorities = current.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(current, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void writeProblem(HttpServletResponse response, ErrorCode code, String title) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, title, null, null));
    }
}
