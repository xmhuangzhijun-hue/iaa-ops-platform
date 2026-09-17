package com.iaaops.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityCorsTest {
    @Test
    void configuredPreviewOriginAllowsBrowserPreflightAndKeepsOtherPortsClosed() throws Exception {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource("  http://127.0.0.1:5180 , https://ops.example.test  ");
        assertThat(preflight(source, "http://127.0.0.1:5180").getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://127.0.0.1:5180");
        assertThat(preflight(source, "https://ops.example.test").getStatus()).isEqualTo(200);
        assertThat(preflight(source, "http://127.0.0.1:5173").getStatus()).isEqualTo(403);
        assertThat(preflight(source, "https://other.example.test").getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "*", "http://*.example.test", "http://localhost:5180,",
            "null", "https://ops.example.test/path", "https://name@ops.example.test"})
    void unsafeOrEmptyOriginConfigurationFailsClosed(String origins) {
        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(origins))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.cors.allowed-origins must contain explicit HTTP(S) origins");
    }

    private static MockHttpServletResponse preflight(CorsConfigurationSource source, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/agent/runs");
        request.setServletPath("/api/v1/agent/runs");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}
