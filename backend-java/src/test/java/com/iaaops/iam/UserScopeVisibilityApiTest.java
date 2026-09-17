package com.iaaops.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.iaaops.support.PostgresTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 账号管理的可见范围必须同时受代理、产品、运营约束，不能借收窄授权接管范围外账号。 */
class UserScopeVisibilityApiTest extends PostgresTestBase {

    private static final String ACTOR = "scope.actor";
    private static final List<String> HIDDEN_TARGETS =
            List.of("broader", "outside", "missing", "null", "empty");

    @BeforeEach
    void clearUsers() {
        jdbc.update("delete from audit_events");
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from user_preferences");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
    }

    @ParameterizedTest
    @ValueSource(strings = {"agencies", "products", "operators"})
    void 受限管理员只能列出同范围或非空子范围账号(String dimension) throws Exception {
        seedDimension(dimension);
        String auth = bearer(ACTOR);

        Map<String, Object> result = json(get("/api/v1/users")
                .param("page_size", "200")
                .header(HttpHeaders.AUTHORIZATION, auth));

        assertThat(userIds(result)).containsExactlyInAnyOrder("scope_actor", "scope_same", "scope_narrower");
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(3);

        for (String suffix : List.of("same", "narrower")) {
            String targetId = "scope_" + suffix;
            Map<String, Object> updated = json(put("/api/v1/users/{userId}/roles", targetId)
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of(
                            "roles", List.of("operator"),
                            "data_scope", Map.of(dimension, List.of("范围甲")),
                            "revision", 0))));
            assertThat(updated.get("revision")).isEqualTo(1);
            assertThat(map(updated.get("data_scope")).get(dimension)).isEqualTo(List.of("范围甲"));
            assertThat(persistedUser(targetId).get("revision")).isEqualTo(1);
            assertThat(jdbc.queryForObject("select data_scope -> ? from users where id = ?",
                    String.class, dimension, targetId)).isEqualTo("[\"范围甲\"]");
            assertThat(persistedRoles(targetId)).containsExactly("operator");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"agencies", "products", "operators"})
    void 把新授权收窄也不能修改原本不可见的账号(String dimension) throws Exception {
        seedDimension(dimension);
        String auth = bearer(ACTOR);

        for (String suffix : HIDDEN_TARGETS) {
            String targetId = "scope_" + suffix;
            Map<String, Object> before = persistedUser(targetId);
            List<String> rolesBefore = persistedRoles(targetId);

            assertThat(errorCode(put("/api/v1/users/{userId}/roles", targetId)
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of(
                            "roles", List.of("operator"),
                            "data_scope", Map.of(dimension, List.of("范围甲")),
                            "revision", 0))), 404))
                    .as("%s must remain inaccessible for actor constrained by %s", suffix, dimension)
                    .isEqualTo("NOT_FOUND");

            assertThat(persistedUser(targetId)).isEqualTo(before);
            assertThat(persistedRoles(targetId)).isEqualTo(rolesBefore);
        }

        assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"agencies\":null,\"products\":null,\"operators\":null}"})
    void 未限制范围的管理员仍可查看并管理所有范围(String actorScope) throws Exception {
        seedDimension("products");
        jdbc.update("update users set data_scope = cast(? as jsonb) where id = 'scope_actor'", actorScope);
        String auth = bearer(ACTOR);

        Map<String, Object> result = json(get("/api/v1/users")
                .param("page_size", "200")
                .header(HttpHeaders.AUTHORIZATION, auth));
        assertThat(userIds(result)).containsExactlyInAnyOrder(
                "scope_actor", "scope_same", "scope_narrower", "scope_broader", "scope_outside",
                "scope_missing", "scope_null", "scope_empty");
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(8);

        Map<String, Object> updated = json(put("/api/v1/users/scope_outside/roles")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("roles", List.of("operator"), "data_scope", Map.of(), "revision", 0))));
        assertThat(updated.get("revision")).isEqualTo(1);
        assertThat(persistedRoles("scope_outside")).containsExactly("operator");
        assertThat(persistedUser("scope_outside").get("data_scope")).isEqualTo("{}");
    }

    @Test
    void 多维约束必须全部满足() throws Exception {
        seedDimension("agencies");
        Map<String, Object> matchingScope = Map.of(
                "agencies", List.of("范围甲"),
                "products", List.of("产品甲"),
                "operators", List.of("运营甲"));
        for (String suffix : List.of("actor", "same", "narrower")) {
            jdbc.update("update users set data_scope = cast(? as jsonb) where id = ?",
                    body(matchingScope), "scope_" + suffix);
        }
        insertTarget("outside_product", body(Map.of(
                "agencies", List.of("范围甲"), "products", List.of("产品乙"), "operators", List.of("运营甲"))));
        insertTarget("outside_operator", body(Map.of(
                "agencies", List.of("范围甲"), "products", List.of("产品甲"), "operators", List.of("运营乙"))));

        Map<String, Object> result = json(get("/api/v1/users")
                .param("page_size", "200")
                .header(HttpHeaders.AUTHORIZATION, bearer(ACTOR)));
        assertThat(userIds(result)).containsExactlyInAnyOrder("scope_actor", "scope_same", "scope_narrower");
    }

    private void seedDimension(String dimension) {
        String actorScope = body(Map.of(dimension, List.of("范围甲", "范围乙")));
        insertUser("scope_actor", ACTOR, "范围管理员", false, actorScope, "company_admin");
        insertTarget("same", actorScope);
        insertTarget("narrower", body(Map.of(dimension, List.of("范围甲"))));
        insertTarget("broader", body(Map.of(dimension, List.of("范围甲", "范围乙", "范围丙"))));
        insertTarget("outside", body(Map.of(dimension, List.of("范围丙"))));
        insertTarget("missing", "{}");
        insertTarget("null", "{\"" + dimension + "\":null}");
        insertTarget("empty", body(Map.of(dimension, List.of())));
    }

    private void insertTarget(String suffix, String scope) {
        insertUser("scope_" + suffix, "scope." + suffix, "测试账号", false, scope, "readonly");
    }

    private static List<String> userIds(Map<String, Object> page) {
        return list(page.get("items")).stream().map(item -> (String) map(item).get("id")).toList();
    }

    private Map<String, Object> persistedUser(String id) {
        return jdbc.queryForMap("select data_scope::text as data_scope, revision from users where id = ?", id);
    }

    private List<String> persistedRoles(String id) {
        return jdbc.queryForList("select role from user_roles where user_id = ? order by role", String.class, id);
    }
}
