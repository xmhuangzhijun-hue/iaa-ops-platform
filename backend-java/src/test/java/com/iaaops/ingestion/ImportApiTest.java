package com.iaaops.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.iaaops.support.PostgresTestBase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 导入的端到端行为：按天整体替换、重复导入不累加、坏行只跳过坏行、权限与文件校验。
 *
 * 处理是异步的，断言前统一等任务跑完。
 */
class ImportApiTest extends PostgresTestBase {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 15);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 9, 16);
    private static final String[] HEADER = {"日期", "小时", "账户", "推广计划", "消耗", "预估收益", "曝光", "点击"};

    @BeforeEach
    void seed() {
        jdbc.update("delete from ad_facts");
        jdbc.update("delete from import_tasks");
        jdbc.update("delete from audit_events");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        insertUser("usr_company", "i.company", "公司管理员", false, "{}", "company_admin");
        insertUser("usr_operator", "i.operator", "运营", false, "{}", "operator");
    }

    @Test
    void 上传后按天整体替换且重复导入不累加() throws Exception {
        // 先放一条同一天的旧数据，导入后应被整天替换掉
        jdbc.update("""
                insert into ad_facts (tenant_id, stat_date, stat_hour, media, account, campaign, cost, revenue,
                                      impressions, clicks, launches, callbacks, conversions)
                values (?, ?, 9, 'vivo', 'old-account', 'old-plan', 999, 999, 1, 1, 0, 0, 0)
                """, TENANT, DAY_1);

        Map<String, Object> task = upload("i.company", "vivo", workbook(
                row(DAY_1, "10", "acc-a", "plan-1", "100.00", "150.00", "10000", "1000"),
                row(DAY_1, "11", "acc-a", "plan-2", "300.00", "150.00", "20000", "2000"),
                row(DAY_2, "10", "acc-b", "plan-3", "200.00", "260.00", "40000", "4000")));

        assertThat(task.get("status")).isEqualTo("succeeded");
        assertThat(task.get("rows_total")).isEqualTo(3);
        assertThat(task.get("rows_replaced")).isEqualTo(1);
        assertThat(strings(task.get("stat_dates"))).containsExactly(DAY_1.toString(), DAY_2.toString());
        assertThat(list(task.get("errors"))).isEmpty();
        // 两个时间戳必须来自同一个钟，否则会出现"完成早于创建"
        assertThat(java.time.OffsetDateTime.parse((String) task.get("finished_at")))
                .isAfterOrEqualTo(java.time.OffsetDateTime.parse((String) task.get("created_at")));

        assertThat(facts()).isEqualTo(3);
        assertThat(cost()).isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject("select count(*) from ad_facts where account = 'old-account'", Integer.class))
                .isZero();

        // 同一份文件再导一次：行数与金额都不变
        Map<String, Object> again = upload("i.company", "vivo", workbook(
                row(DAY_1, "10", "acc-a", "plan-1", "100.00", "150.00", "10000", "1000"),
                row(DAY_1, "11", "acc-a", "plan-2", "300.00", "150.00", "20000", "2000"),
                row(DAY_2, "10", "acc-b", "plan-3", "200.00", "260.00", "40000", "4000")));
        assertThat(again.get("rows_replaced")).isEqualTo(3);
        assertThat(facts()).isEqualTo(3);
        assertThat(cost()).isEqualByComparingTo("600.00");
    }

    @Test
    void 只替换文件覆盖到的那几天() throws Exception {
        upload("i.company", "vivo", workbook(row(DAY_1, "10", "acc-a", "plan-1", "100.00", "0", "1", "1")));
        upload("i.company", "vivo", workbook(row(DAY_2, "10", "acc-b", "plan-2", "200.00", "0", "1", "1")));
        // 第二次只覆盖 DAY_2，DAY_1 的数据要还在
        assertThat(facts()).isEqualTo(2);
        assertThat(cost()).isEqualByComparingTo("300.00");
    }

    @Test
    void 只替换同一个媒体的数据() throws Exception {
        upload("i.company", "vivo", workbook(row(DAY_1, "10", "acc-a", "plan-1", "100.00", "0", "1", "1")));
        upload("i.company", "oppo", workbook(row(DAY_1, "10", "acc-b", "plan-2", "200.00", "0", "1", "1")));
        assertThat(facts()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from ad_facts where media = 'vivo'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 坏行只跳过坏行并记下行号与列名() throws Exception {
        Map<String, Object> task = upload("i.company", "vivo", workbook(
                row(DAY_1, "10", "acc-a", "plan-1", "100.00", "0", "1", "1"),
                row(DAY_1, "10", "", "plan-2", "100.00", "0", "1", "1"),
                row(DAY_1, "99", "acc-c", "plan-3", "100.00", "0", "1", "1"),
                row(DAY_1, "10", "acc-d", "plan-4", "不是数字", "0", "1", "1")));

        assertThat(task.get("status")).isEqualTo("succeeded");
        assertThat(task.get("rows_total")).isEqualTo(1);
        List<Map<String, Object>> errors = list(task.get("errors")).stream().map(PostgresTestBase::map).toList();
        assertThat(errors).hasSize(3);
        assertThat(errors.getFirst().get("row")).isEqualTo(3);
        assertThat(errors.getFirst().get("column")).isEqualTo("账户");
        assertThat(errors.stream().map(error -> error.get("column")))
                .containsExactly("账户", "小时", "消耗");
        assertThat(facts()).isEqualTo(1);
    }

    @Test
    void 缺必需列的文件整份判失败且不动数据() throws Exception {
        jdbc.update("""
                insert into ad_facts (tenant_id, stat_date, stat_hour, media, account, campaign, cost, revenue,
                                      impressions, clicks, launches, callbacks, conversions)
                values (?, ?, 9, 'vivo', 'keep-me', 'plan', 1, 1, 1, 1, 0, 0, 0)
                """, TENANT, DAY_1);

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("明细");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("账户");
        header.createCell(1).setCellValue("消耗");
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue("acc-a");
        data.createCell(1).setCellValue("100");

        Map<String, Object> task = upload("i.company", "vivo", bytes(workbook));
        assertThat(task.get("status")).isEqualTo("failed");
        assertThat(map(list(task.get("errors")).getFirst()).get("message").toString()).contains("日期");
        // 失败的导入不能动既有数据
        assertThat(facts()).isEqualTo(1);
    }

    @Test
    void 不是xlsx的文件按失败处理() throws Exception {
        Map<String, Object> task = upload("i.company", "vivo", "这不是一个 Excel 文件".getBytes());
        assertThat(task.get("status")).isEqualTo("failed");
        assertThat(map(list(task.get("errors")).getFirst()).get("message").toString()).contains("xlsx");
    }

    @Test
    void 没有导入权限的账号不能上传也不能查任务() throws Exception {
        MvcResult denied = mvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "x.xlsx", null, workbook(
                        row(DAY_1, "10", "acc-a", "plan-1", "1", "0", "1", "1"))))
                .param("media", "vivo")
                .header("Authorization", bearer("i.operator")))
                .andReturn();
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from import_tasks", Integer.class)).isZero();

        assertThat(errorCode(get("/api/v1/imports/imp_whatever")
                .header("Authorization", bearer("i.operator")), 403)).isEqualTo("FORBIDDEN");
    }

    @Test
    void 查不存在的任务返回404() throws Exception {
        assertThat(errorCode(get("/api/v1/imports/imp_missing")
                .header("Authorization", bearer("i.company")), 404)).isEqualTo("NOT_FOUND");
    }

    @Test
    void 空文件在受理阶段就被拒() throws Exception {
        MvcResult result = mvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "empty.xlsx", null, new byte[0]))
                .param("media", "vivo")
                .header("Authorization", bearer("i.company")))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void 受理即留痕() throws Exception {
        Map<String, Object> task = upload("i.company", "vivo",
                workbook(row(DAY_1, "10", "acc-a", "plan-1", "100.00", "0", "1", "1")));
        Map<String, Object> audit = json(get("/api/v1/audit-events")
                .param("action", "import.create")
                .header("Authorization", bearer("i.company")));
        Map<String, Object> event = map(list(audit.get("items")).getFirst());
        assertThat(event.get("actor")).isEqualTo("i.company");
        assertThat(event.get("target_id")).isEqualTo(task.get("id"));
        assertThat(map(event.get("detail")).get("media")).isEqualTo("vivo");
    }

    // ---------- 工具 ----------

    /** 上传并等任务跑完（处理是异步的）。 */
    private Map<String, Object> upload(String username, String media, byte[] content) throws Exception {
        String auth = bearer(username);
        MvcResult accepted = mvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "media-export.xlsx", null, content))
                .param("media", media)
                .header("Authorization", auth))
                .andReturn();
        assertThat(accepted.getResponse().getStatus()).isEqualTo(202);
        Map<String, Object> task = objectMapper.readValue(
                accepted.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
        return await((String) task.get("id"), auth);
    }

    private Map<String, Object> await(String taskId, String auth) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            Map<String, Object> task = json(get("/api/v1/imports/" + taskId).header("Authorization", auth));
            if (List.of("succeeded", "failed").contains(task.get("status"))) {
                return task;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("导入任务 5 秒内没有结束：" + taskId);
    }

    private int facts() {
        return jdbc.queryForObject("select count(*) from ad_facts", Integer.class);
    }

    private BigDecimal cost() {
        return jdbc.queryForObject("select coalesce(sum(cost), 0) from ad_facts", BigDecimal.class);
    }

    private static String[] row(LocalDate date, String hour, String account, String campaign, String cost,
            String revenue, String impressions, String clicks) {
        return new String[] {date.toString(), hour, account, campaign, cost, revenue, impressions, clicks};
    }

    private static byte[] workbook(String[]... rows) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("明细");
        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADER.length; index++) {
            header.createCell(index).setCellValue(HEADER[index]);
        }
        for (int index = 0; index < rows.length; index++) {
            Row row = sheet.createRow(index + 1);
            for (int column = 0; column < rows[index].length; column++) {
                row.createCell(column).setCellValue(rows[index][column]);
            }
        }
        return bytes(workbook);
    }

    private static byte[] bytes(XSSFWorkbook workbook) throws IOException {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
