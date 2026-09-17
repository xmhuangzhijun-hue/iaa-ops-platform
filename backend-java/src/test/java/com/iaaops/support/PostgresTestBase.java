package com.iaaops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * 集成测试的共同底座：一个 PostgreSQL 容器 + 一份 Spring 上下文，供各模块的用例复用。
 *
 * 表结构由 {@code db/migration} 下的迁移脚本建出，与生产同一份；实体由 {@code ddl-auto: validate} 校验。
 */
@SpringBootTest(properties = {
        // 测试库由与生产同一份迁移脚本建出，再用 validate 校验实体——
        // 如果测试自己建表，迁移写错了测试反而发现不了。
        "spring.jpa.hibernate.ddl-auto=validate",
})
@AutoConfigureMockMvc
public abstract class PostgresTestBase {

    // 单例容器：整个测试 JVM 共用一个库与一份 Spring 上下文。
    // 不用 @Testcontainers + @Container——那会在每个测试类结束时停掉容器，
    // 而 Spring 上下文是跨类缓存的，第二个类就会连到一个已经关掉的库。
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-bookworm");

    static {
        POSTGRES.start();
    }

    protected static final String PASSWORD = "test-password-2026";
    protected static final String TENANT = "tenant_demo";

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry registry) {
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        registry.add("app.jwt-signing-key", () -> Base64.getEncoder().encodeToString(random));
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected ObjectMapper objectMapper;

    /** 租户是所有业务表的外键根，建账号前先保证它在。 */
    protected void insertTenant() {
        jdbc.update("insert into tenants (id, name) values (?, ?) on conflict (id) do nothing", TENANT, "演示租户");
    }

    protected void insertUser(String id, String username, String displayName, boolean mustChange, String scope,
            String role) {
        insertTenant();
        jdbc.update("""
                insert into users (id, tenant_id, username, display_name, password_hash, status,
                                   must_change_password, data_scope, revision)
                values (?, ?, ?, ?, ?, 'active', ?, cast(? as jsonb), 0)
                """, id, TENANT, username, displayName, passwordEncoder.encode(PASSWORD), mustChange, scope);
        jdbc.update("insert into user_roles (user_id, role) values (?, ?)", id, role);
    }

    protected Map<String, Object> login(String username) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", username, "password", PASSWORD))))
                .andReturn());
    }

    protected String bearer(String username) throws Exception {
        return "Bearer " + login(username).get("access_token");
    }

    protected String body(Map<String, Object> value) {
        return objectMapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> json(RequestBuilder request) throws Exception {
        return json(mvc.perform(request).andReturn());
    }

    @SuppressWarnings("unchecked")
    protected String errorCode(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> problem =
                objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        return (String) problem.get("code");
    }

    @SuppressWarnings("unchecked")
    protected static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    protected static List<String> strings(Object value) {
        return (List<String>) value;
    }
}
