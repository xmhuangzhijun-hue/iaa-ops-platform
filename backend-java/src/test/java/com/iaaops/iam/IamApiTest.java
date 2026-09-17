package com.iaaops.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.iaaops.support.PostgresTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * iam 模块的端到端行为：登录、令牌轮换与重放、临时口令拦截、权限可见指标、偏好并发。
 */
class IamApiTest extends PostgresTestBase {

    @BeforeEach
    void seed() {
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from user_preferences");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        insertUser("usr_op", "t.op", "运营", false, "{\"operators\": [\"运营甲\"]}", "operator");
        insertUser("usr_agency", "t.agency", "代理", false, "{\"agencies\": [\"星河代理\"]}", "agency_admin");
        insertUser("usr_new", "t.new", "新人", true, "{}", "readonly");
        insertUser("usr_off", "t.off", "已停用", false, "{}", "readonly");
        jdbc.update("update users set status = 'disabled' where id = 'usr_off'");
    }

    @Test
    void 登录成功后可读取当前账号与导航() throws Exception {
        Map<String, Object> tokens = login("t.op");
        assertThat(tokens.get("token_type")).isEqualTo("bearer");
        assertThat(tokens.get("must_change_password")).isEqualTo(false);
        assertThat((Integer) tokens.get("expires_in")).isEqualTo(900);

        Map<String, Object> me = json(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("access_token")));
        assertThat(me.get("username")).isEqualTo("t.op");
        assertThat(strings(me.get("roles"))).containsExactly("operator");
        assertThat(strings(me.get("permissions"))).containsExactly("agent.execute", "dashboard.read", "metrics.real");
        assertThat(map(me.get("data_scope")).get("operators")).isEqualTo(List.of("运营甲"));
        assertThat(strings(me.get("visible_metrics"))).contains("revenue", "roi");
        assertThat(list(me.get("navigation"))).isNotEmpty();
    }

    @Test
    void 对外口径账号看不到收益类指标() throws Exception {
        Map<String, Object> me = json(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("t.agency")));
        assertThat(strings(me.get("visible_metrics")))
                .doesNotContain("revenue", "roi", "click_arpu", "revenue_gap")
                .contains("cost", "clicks", "ctr", "cpc");
    }

    @Test
    void 口令错误与账号停用返回同一种错误() throws Exception {
        assertThat(errorCode(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "t.op", "password", "wrong-password"))), 401))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(errorCode(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "t.off", "password", PASSWORD))), 401))
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void 无令牌与坏令牌都返回需要登录() throws Exception {
        assertThat(errorCode(get("/api/v1/auth/me"), 401)).isEqualTo("AUTH_REQUIRED");
        assertThat(errorCode(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"), 401))
                .isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void 刷新令牌轮换且重放会吊销全部令牌() throws Exception {
        Map<String, Object> first = login("t.op");
        Map<String, Object> second = json(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refresh_token", first.get("refresh_token")))));
        assertThat(second.get("refresh_token")).isNotEqualTo(first.get("refresh_token"));

        // 旧令牌重放：按泄露处理，该账号全部刷新令牌立即失效
        assertThat(errorCode(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refresh_token", first.get("refresh_token")))), 401))
                .isEqualTo("AUTH_REQUIRED");
        assertThat(errorCode(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refresh_token", second.get("refresh_token")))), 401))
                .isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void 临时口令账号改密前只能访问自身与改密接口() throws Exception {
        Map<String, Object> tokens = login("t.new");
        assertThat(tokens.get("must_change_password")).isEqualTo(true);
        String auth = "Bearer " + tokens.get("access_token");

        assertThat(json(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth)).get("username"))
                .isEqualTo("t.new");
        assertThat(errorCode(get("/api/v1/me/preferences").header(HttpHeaders.AUTHORIZATION, auth), 403))
                .isEqualTo("PASSWORD_CHANGE_REQUIRED");

        Map<String, Object> changed = json(post("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("current_password", PASSWORD, "new_password", "brand-new-password-2026"))));
        assertThat(changed.get("must_change_password")).isEqualTo(false);
        // 改密后旧刷新令牌一律失效
        assertThat(errorCode(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refresh_token", tokens.get("refresh_token")))), 401))
                .isEqualTo("AUTH_REQUIRED");
        assertThat(json(get("/api/v1/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + changed.get("access_token"))).get("revision"))
                .isEqualTo(0);
    }

    @Test
    void 偏好按版本号做并发冲突检测() throws Exception {
        String auth = bearer("t.op");
        Map<String, Object> current = json(get("/api/v1/me/preferences").header(HttpHeaders.AUTHORIZATION, auth));
        assertThat(current.get("revision")).isEqualTo(0);
        assertThat(current.get("theme_preset")).isEqualTo("aurora-blue");

        Map<String, Object> payload = Map.of("theme_mode", "dark", "theme_preset", "arc-purple",
                "table_columns", Map.of("report-aggregate", List.of("date", "cost")), "revision", 0);
        Map<String, Object> saved = json(put("/api/v1/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(payload)));
        assertThat(saved.get("revision")).isEqualTo(1);

        assertThat(errorCode(put("/api/v1/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(payload)), 409))
                .isEqualTo("CONFLICT");
    }

    @Test
    void 自定义主题必须给出主色() throws Exception {
        assertThat(errorCode(put("/api/v1/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer("t.op"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("theme_mode", "dark", "theme_preset", "custom", "revision", 0))), 422))
                .isEqualTo("VALIDATION_FAILED");
    }
}
