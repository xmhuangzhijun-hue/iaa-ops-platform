package com.iaaops.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.iaaops.support.PostgresTestBase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * 看盘接口的端到端行为：口径、数据范围、分页排序、导出与各类拒绝路径。
 *
 * 用一小份可手算的数据，断言的是「算得对不对」而不是「跑没跑通」。
 */
class ReportingApiTest extends PostgresTestBase {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 15);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 9, 16);

    @BeforeEach
    void seed() {
        jdbc.update("delete from ad_facts");
        jdbc.update("delete from account_mappings");
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");

        insertUser("usr_admin", "t.admin", "管理员", false, "{}", "company_admin");
        insertUser("usr_agency", "t.agency", "代理", false, "{\"agencies\": [\"星河代理\"]}", "agency_admin");
        insertUser("usr_readonly", "t.readonly", "只读", false, "{}", "readonly");

        mapping("vivo", "acc-a", "星河代理", "记账", "运营甲");
        mapping("vivo", "acc-b", "蓝鲸代理", "天气", "运营乙");
        // acc-c 故意不建映射：未映射数据只对不限范围的账号可见

        // 两天两账户，数字取整方便手算：ROI = 收益 / 消耗，CPC = 消耗 / 点击
        fact(DAY_1, 10, "vivo", "acc-a", "plan-1", "100.00", "150.00", 10_000, 1_000, 500, 100, 200);
        fact(DAY_1, 11, "vivo", "acc-a", "plan-2", "300.00", "150.00", 20_000, 2_000, 900, 180, 300);
        fact(DAY_2, 10, "vivo", "acc-b", "plan-3", "200.00", "260.00", 40_000, 4_000, 0, 0, 0);
        fact(DAY_2, 11, "vivo", "acc-c", "plan-4", "50.00", "10.00", 5_000, 0, 0, 0, 0);
    }

    private void mapping(String media, String account, String agency, String product, String operator) {
        jdbc.update("""
                insert into account_mappings (tenant_id, media, account, agency, product, operator, revision)
                values (?, ?, ?, ?, ?, ?, 1)
                """, TENANT, media, account, agency, product, operator);
    }

    private void fact(LocalDate day, int hour, String media, String account, String campaign, String cost,
            String revenue, long impressions, long clicks, long launches, long callbacks, long conversions) {
        jdbc.update("""
                insert into ad_facts (tenant_id, stat_date, stat_hour, media, account, campaign, cost, revenue,
                                      impressions, clicks, launches, callbacks, conversions, loaded_at)
                values (?, ?, ?, ?, ?, ?, cast(? as numeric), cast(? as numeric), ?, ?, ?, ?, ?, now())
                """, TENANT, day, hour, media, account, campaign, cost, revenue, impressions, clicks, launches,
                callbacks, conversions);
    }

    private RequestBuilder report(String path, String username, Map<String, Object> query) throws Exception {
        return post("/api/v1" + path)
                .header("Authorization", bearer(username))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(query));
    }

    private static Map<String, Object> query(Map<String, Object> extra) {
        java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>();
        merged.put("date_from", DAY_1.toString());
        merged.put("date_to", DAY_2.toString());
        merged.putAll(extra);
        return merged;
    }

    @Test
    void 派生指标由汇总后的基础指标重算() throws Exception {
        Map<String, Object> result = json(report("/reports/aggregate", "t.admin",
                query(Map.of("group_by", List.of("account")))));

        Map<String, Object> first = map(list(result.get("rows")).getFirst());
        // acc-a 两行合并：消耗 400、收益 300 → ROI 0.75，而不是两行 ROI(1.5 / 0.5) 的平均 1.0
        assertThat(first.get("account")).isEqualTo("acc-a");
        assertThat(number(first.get("cost"))).isEqualTo(400.0);
        assertThat(number(first.get("roi"))).isEqualTo(0.75);
        // CPC 按指标精度输出 4 位小数：400 / 3000 = 0.1333
        assertThat(number(first.get("cpc"))).isEqualTo(0.1333);

        // 合计同理：总消耗 650、总收益 570
        Map<String, Object> totals = map(result.get("totals"));
        assertThat(number(totals.get("cost"))).isEqualTo(650.0);
        assertThat(number(totals.get("revenue"))).isEqualTo(570.0);
        assertThat(number(totals.get("roi"))).isEqualTo(0.877);
    }

    @Test
    void 分母为零的派生指标留空而不是零() throws Exception {
        Map<String, Object> result = json(report("/reports/aggregate", "t.admin",
                query(Map.of("group_by", List.of("account"), "metrics", List.of("cost", "clicks", "cpc", "cvr")))));
        Map<String, Object> noClicks = list(result.get("rows")).stream()
                .map(PostgresTestBase::map)
                .filter(row -> "acc-c".equals(row.get("account")))
                .findFirst()
                .orElseThrow();
        assertThat(noClicks.get("clicks")).isEqualTo(0);
        assertThat(noClicks.get("cpc")).isNull();
        assertThat(noClicks.get("cvr")).isNull();
    }

    @Test
    void 数据范围限制在代理自己的数据上() throws Exception {
        Map<String, Object> result = json(report("/reports/aggregate", "t.agency",
                query(Map.of("group_by", List.of("account")))));
        assertThat(list(result.get("rows")).stream().map(PostgresTestBase::map).map(row -> row.get("account")))
                .containsExactly("acc-a");
        // 未映射的 acc-c 不出现在受限账号的结果里
        assertThat(number(map(result.get("totals")).get("cost"))).isEqualTo(400.0);
    }

    @Test
    void 对外口径看不到收益类指标也不能点名索取() throws Exception {
        Map<String, Object> result = json(report("/reports/aggregate", "t.agency",
                query(Map.of("group_by", List.of("account")))));
        assertThat(strings(list(result.get("columns")).stream().map(PostgresTestBase::map)
                .map(column -> (String) column.get("key")).toList()))
                .doesNotContain("revenue", "roi", "click_arpu")
                .contains("cost", "clicks");

        assertThat(errorCode(report("/reports/aggregate", "t.agency",
                query(Map.of("group_by", List.of("account"), "metrics", List.of("cost", "revenue")))), 403))
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void 只读账号不能看趋势与原始明细() throws Exception {
        assertThat(errorCode(report("/reports/trend", "t.readonly", query(Map.of())), 403)).isEqualTo("FORBIDDEN");
        assertThat(errorCode(report("/reports/raw", "t.readonly", query(Map.of())), 403)).isEqualTo("FORBIDDEN");
    }

    @Test
    void 分天在分组前固定加入日期维度() throws Exception {
        Map<String, Object> result = json(report("/reports/daily", "t.admin",
                query(Map.of("group_by", List.of("media")))));
        List<String> keys = list(result.get("columns")).stream().map(PostgresTestBase::map)
                .map(column -> (String) column.get("key")).toList();
        assertThat(keys.subList(0, 2)).containsExactly("stat_date", "media");
        // 默认按日期倒序
        assertThat(map(list(result.get("rows")).getFirst()).get("stat_date")).isEqualTo(DAY_2.toString());
    }

    @Test
    void 趋势按小时输出并限制跨度() throws Exception {
        Map<String, Object> result = json(report("/reports/trend", "t.admin",
                query(Map.of("granularity", "hour", "metrics", List.of("cost")))));
        Map<String, Object> series = map(list(result.get("series")).getFirst());
        assertThat(series.get("key")).isEqualTo("全部");
        assertThat(map(list(series.get("points")).getFirst()).get("bucket")).isEqualTo(DAY_1 + " 10:00");

        Map<String, Object> tooWide = query(Map.of("granularity", "hour", "metrics", List.of("cost")));
        tooWide.put("date_from", DAY_1.minusDays(30).toString());
        assertThat(errorCode(report("/reports/trend", "t.admin", tooWide), 422)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void 趋势按维度拆线且未映射单列() throws Exception {
        Map<String, Object> result = json(report("/reports/trend", "t.admin",
                query(Map.of("metrics", List.of("cost"), "split_by", "product"))));
        List<String> labels = list(result.get("series")).stream().map(PostgresTestBase::map)
                .map(series -> (String) series.get("key")).toList();
        // 按消耗排序：记账 400 > 天气 200 > 未映射 50
        assertThat(labels).containsExactly("记账", "天气", "未映射");
    }

    @Test
    void ROI异常按阈值筛选并回显规则() throws Exception {
        Map<String, Object> result = json(report("/reports/roi-anomalies", "t.admin",
                query(Map.of("group_by", List.of("account"), "roi_below", 1.0, "min_cost", 100))));
        assertThat(result.get("rule")).isEqualTo("ROI < 100% 且消耗 ≥ 100");

        List<Map<String, Object>> items = list(result.get("items")).stream().map(PostgresTestBase::map).toList();
        // acc-a：ROI 0.75、消耗 400 命中；acc-b：ROI 1.3 不命中；acc-c 消耗 50 未达门槛
        assertThat(items).hasSize(1);
        assertThat(map(items.getFirst().get("dimensions")).get("account")).isEqualTo("acc-a");
        assertThat(items.getFirst().get("reason")).isEqualTo("ROI 75.0% 低于 100%");
    }

    @Test
    void 原始明细按页返回且合计不受分页影响() throws Exception {
        Map<String, Object> page = json(report("/reports/raw", "t.admin",
                query(Map.of("page", 1, "page_size", 2))));
        assertThat(list(page.get("rows"))).hasSize(2);
        assertThat(page.get("total_rows")).isEqualTo(4);
        assertThat(number(map(page.get("totals")).get("cost"))).isEqualTo(650.0);

        Map<String, Object> second = json(report("/reports/raw", "t.admin",
                query(Map.of("page", 2, "page_size", 2))));
        assertThat(list(second.get("rows"))).hasSize(2);
        assertThat(second.get("page")).isEqualTo(2);
    }

    @Test
    void 排序字段必须在结果列中() throws Exception {
        assertThat(errorCode(report("/reports/aggregate", "t.admin",
                query(Map.of("group_by", List.of("product"), "metrics", List.of("cost"),
                        "sort", List.of(Map.of("field", "launches"))))), 422))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void 日期区间不成立时拒绝() throws Exception {
        Map<String, Object> reversed = query(Map.of("group_by", List.of("product")));
        reversed.put("date_from", DAY_2.toString());
        reversed.put("date_to", DAY_1.toString());
        assertThat(errorCode(report("/reports/aggregate", "t.admin", reversed), 422)).isEqualTo("VALIDATION_FAILED");

        Map<String, Object> tooWide = query(Map.of("group_by", List.of("product")));
        tooWide.put("date_from", DAY_2.minusDays(120).toString());
        assertThat(errorCode(report("/reports/aggregate", "t.admin", tooWide), 422)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void 筛选可选值只给账号可见范围内的取值() throws Exception {
        Map<String, Object> all = json(get("/api/v1/filter-options")
                .header("Authorization", bearer("t.admin"))
                .param("date_from", DAY_1.toString())
                .param("date_to", DAY_2.toString()));
        assertThat(list(all.get("accounts")).stream().map(PostgresTestBase::map)
                .map(option -> option.get("value"))).containsExactly("acc-a", "acc-b", "acc-c");

        Map<String, Object> agency = json(get("/api/v1/filter-options")
                .header("Authorization", bearer("t.agency"))
                .param("date_from", DAY_1.toString())
                .param("date_to", DAY_2.toString()));
        assertThat(list(agency.get("accounts")).stream().map(PostgresTestBase::map)
                .map(option -> option.get("value"))).containsExactly("acc-a");
    }

    @Test
    void 导出的CSV带BOM且与页面同一口径() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/reports/export")
                .header("Authorization", bearer("t.admin"))
                .param("view", "aggregate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(query(Map.of("group_by", List.of("account"), "metrics", List.of("cost", "roi"))))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename*=UTF-8''iaa-aggregate-%s_%s.csv".formatted(DAY_1, DAY_2));
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿账户,消耗,ROI\n");
        assertThat(csv).contains("acc-a,400.00,0.750");
    }

    @Test
    void 对外口径不能导出原始明细() throws Exception {
        assertThat(errorCode(post("/api/v1/reports/export")
                .header("Authorization", bearer("t.agency"))
                .param("view", "raw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(query(Map.of()))), 403))
                .isEqualTo("FORBIDDEN");
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }
}
