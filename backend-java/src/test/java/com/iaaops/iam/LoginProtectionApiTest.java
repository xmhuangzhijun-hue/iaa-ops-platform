package com.iaaops.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.iaaops.support.PostgresTestBase;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 通过真实认证接口验证锁定、来源地址和事务提交边界，时间推进不需要等待。 */
class LoginProtectionApiTest extends PostgresTestBase {
    private static final String USER = "protection.active";
    private static final String DISABLED = "protection.disabled";
    private static final String IP = "192.0.2.10";
    private static final String OTHER_IP = "192.0.2.11";
    private static final String WRONG = "incorrect-password";
    private static final AtomicLong DAY = new AtomicLong();

    @MockitoBean(name = "loginClock")
    Clock clock;

    @Autowired
    AuthService auth;
    @Autowired
    PlatformTransactionManager transactions;

    private Instant now;

    @BeforeEach
    void seed() {
        // 此类复用 Spring 上下文，推进一天让上一用例的计数自然到期。
        now = Instant.parse("2030-01-01T00:00:00Z").plus(Duration.ofDays(DAY.incrementAndGet()));
        when(clock.instant()).thenAnswer(invocation -> now);
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from user_preferences");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        insertUser("usr_protection", USER, "测试账号", false, "{}", "readonly");
        insertUser("usr_protection_off", DISABLED, "停用账号", false, "{}", "readonly");
        jdbc.update("update users set status = 'disabled' where id = 'usr_protection_off'");
    }

    @Test
    void 账号锁定跨来源生效且拒绝请求不会延长锁期() throws Exception {
        fail(USER, IP, 5);
        rejected(loginRequest(USER, PASSWORD, OTHER_IP));
        advance(Duration.ofMinutes(14).plusSeconds(59));
        rejected(loginRequest(USER, PASSWORD, OTHER_IP));
        advance(Duration.ofSeconds(1));
        assertThat(json(loginRequest(USER, PASSWORD, OTHER_IP))).containsKey("access_token");
    }

    @Test
    void 来源阈值跨账号累计且转发头不能冒充新来源() throws Exception {
        for (int index = 0; index < 20; index++) {
            rejected(loginRequest("missing.ip." + index, WRONG, IP)
                    .header("X-Forwarded-For", "198.51.100." + (index + 1))
                    .header("Forwarded", "for=203.0.113." + (index + 1)));
        }
        rejected(loginRequest(USER, PASSWORD, IP)
                .header("X-Forwarded-For", OTHER_IP).header("Forwarded", "for=" + OTHER_IP));
        // 真正不同的连接来源仍可登录，不受伪造头影响。
        assertThat(json(loginRequest(USER, PASSWORD, OTHER_IP))).containsKey("access_token");
        advance(Duration.ofMinutes(15));
        assertThat(json(loginRequest(USER, PASSWORD, IP))).containsKey("access_token");
    }

    @Test
    void 错误停用不存在和锁定的完整响应相同() throws Exception {
        Map<String, Object> ordinary = rejected(loginRequest(USER, WRONG, IP));
        assertThat(rejected(loginRequest(DISABLED, PASSWORD, OTHER_IP))).isEqualTo(ordinary);
        assertThat(rejected(loginRequest("missing.generic", PASSWORD, "192.0.2.12"))).isEqualTo(ordinary);
        fail(USER, IP, 4);
        assertThat(rejected(loginRequest(USER, PASSWORD, "192.0.2.13"))).isEqualTo(ordinary);
        fail(DISABLED, OTHER_IP, 4);
        assertThat(rejected(loginRequest(DISABLED, PASSWORD, "192.0.2.14"))).isEqualTo(ordinary);
        fail("missing.generic", "192.0.2.12", 4);
        assertThat(rejected(loginRequest("missing.generic", PASSWORD, "192.0.2.15"))).isEqualTo(ordinary);
    }

    @Test
    void 未达阈值的失败在窗口到期后不再累计() throws Exception {
        fail(USER, IP, 4);
        advance(Duration.ofMinutes(15));
        fail(USER, IP, 4);
        assertThat(json(loginRequest(USER, PASSWORD, IP))).containsKey("access_token");
    }

    @Test
    void 成功登录同时清零账号和当前来源的失败() throws Exception {
        for (int round = 0; round < 2; round++) {
            for (int index = 0; index < 15; index++) {
                rejected(loginRequest("missing.success." + round + "." + index, WRONG, IP));
            }
            fail(USER, IP, 4);
            assertThat(json(loginRequest(USER, PASSWORD, IP))).containsKey("access_token");
        }
    }

    @Test
    void 已登录账号改密提交后解除账号及当前来源锁定() throws Exception {
        String token = (String) json(loginRequest(USER, PASSWORD, IP)).get("access_token");
        fail(USER, IP, 5);
        for (int index = 0; index < 15; index++) {
            rejected(loginRequest("missing.password." + index, WRONG, IP));
        }
        rejected(loginRequest(USER, PASSWORD, IP));
        String replacement = "replacement-password-2026";
        assertThat(json(passwordRequest(token, PASSWORD, replacement, IP))).containsKey("access_token");
        assertThat(json(loginRequest(USER, replacement, IP))).containsKey("access_token");
    }

    @Test
    void 改密失败不会解除锁定() throws Exception {
        String token = (String) json(loginRequest(USER, PASSWORD, IP)).get("access_token");
        fail(USER, IP, 5);
        assertThat(errorCode(passwordRequest(token, WRONG, "replacement-password-2026", IP), 401))
                .isEqualTo("INVALID_CREDENTIALS");
        rejected(loginRequest(USER, PASSWORD, IP));
    }

    @Test
    void 登录事务回滚不会清零此前失败() throws Exception {
        fail(USER, IP, 4);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            auth.login(USER, PASSWORD, IP);
            transaction.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isZero();
        fail(USER, IP, 1);
        rejected(loginRequest(USER, PASSWORD, OTHER_IP));
    }

    @Test
    void 改密事务回滚不解除锁定也不改变口令() throws Exception {
        var current = auth.loadCurrentUser("usr_protection").orElseThrow();
        fail(USER, IP, 5);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            auth.changePassword(current, PASSWORD, "replacement-password-2026", IP);
            transaction.setRollbackOnly();
        });
        rejected(loginRequest(USER, PASSWORD, OTHER_IP));
        advance(Duration.ofMinutes(15));
        assertThat(json(loginRequest(USER, PASSWORD, IP))).containsKey("access_token");
    }

    private MockHttpServletRequestBuilder loginRequest(String username, String password, String address) {
        return post("/api/v1/auth/login").with(request -> {
            request.setRemoteAddr(address);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("username", username, "password", password)));
    }

    private MockHttpServletRequestBuilder passwordRequest(String token, String current, String replacement,
            String address) {
        return post("/api/v1/auth/password").with(request -> {
            request.setRemoteAddr(address);
            return request;
        }).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("current_password", current, "new_password", replacement)));
    }

    private void fail(String username, String address, int count) throws Exception {
        for (int index = 0; index < count; index++) rejected(loginRequest(username, WRONG, address));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rejected(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> problem = objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        assertThat(problem).containsEntry("code", "INVALID_CREDENTIALS");
        return problem;
    }

    private void advance(Duration duration) {
        now = now.plus(duration);
    }
}
