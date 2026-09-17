package com.iaaops.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.iaaops.support.PostgresTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * 映射管理、用户与权限、审计日志三组接口的端到端行为。
 *
 * 重点在拒绝路径：谁不能改什么、并发改同一条会怎样、越权授权会不会被挡住。
 */
class AdminApiTest extends PostgresTestBase {

    @BeforeEach
    void seed() {
        jdbc.update("delete from audit_events");
        jdbc.update("delete from account_mappings");
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        insertTenant();

        insertUser("usr_admin", "a.admin", "公司管理员", false, "{}", "company_admin");
        insertUser("usr_agency", "a.agency", "代理管理员", false, "{\"agencies\": [\"星河代理\"]}", "agency_admin");
        insertUser("usr_op", "a.op", "运营", false, "{}", "operator");

        jdbc.update("""
                insert into account_mappings (tenant_id, media, account, agency, product, operator, revision)
                values (?, 'vivo', 'acc-a', '星河代理', '记账', '运营甲', 1)
                """, TENANT);
        jdbc.update("""
                insert into account_mappings (tenant_id, media, account, agency, product, operator, revision)
                values (?, 'oppo', 'acc-b', '蓝鲸代理', '天气', '运营乙', 3)
                """, TENANT);
    }

    private RequestBuilder json(String method, String path, String username, Map<String, Object> payload)
            throws Exception {
        var builder = switch (method) {
            case "PUT" -> put(path);
            case "POST" -> post(path);
            default -> throw new IllegalArgumentException(method);
        };
        return builder.header("Authorization", bearer(username))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(payload));
    }

    // ---------- 映射管理 ----------

    @Test
    void 映射列表支持按媒体与关键词筛选() throws Exception {
        Map<String, Object> all = json(get("/api/v1/mappings/accounts")
                .header("Authorization", bearer("a.admin")));
        assertThat(all.get("total")).isEqualTo(2);

        Map<String, Object> filtered = json(get("/api/v1/mappings/accounts")
                .header("Authorization", bearer("a.admin"))
                .param("media", "vivo"));
        assertThat(filtered.get("total")).isEqualTo(1);
        assertThat(map(list(filtered.get("items")).getFirst()).get("account")).isEqualTo("acc-a");

        Map<String, Object> byKeyword = json(get("/api/v1/mappings/accounts")
                .header("Authorization", bearer("a.admin"))
                .param("keyword", "天气"));
        assertThat(map(list(byKeyword.get("items")).getFirst()).get("account")).isEqualTo("acc-b");
    }

    @Test
    void 只有映射管理权限的账号能改映射() throws Exception {
        assertThat(errorCode(get("/api/v1/mappings/accounts").header("Authorization", bearer("a.op")), 403))
                .isEqualTo("FORBIDDEN");
        assertThat(errorCode(json("PUT", "/api/v1/mappings/accounts", "a.op",
                Map.of("items", List.of(Map.of("account", "acc-a", "media", "vivo", "revision", 1)))), 403))
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void 映射写入区分新增修改与无变化() throws Exception {
        Map<String, Object> result = json(json("PUT", "/api/v1/mappings/accounts", "a.admin", Map.of("items", List.of(
                // 新增：不带 revision
                Map.of("account", "acc-c", "media", "vivo", "agency", "青橙代理", "product", "小说"),
                // 修改：带上最后读到的 revision
                Map.of("account", "acc-a", "media", "vivo", "agency", "星河代理", "product", "记账",
                        "operator", "运营丙", "revision", 1),
                // 三个归属字段都没变：算 unchanged，不该把 revision 白白加一
                Map.of("account", "acc-b", "media", "oppo", "agency", "蓝鲸代理", "product", "天气",
                        "operator", "运营乙", "revision", 3)))));

        assertThat(result).containsEntry("created", 1).containsEntry("updated", 1).containsEntry("unchanged", 1);
        assertThat(jdbc.queryForObject(
                "select revision from account_mappings where account = 'acc-b'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select operator from account_mappings where account = 'acc-a'", String.class)).isEqualTo("运营丙");
    }

    @Test
    void 任一条版本对不上整批都不写() throws Exception {
        assertThat(errorCode(json("PUT", "/api/v1/mappings/accounts", "a.admin", Map.of("items", List.of(
                Map.of("account", "acc-c", "media", "vivo", "product", "小说"),
                // acc-a 当前是 1，这里故意给 99
                Map.of("account", "acc-a", "media", "vivo", "product", "记账", "revision", 99)))), 409))
                .isEqualTo("CONFLICT");

        // 整批回滚：新增的那条也不能留下
        assertThat(jdbc.queryForObject(
                "select count(*) from account_mappings where account = 'acc-c'", Integer.class)).isZero();
    }

    @Test
    void 新建已存在的映射与重复条目都被拒() throws Exception {
        assertThat(errorCode(json("PUT", "/api/v1/mappings/accounts", "a.admin",
                Map.of("items", List.of(Map.of("account", "acc-a", "media", "vivo", "product", "记账")))), 409))
                .isEqualTo("CONFLICT");

        assertThat(errorCode(json("PUT", "/api/v1/mappings/accounts", "a.admin", Map.of("items", List.of(
                Map.of("account", "acc-x", "media", "vivo", "product", "甲"),
                Map.of("account", "acc-x", "media", "vivo", "product", "乙")))), 422))
                .isEqualTo("VALIDATION_FAILED");
    }

    // ---------- 用户与权限 ----------

    @Test
    void 创建账号只返回一次临时口令且必须改密() throws Exception {
        Map<String, Object> created = json(mvc.perform(json("POST", "/api/v1/users", "a.admin", Map.of(
                "username", "new.operator", "display_name", "新运营",
                "roles", List.of("operator"), "data_scope", Map.of()))).andReturn(), 201);

        assertThat(created.get("must_change_password")).isEqualTo(true);
        String password = (String) created.get("one_time_password");
        assertThat(password).hasSize(16);
        assertThat(map(created.get("user")).get("username")).isEqualTo("new.operator");

        // 新账号能用这个口令登录，但登录后处于必须改密状态
        Map<String, Object> tokens = json(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", "new.operator", "password", password))));
        assertThat(tokens.get("must_change_password")).isEqualTo(true);
    }

    @Test
    void 不能授出自己没有的权限() throws Exception {
        // 代理管理员想造一个公司管理员
        assertThat(errorCode(json("POST", "/api/v1/users", "a.agency", Map.of(
                "username", "sneaky.admin", "display_name", "越权",
                "roles", List.of("company_admin"), "data_scope", Map.of())), 403))
                .isEqualTo("FORBIDDEN");
        assertThat(jdbc.queryForObject(
                "select count(*) from users where username = 'sneaky.admin'", Integer.class)).isZero();
    }

    @Test
    void 不能把数据范围放得比自己宽() throws Exception {
        // 星河代理的管理员想给别人开蓝鲸代理的数据
        assertThat(errorCode(json("POST", "/api/v1/users", "a.agency", Map.of(
                "username", "wide.scope", "display_name", "越界",
                "roles", List.of("customer"), "data_scope", Map.of("agencies", List.of("蓝鲸代理")))), 403))
                .isEqualTo("FORBIDDEN");

        // 不写范围等于不限，同样越界
        assertThat(errorCode(json("POST", "/api/v1/users", "a.agency", Map.of(
                "username", "no.scope", "display_name", "不限",
                "roles", List.of("customer"), "data_scope", Map.of())), 403))
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void 真实口径与对外口径不能同时授予() throws Exception {
        assertThat(errorCode(json("POST", "/api/v1/users", "a.admin", Map.of(
                "username", "both.metrics", "display_name", "双口径",
                "roles", List.of("operator", "customer"), "data_scope", Map.of())), 422))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void 改角色要带版本号且能改范围() throws Exception {
        int revision = jdbc.queryForObject("select revision from users where id = 'usr_op'", Integer.class);
        Map<String, Object> updated = json(json("PUT", "/api/v1/users/usr_op/roles", "a.admin", Map.of(
                "roles", List.of("readonly"),
                "data_scope", Map.of("products", List.of("记账")),
                "revision", revision)));
        assertThat(strings(updated.get("roles"))).containsExactly("readonly");
        assertThat(map(updated.get("data_scope")).get("products")).isEqualTo(List.of("记账"));
        assertThat((Integer) updated.get("revision")).isEqualTo(revision + 1);

        // 拿旧版本号再改一次：应当被挡住
        assertThat(errorCode(json("PUT", "/api/v1/users/usr_op/roles", "a.admin", Map.of(
                "roles", List.of("operator"), "data_scope", Map.of(), "revision", revision)), 409))
                .isEqualTo("CONFLICT");
    }

    @Test
    void 代理管理员看不到也改不了别人代理的账号() throws Exception {
        Map<String, Object> visible = json(get("/api/v1/users").header("Authorization", bearer("a.agency")));
        assertThat(list(visible.get("items")).stream().map(PostgresTestBase::map)
                .map(user -> user.get("username"))).containsExactly("a.agency");

        assertThat(errorCode(json("PUT", "/api/v1/users/usr_op/roles", "a.agency", Map.of(
                "roles", List.of("readonly"), "data_scope", Map.of("agencies", List.of("星河代理")),
                "revision", 1)), 404))
                .isEqualTo("NOT_FOUND");
    }

    // ---------- 审计日志 ----------

    @Test
    void 写操作留痕且带前后值() throws Exception {
        json(json("PUT", "/api/v1/mappings/accounts", "a.admin", Map.of("items", List.of(
                Map.of("account", "acc-a", "media", "vivo", "agency", "星河代理", "product", "记账",
                        "operator", "运营丁", "revision", 1)))));

        Map<String, Object> page = json(get("/api/v1/audit-events").header("Authorization", bearer("a.admin")));
        Map<String, Object> event = map(list(page.get("items")).getFirst());
        assertThat(event.get("action")).isEqualTo("mapping.upsert");
        assertThat(event.get("actor")).isEqualTo("a.admin");
        // 一条被改动的映射记一条审计，target_id 指向具体账户
        assertThat(event.get("target_id")).isEqualTo("vivo/acc-a");
        Map<String, Object> detail = map(event.get("detail"));
        assertThat(detail.get("operator_before")).isEqualTo("运营甲");
        assertThat(detail.get("operator_after")).isEqualTo("运营丁");
        assertThat(detail.get("created")).isEqualTo(false);
    }

    @Test
    void 审计游标分页不重不漏() throws Exception {
        for (int index = 0; index < 5; index++) {
            json(json("POST", "/api/v1/users", "a.admin", Map.of(
                    "username", "seq.user" + index, "display_name", "批量" + index,
                    "roles", List.of("readonly"), "data_scope", Map.of())), 201);
        }

        Map<String, Object> first = json(get("/api/v1/audit-events")
                .header("Authorization", bearer("a.admin")).param("limit", "2"));
        assertThat(list(first.get("items"))).hasSize(2);
        assertThat(first.get("next_cursor")).isNotNull();

        Map<String, Object> second = json(get("/api/v1/audit-events")
                .header("Authorization", bearer("a.admin"))
                .param("limit", "2")
                .param("cursor", (String) first.get("next_cursor")));
        List<Object> firstIds = list(first.get("items")).stream().map(PostgresTestBase::map)
                .map(item -> item.get("id")).map(Object.class::cast).toList();
        List<Object> secondIds = list(second.get("items")).stream().map(PostgresTestBase::map)
                .map(item -> item.get("id")).map(Object.class::cast).toList();
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
    }

    @Test
    void 审计日志按动作筛选且无权账号看不到() throws Exception {
        json(json("POST", "/api/v1/users", "a.admin", Map.of(
                "username", "audit.user", "display_name", "审计", "roles", List.of("readonly"),
                "data_scope", Map.of())), 201);

        Map<String, Object> filtered = json(get("/api/v1/audit-events")
                .header("Authorization", bearer("a.admin")).param("action", "user.create"));
        assertThat(list(filtered.get("items"))).isNotEmpty();
        assertThat(list(filtered.get("items")).stream().map(PostgresTestBase::map)
                .map(item -> item.get("action"))).containsOnly("user.create");

        Map<String, Object> none = json(get("/api/v1/audit-events")
                .header("Authorization", bearer("a.admin")).param("action", "nothing.here"));
        assertThat(list(none.get("items"))).isEmpty();

        assertThat(errorCode(get("/api/v1/audit-events").header("Authorization", bearer("a.op")), 403))
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void 坏游标直接拒绝而不是当成第一页() throws Exception {
        assertThat(errorCode(get("/api/v1/audit-events")
                .header("Authorization", bearer("a.admin")).param("cursor", "not-a-cursor"), 422))
                .isEqualTo("VALIDATION_FAILED");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return objectMapper.readValue(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
    }

    private Map<String, Object> json(RequestBuilder request, int expectedStatus) throws Exception {
        return json(mvc.perform(request).andReturn(), expectedStatus);
    }
}
