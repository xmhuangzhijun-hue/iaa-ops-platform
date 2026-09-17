package com.iaaops.reporting.persistence;

import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.DataScope;
import com.iaaops.reporting.domain.Dimension;
import com.iaaops.shared.metrics.Metric;
import com.iaaops.shared.metrics.MetricRegistry;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 事实表查询。
 *
 * SQL 只做过滤与基础指标求和，派生指标一律交给指标口径共享内核重算。
 * 数据范围在 SQL 条件里强制，不做取回后再过滤。
 */
@Repository
public class FactQueryRepository {

    private static final String SOURCE = """
            from ad_facts f
            left join account_mappings m
              on m.tenant_id = f.tenant_id and m.media = f.media and m.account = f.account
            """;

    private final JdbcTemplate jdbc;

    FactQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一组 SQL 条件与其绑定参数。 */
    public record Conditions(List<String> clauses, List<Object> params) {

        public Conditions and(String clause, Object... extra) {
            List<String> merged = new ArrayList<>(clauses);
            merged.add(clause);
            List<Object> values = new ArrayList<>(params);
            values.addAll(List.of(extra));
            return new Conditions(merged, values);
        }

        public String where() {
            return "where " + String.join(" and ", clauses);
        }

        public Object[] args() {
            return params.toArray();
        }
    }

    public Conditions conditions(CurrentUser user, LocalDate from, LocalDate to, ReportFilterValues filters) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        clauses.add("f.tenant_id = ?");
        params.add(user.tenantId());
        clauses.add("f.stat_date between ? and ?");
        params.add(from);
        params.add(to);

        addIn(clauses, params, Dimension.MEDIA, filters.media());
        addIn(clauses, params, Dimension.ACCOUNT, filters.accounts());
        addIn(clauses, params, Dimension.PRODUCT, filters.products());
        addIn(clauses, params, Dimension.AGENCY, filters.agencies());
        addIn(clauses, params, Dimension.OPERATOR, filters.operators());

        DataScope scope = user.dataScope();
        addScope(clauses, params, Dimension.AGENCY, scope.agencies());
        addScope(clauses, params, Dimension.PRODUCT, scope.products());
        addScope(clauses, params, Dimension.OPERATOR, scope.operators());
        return new Conditions(clauses, params);
    }

    /** 授权空集没有任何可见行；不能复用请求筛选的空集=不筛选语义。 */
    private static void addScope(List<String> clauses, List<Object> params, Dimension dimension,
            List<String> values) {
        if (values == null) {
            return;
        }
        if (values.isEmpty()) {
            clauses.add("false");
            return;
        }
        addIn(clauses, params, dimension, values);
    }

    private static void addIn(List<String> clauses, List<Object> params, Dimension dimension, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        clauses.add(dimension.column() + " in (" + placeholders(values.size()) + ")");
        params.addAll(values);
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    /** 关键词在给定维度上做大小写不敏感匹配，转义 SQL 通配符。 */
    public static Conditions withKeyword(Conditions conditions, List<Dimension> dimensions, String keyword) {
        String escaped = keyword.replace("/", "//").replace("%", "/%").replace("_", "/_");
        String pattern = "%" + escaped + "%";
        List<String> matches = dimensions.stream()
                .map(dimension -> "cast(" + dimension.column() + " as text) ilike ? escape '/'")
                .toList();
        Object[] params = java.util.Collections.nCopies(dimensions.size(), (Object) pattern).toArray();
        return conditions.and("(" + String.join(" or ", matches) + ")", params);
    }

    /** 按维度分组求和；最多取 limit + 1 行，由调用方判断是否超限。 */
    public List<Map<String, Object>> grouped(Conditions conditions, List<Dimension> dimensions, int limit) {
        String selected = dimensions.stream()
                .map(dimension -> dimension.column() + " as " + dimension.key())
                .toList()
                .stream().reduce((a, b) -> a + ", " + b).orElse("");
        String sums = MetricRegistry.baseMetrics().stream()
                .map(metric -> "coalesce(sum(f." + metric.key() + "), 0) as " + metric.key())
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        String groupBy = dimensions.stream()
                .map(Dimension::column)
                .reduce((a, b) -> a + ", " + b)
                .map(value -> "group by " + value)
                .orElse("");
        String sql = "select " + (selected.isEmpty() ? "" : selected + ", ") + sums + "\n"
                + SOURCE + conditions.where() + "\n" + groupBy + "\nlimit " + (limit + 1);
        return jdbc.query(sql, (rs, index) -> readRow(rs, dimensions), conditions.args());
    }

    private static Map<String, Object> readRow(java.sql.ResultSet rs, List<Dimension> dimensions)
            throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Dimension dimension : dimensions) {
            row.put(dimension.key(), readDimension(rs, dimension));
        }
        for (Metric metric : MetricRegistry.baseMetrics()) {
            row.put(metric.key(), rs.getBigDecimal(metric.key()));
        }
        return row;
    }

    private static Object readDimension(java.sql.ResultSet rs, Dimension dimension) throws java.sql.SQLException {
        return switch (dimension) {
            case STAT_DATE -> {
                java.sql.Date value = rs.getDate(dimension.key());
                yield value == null ? null : value.toLocalDate();
            }
            case STAT_HOUR -> {
                int value = rs.getInt(dimension.key());
                yield rs.wasNull() ? null : value;
            }
            default -> rs.getString(dimension.key());
        };
    }

    public OffsetDateTime loadedAt(Conditions conditions) {
        return jdbc.queryForObject("select max(f.loaded_at)\n" + SOURCE + conditions.where(),
                (rs, index) -> rs.getObject(1, OffsetDateTime.class), conditions.args());
    }

    public long count(Conditions conditions) {
        Long value = jdbc.queryForObject("select count(*)\n" + SOURCE + conditions.where(), Long.class,
                conditions.args());
        return value == null ? 0 : value;
    }

    /** 明细行；排序列由调用方从维度与指标枚举中解析，不接受任意字符串。 */
    public List<Map<String, Object>> detail(Conditions conditions, List<Metric> metrics, String orderBy,
            int offset, int limit) {
        String columns = Dimension.RAW_DIMENSIONS.stream()
                .map(dimension -> dimension.column() + " as " + dimension.key())
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        String metricColumns = metrics.stream()
                .map(metric -> "f." + metric.key() + " as " + metric.key())
                .reduce((a, b) -> a + ", " + b)
                .map(value -> ", " + value)
                .orElse("");
        String sql = "select " + columns + metricColumns + "\n" + SOURCE + conditions.where()
                + "\norder by " + orderBy + ", f.id\noffset " + offset + " limit " + limit;
        return jdbc.query(sql, (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Dimension dimension : Dimension.RAW_DIMENSIONS) {
                row.put(dimension.key(), readDimension(rs, dimension));
            }
            for (Metric metric : metrics) {
                row.put(metric.key(), rs.getBigDecimal(metric.key()));
            }
            return row;
        }, conditions.args());
    }

    /** 明细页的合计：整段条件求和，不受分页影响。 */
    public Map<String, Object> sums(Conditions conditions, List<Metric> metrics) {
        String selected = metrics.stream()
                .map(metric -> "coalesce(sum(f." + metric.key() + "), 0) as " + metric.key())
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        return jdbc.queryForObject("select " + selected + "\n" + SOURCE + conditions.where(), (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Metric metric : metrics) {
                row.put(metric.key(), rs.getBigDecimal(metric.key()));
            }
            return row;
        }, conditions.args());
    }

    /** 某个维度在当前条件下的去重取值，按值排序，空值不出现在筛选项里。 */
    public List<String> distinctValues(Conditions conditions, Dimension dimension) {
        String column = dimension.column();
        String sql = "select distinct " + column + " as value\n" + SOURCE
                + conditions.and(column + " is not null").where() + "\norder by value";
        return jdbc.queryForList(sql, String.class, conditions.args());
    }

    /** 请求里的筛选条件，供 conditions 使用（各维度为空表示不限）。 */
    public record ReportFilterValues(List<String> media, List<String> products, List<String> agencies,
            List<String> accounts, List<String> operators) {
    }
}
