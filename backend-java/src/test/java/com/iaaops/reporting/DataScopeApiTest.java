package com.iaaops.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.iaaops.support.PostgresTestBase;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F-01 的永久回归：通过真实鉴权、角色更新和报表接口验证授权范围。
 * null/缺省表示不限，[] 表示无授权；普通查询筛选里的 [] 仍表示不额外筛选。
 * 全部数据只写入 PostgresTestBase 的 Testcontainers 数据库。
 */
class DataScopeApiTest extends PostgresTestBase {

    private static final String ADMIN = "scope.admin";
    private static final String AGENCY = "scope.agency";
    private static final String OTHER_TENANT = "tenant_scope_other";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 15);
    private static final LocalDate TO = LocalDate.of(2026, 9, 16);

    @BeforeEach
    void seed() {
        jdbc.update("delete from ad_facts");
        jdbc.update("delete from account_mappings");
        jdbc.update("delete from import_tasks");
        jdbc.update("delete from audit_events");
        jdbc.update("delete from user_preferences");
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        insertUser("usr_scope_admin", ADMIN, "范围管理员", false, "{}", "company_admin");
        insertUser("usr_scope_agency", AGENCY, "范围代理", false,
                "{\"agencies\":[\"星河代理\"]}", "agency_admin");
        mapping(TENANT, "acc-a", "星河代理", "记账", "运营甲");
        mapping(TENANT, "acc-b", "蓝鲸代理", "天气", "运营乙");
        // acc-c 未映射，只能由三个维度都不限的账号看到。
        fact(TENANT, FROM, 10, "acc-a", "100.00", "150.00");
        fact(TENANT, FROM, 11, "acc-a", "300.00", "150.00");
        fact(TENANT, TO, 10, "acc-b", "200.00", "260.00");
        fact(TENANT, TO, 11, "acc-c", "50.00", "10.00");

        // 另一租户既有同名账户也有独占账户：同时检验事实表过滤与映射 JOIN 的租户边界。
        jdbc.update("insert into tenants (id, name) values (?, ?) on conflict (id) do nothing",
                OTHER_TENANT, "其他虚构租户");
        mapping(OTHER_TENANT, "acc-a", "其他租户代理", "其他租户产品", "其他租户运营");
        mapping(OTHER_TENANT, "other-only", "其他租户代理", "其他租户产品", "其他租户运营");
        fact(OTHER_TENANT, FROM, 10, "acc-a", "9000.00", "9000.00");
        fact(OTHER_TENANT, FROM, 10, "other-only", "1000.00", "1000.00");
    }

    @Test
    void agencySelfUpdateToEmptyScopeRemovesReportingAccess() throws Exception {
        String token = bearer(AGENCY);
        assertAccountsAndCost(report("aggregate", token, groupedQuery(Map.of())), List.of("acc-a"), "400");
        assertThat(optionValues(options(token), "accounts")).containsExactly("acc-a");

        Map<String, Object> users = json(get("/api/v1/users").header("Authorization", token));
        Map<String, Object> self = list(users.get("items")).stream().map(PostgresTestBase::map)
                .filter(row -> "usr_scope_agency".equals(row.get("id"))).findFirst().orElseThrow();
        Map<String, Object> updated = json(put("/api/v1/users/usr_scope_agency/roles")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("roles", List.of("agency_admin"),
                        "data_scope", Map.of("agencies", List.of()), "revision", self.get("revision")))));
        assertThat(list(map(updated.get("data_scope")).get("agencies"))).isEmpty();
        assertThat(jdbc.queryForObject("select data_scope ->> 'agencies' from users where id = ?",
                String.class, "usr_scope_agency")).isEqualTo("[]");

        // 已签发与新签发的令牌都必须读取更新后的授权，不能留一条旧令牌越权路径。
        for (String currentToken : List.of(token, bearer(AGENCY))) {
            for (String view : List.of("aggregate", "daily")) {
                assertEmptyReport(report(view, currentToken, groupedQuery(Map.of())));
                assertThat(csv(currentToken, view, groupedQuery(Map.of())).lines().count()).isEqualTo(1);
            }
            assertEmptyOptions(currentToken);
            assertThat(errorCode(post("/api/v1/reports/aggregate").header("Authorization", currentToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(query(Map.of("metrics", List.of("cost", "revenue"))))), 403))
                    .isEqualTo("FORBIDDEN");
        }
    }

    @ParameterizedTest(name = "{0}=[] denies every report and export")
    @ValueSource(strings = {"agencies", "products", "operators"})
    void eachEmptyAuthorizationDimensionDeniesAllReportingSurfaces(String dimension) throws Exception {
        setAdminScope(Map.of(dimension, List.of()));
        String token = bearer(ADMIN);
        for (String view : List.of("aggregate", "daily", "raw")) {
            Map<String, Object> request = view.equals("raw") ? query(Map.of()) : groupedQuery(Map.of());
            assertEmptyReport(report(view, token, request));
            assertThat(csv(token, view, groupedQuery(Map.of())).lines().count()).isEqualTo(1);
        }
        for (Map<String, Object> extra : List.of(Map.<String, Object>of(), Map.<String, Object>of("split_by", "account"))) {
            Map<String, Object> trend = report("trend", token, query(extra));
            assertThat(list(trend.get("series"))).allSatisfy(series ->
                    assertThat(list(map(series).get("points"))).isEmpty());
            assertThat(trend.get("data_as_of")).isNull();
        }
        Map<String, Object> anomalies = report("roi-anomalies", token, query(Map.of("min_cost", 0)));
        assertThat(list(anomalies.get("items"))).isEmpty();
        assertThat(anomalies.get("data_as_of")).isNull();
        assertEmptyOptions(token);
    }

    @ParameterizedTest(name = "{0} preserves unrestricted access within one tenant")
    @ValueSource(strings = {"{}", "{\"agencies\":null,\"products\":null,\"operators\":null}"})
    void absentAndNullScopeKeepUnmappedRowsButNeverCrossTenant(String scope) throws Exception {
        jdbc.update("update users set data_scope = cast(? as jsonb) where username = ?", scope, ADMIN);
        assertUnrestrictedTenantReporting(bearer(ADMIN), Map.of());
    }

    @Test
    void ordinaryEmptyFiltersDoNotRevokeUnrestrictedAccess() throws Exception {
        Map<String, Object> emptyFilters = Map.of("media", List.of(), "products", List.of(),
                "agencies", List.of(), "accounts", List.of(), "operators", List.of());
        assertUnrestrictedTenantReporting(bearer(ADMIN), emptyFilters);
    }

    @ParameterizedTest(name = "request filters intersect the {0} authorization")
    @CsvSource({"agencies,星河代理,蓝鲸代理", "products,记账,天气", "operators,运营甲,运营乙"})
    void requestFiltersCannotBroadenNonemptyAuthorization(String dimension, String allowed, String denied)
            throws Exception {
        setAdminScope(Map.of(dimension, List.of(allowed)));
        String token = bearer(ADMIN);
        for (List<String> requested : List.of(List.<String>of(), List.of(allowed, denied))) {
            assertAccountsAndCost(report("aggregate", token,
                    groupedQuery(Map.of(dimension, requested))), List.of("acc-a"), "400");
        }
        assertEmptyReport(report("aggregate", token, groupedQuery(Map.of(dimension, List.of(denied)))));
        assertThat(optionValues(options(token), "accounts")).containsExactly("acc-a");
        String exported = csv(token, "aggregate", groupedQuery(Map.of(dimension, List.of(allowed, denied))));
        assertThat(exported).contains("acc-a,400.00").doesNotContain("acc-b", "acc-c", "other-only");
    }

    @Test
    void multipleAuthorizationDimensionsAreAnIntersection() throws Exception {
        setAdminScope(Map.of("agencies", List.of("星河代理"), "products", List.of("天气")));
        String token = bearer(ADMIN);
        assertEmptyReport(report("aggregate", token, groupedQuery(Map.of())));
        assertEmptyOptions(token);
    }

    private void assertUnrestrictedTenantReporting(String token, Map<String, Object> filters) throws Exception {
        assertAccountsAndCost(report("aggregate", token, groupedQuery(filters)),
                List.of("acc-a", "acc-b", "acc-c"), "650");
        Map<String, Object> daily = report("daily", token, groupedQuery(filters));
        assertThat(list(daily.get("rows"))).hasSize(3);
        assertThat(number(map(daily.get("totals")).get("cost"))).isEqualByComparingTo("650");
        Map<String, Object> raw = report("raw", token, query(Map.of("filters", filters)));
        assertThat(raw.get("total_rows")).isEqualTo(4);
        assertThat(number(map(raw.get("totals")).get("cost"))).isEqualByComparingTo("650");

        Map<String, Object> trend = report("trend", token,
                query(Map.of("filters", filters, "metrics", List.of("cost"))));
        List<Object> points = list(map(list(trend.get("series")).getFirst()).get("points"));
        assertThat(points).hasSize(2);
        assertThat(points.stream().map(PostgresTestBase::map).map(point -> map(point.get("values")).get("cost"))
                .map(DataScopeApiTest::number).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("650");
        Map<String, Object> anomalies = report("roi-anomalies", token,
                query(Map.of("filters", filters, "min_cost", 0, "roi_below", 1)));
        assertThat(list(anomalies.get("items")).stream().map(PostgresTestBase::map)
                .map(item -> map(item.get("dimensions")).get("account")))
                .containsExactlyInAnyOrder("acc-a", "acc-c");

        Map<String, Object> options = options(token);
        assertThat(optionValues(options, "accounts")).containsExactly("acc-a", "acc-b", "acc-c");
        assertThat(optionValues(options, "agencies")).containsExactlyInAnyOrder("星河代理", "蓝鲸代理");
        for (String view : List.of("aggregate", "daily", "raw")) {
            String exported = csv(token, view, groupedQuery(filters));
            assertThat(exported).contains("acc-a", "acc-b", "acc-c")
                    .doesNotContain("other-only", "9000.00", "其他租户");
            assertThat(exported.lines().count()).isEqualTo(view.equals("raw") ? 5 : 4);
        }
    }

    private void setAdminScope(Map<String, Object> scope) {
        jdbc.update("update users set data_scope = cast(? as jsonb) where username = ?", body(scope), ADMIN);
    }

    private static void assertEmptyReport(Map<String, Object> result) {
        assertThat(list(result.get("rows"))).isEmpty();
        assertThat(result.get("total_rows")).isEqualTo(0);
        assertThat(number(map(result.get("totals")).get("cost"))).isEqualByComparingTo("0");
        assertThat(result.get("data_as_of")).isNull();
    }

    private static void assertAccountsAndCost(Map<String, Object> result, List<String> accounts, String cost) {
        assertThat(list(result.get("rows")).stream().map(PostgresTestBase::map).map(row -> row.get("account")))
                .containsExactlyElementsOf(accounts);
        assertThat(number(map(result.get("totals")).get("cost"))).isEqualByComparingTo(cost);
    }

    private void assertEmptyOptions(String token) throws Exception {
        Map<String, Object> result = options(token);
        for (String field : List.of("media", "products", "agencies", "accounts", "operators")) {
            assertThat(list(result.get(field))).as("filter options: %s", field).isEmpty();
        }
    }

    private Map<String, Object> options(String token) throws Exception {
        return json(get("/api/v1/filter-options").header("Authorization", token)
                .param("date_from", FROM.toString()).param("date_to", TO.toString()));
    }

    private static List<String> optionValues(Map<String, Object> options, String field) {
        return list(options.get(field)).stream().map(PostgresTestBase::map)
                .map(row -> (String) row.get("value")).toList();
    }

    private Map<String, Object> report(String view, String token, Map<String, Object> request) throws Exception {
        return json(post("/api/v1/reports/" + view).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body(request)));
    }

    private String csv(String token, String view, Map<String, Object> request) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/reports/export").param("view", view)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body(request)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> query(Map<String, Object> extra) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("date_from", FROM.toString());
        request.put("date_to", TO.toString());
        request.putAll(extra);
        return request;
    }

    private static Map<String, Object> groupedQuery(Map<String, Object> filters) {
        return query(Map.of("group_by", List.of("account"), "metrics", List.of("cost"), "filters", filters));
    }

    private static BigDecimal number(Object value) {
        return new BigDecimal(value.toString());
    }

    private void mapping(String tenant, String account, String agency, String product, String operator) {
        jdbc.update("""
                insert into account_mappings (tenant_id, media, account, agency, product, operator, revision)
                values (?, 'vivo', ?, ?, ?, ?, 1)
                """, tenant, account, agency, product, operator);
    }

    private void fact(String tenant, LocalDate date, int hour, String account, String cost, String revenue) {
        jdbc.update("""
                insert into ad_facts (tenant_id, stat_date, stat_hour, media, account, cost, revenue,
                                      impressions, clicks, launches, callbacks, conversions)
                values (?, ?, ?, 'vivo', ?, cast(? as numeric), cast(? as numeric), 1000, 100, 0, 0, 0)
                """, tenant, date, hour, account, cost, revenue);
    }
}
