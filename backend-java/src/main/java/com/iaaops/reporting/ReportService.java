package com.iaaops.reporting;

import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.Permissions;
import com.iaaops.reporting.ReportDtos.Column;
import com.iaaops.reporting.ReportDtos.FilterOptions;
import com.iaaops.reporting.ReportDtos.Filters;
import com.iaaops.reporting.ReportDtos.Option;
import com.iaaops.reporting.ReportDtos.RawDetailQuery;
import com.iaaops.reporting.ReportDtos.ReportQuery;
import com.iaaops.reporting.ReportDtos.ReportResult;
import com.iaaops.reporting.ReportDtos.RoiAnomaly;
import com.iaaops.reporting.ReportDtos.RoiAnomalyQuery;
import com.iaaops.reporting.ReportDtos.RoiAnomalyResult;
import com.iaaops.reporting.ReportDtos.SortSpec;
import com.iaaops.reporting.ReportDtos.TrendPoint;
import com.iaaops.reporting.ReportDtos.TrendQuery;
import com.iaaops.reporting.ReportDtos.TrendResult;
import com.iaaops.reporting.ReportDtos.TrendSeries;
import com.iaaops.reporting.domain.Dimension;
import com.iaaops.reporting.domain.Summaries;
import com.iaaops.reporting.persistence.FactQueryRepository;
import com.iaaops.reporting.persistence.FactQueryRepository.Conditions;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import com.iaaops.shared.metrics.Metric;
import com.iaaops.shared.metrics.MetricRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看盘查询。
 *
 * 与既有实现同一套口径：SQL 只过滤与求和，派生指标由共享内核用汇总值重算，
 * 合计行、导出与页面走同一条路径，不另写一套算法。
 */
@Service
public class ReportService {

    public static final int MAX_GROUPS = 20_000;
    public static final int MAX_EXPORT_ROWS = 100_000;
    private static final int MAX_HOURLY_DAYS = 7;
    private static final int TOP_SERIES = 8;
    private static final String OTHER_LABEL = "其他";
    private static final String ALL_LABEL = "全部";
    private static final List<String> DEFAULT_METRICS =
            List.of("cost", "revenue", "roi", "clicks", "cpc", "click_arpu", "ctr", "cvr");

    private final FactQueryRepository facts;

    ReportService(FactQueryRepository facts) {
        this.facts = facts;
    }

    // ---------- 筛选可选值 ----------

    @Transactional(readOnly = true)
    public FilterOptions filterOptions(CurrentUser user, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "date_to 不能早于 date_from");
        }
        Conditions conditions = conditions(user, from, to, Filters.EMPTY);
        return new FilterOptions(
                options(conditions, Dimension.MEDIA),
                options(conditions, Dimension.PRODUCT),
                options(conditions, Dimension.AGENCY),
                options(conditions, Dimension.ACCOUNT),
                options(conditions, Dimension.OPERATOR));
    }

    private List<Option> options(Conditions conditions, Dimension dimension) {
        return facts.distinctValues(conditions, dimension).stream()
                .map(value -> new Option(value, dimension == Dimension.MEDIA
                        ? Dimension.MEDIA_LABELS.getOrDefault(value, value)
                        : value))
                .toList();
    }

    // ---------- 聚合与分天 ----------

    @Transactional(readOnly = true)
    public ReportResult aggregate(CurrentUser user, ReportQuery query) {
        return aggregate(user, query, List.of(), List.of(SortSpec.desc("cost")), null);
    }

    @Transactional(readOnly = true)
    public ReportResult daily(CurrentUser user, ReportQuery query) {
        return aggregate(user, query, List.of(Dimension.STAT_DATE),
                List.of(SortSpec.desc("stat_date"), SortSpec.desc("cost")), null);
    }

    private ReportResult aggregate(CurrentUser user, ReportQuery query, List<Dimension> leading,
            List<SortSpec> defaultSort, Integer limit) {
        checkRange(query.dateFrom(), query.dateTo());
        List<Dimension> dimensions = dimensions(leading, query.groupBy());
        List<Metric> metrics = resolveMetrics(user, query.metrics());

        Conditions base = conditions(user, query.dateFrom(), query.dateTo(), query.filters());
        Conditions conditions = query.keyword() == null || query.keyword().isEmpty()
                ? base
                : FactQueryRepository.withKeyword(base, dimensions, query.keyword());

        List<Map<String, Object>> grouped = groupedRows(conditions, dimensions);
        List<Summaries.Row> items = sort(
                Summaries.summarize(grouped, dimensions),
                query.sort().isEmpty() ? defaultSort : query.sort(),
                allowedSortFields(dimensions, metrics));
        Summaries.Row totals = Summaries.summarize(grouped, List.of()).getFirst();

        int page = limit == null ? query.page() : 1;
        int pageSize = limit == null ? query.pageSize() : limit;
        int start = Math.min((page - 1) * pageSize, items.size());
        int end = Math.min(start + pageSize, items.size());

        return new ReportResult(
                columns(dimensions, metrics),
                items.subList(start, end).stream().map(row -> row(row, dimensions, metrics)).toList(),
                row(totals, List.of(), metrics),
                page, pageSize, items.size(), facts.loadedAt(base));
    }

    // ---------- 趋势 ----------

    @Transactional(readOnly = true)
    public TrendResult trend(CurrentUser user, TrendQuery query) {
        checkRange(query.dateFrom(), query.dateTo());
        List<Metric> metrics = resolveMetrics(user, query.metrics());
        boolean hourly = query.hourly();
        if (hourly && java.time.temporal.ChronoUnit.DAYS.between(query.dateFrom(), query.dateTo()) >= MAX_HOURLY_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "按小时查看的跨度不超过 " + MAX_HOURLY_DAYS + " 天");
        }
        List<Dimension> buckets = hourly
                ? List.of(Dimension.STAT_DATE, Dimension.STAT_HOUR)
                : List.of(Dimension.STAT_DATE);
        Conditions conditions = conditions(user, query.dateFrom(), query.dateTo(), query.filters());
        if (hourly) {
            conditions = conditions.and("f.stat_hour is not null");
        }
        Dimension split = query.splitBy() == null ? null : dimension(query.splitBy());
        List<Dimension> grouping = new ArrayList<>(buckets);
        if (split != null) {
            grouping.add(split);
        }
        List<Map<String, Object>> rows = groupedRows(conditions, grouping);

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        if (split != null) {
            List<Summaries.Row> ranking = new ArrayList<>(Summaries.summarize(rows, List.of(split)));
            ranking.sort(Comparator.comparing((Summaries.Row row) -> row.value(Metric.COST)).reversed());
            List<String> top = ranking.stream()
                    .limit(TOP_SERIES)
                    .map(row -> Summaries.label(row.dimension(split.key())))
                    .toList();
            top.forEach(label -> grouped.put(label, new ArrayList<>()));
            for (Map<String, Object> row : rows) {
                String label = Summaries.label(row.get(split.key()));
                grouped.computeIfAbsent(top.contains(label) ? label : OTHER_LABEL, ignored -> new ArrayList<>())
                        .add(row);
            }
        } else {
            grouped.put(ALL_LABEL, rows);
        }

        List<TrendSeries> series = grouped.entrySet().stream()
                .map(entry -> new TrendSeries(entry.getKey(), entry.getKey(),
                        points(entry.getValue(), buckets, metrics, hourly)))
                .toList();
        return new TrendResult(metrics.stream().map(Metric::key).toList(), series, facts.loadedAt(conditions));
    }

    private List<TrendPoint> points(List<Map<String, Object>> rows, List<Dimension> buckets, List<Metric> metrics,
            boolean hourly) {
        List<Summaries.Row> summarized = new ArrayList<>(Summaries.summarize(rows, buckets));
        summarized.sort(Comparator
                .comparing((Summaries.Row row) -> (LocalDate) row.dimension(Dimension.STAT_DATE.key()))
                .thenComparing(row -> hour(row)));
        return summarized.stream()
                .map(row -> new TrendPoint(bucket(row, hourly), values(row, metrics)))
                .toList();
    }

    private static int hour(Summaries.Row row) {
        Object value = row.dimension(Dimension.STAT_HOUR.key());
        return value == null ? 0 : (Integer) value;
    }

    private static String bucket(Summaries.Row row, boolean hourly) {
        String day = row.dimension(Dimension.STAT_DATE.key()).toString();
        return hourly ? "%s %02d:00".formatted(day, hour(row)) : day;
    }

    private static Map<String, Object> values(Summaries.Row row, List<Metric> metrics) {
        Map<String, Object> values = new LinkedHashMap<>();
        metrics.forEach(metric -> values.put(metric.key(), MetricRegistry.present(metric, row.value(metric))));
        return values;
    }

    // ---------- ROI 异常 ----------

    @Transactional(readOnly = true)
    public RoiAnomalyResult roiAnomalies(CurrentUser user, RoiAnomalyQuery query) {
        checkRange(query.dateFrom(), query.dateTo());
        Conditions conditions = conditions(user, query.dateFrom(), query.dateTo(), query.filters());
        List<Dimension> dimensions = query.groupBy().stream().map(ReportService::dimension).toList();
        List<Summaries.Row> matched = new ArrayList<>(Summaries.summarize(groupedRows(conditions, dimensions),
                dimensions).stream()
                .filter(row -> row.value(Metric.COST).compareTo(query.minCost()) >= 0)
                .filter(row -> row.value(Metric.ROI) != null)
                .filter(row -> row.value(Metric.ROI).compareTo(query.roiBelow()) < 0)
                .toList());
        matched.sort(Comparator.comparing((Summaries.Row row) -> row.value(Metric.COST)).reversed());

        List<RoiAnomaly> items = matched.stream().map(row -> {
            Map<String, String> values = new LinkedHashMap<>();
            dimensions.forEach(dimension ->
                    values.put(dimension.key(), Summaries.label(row.dimension(dimension.key()))));
            return new RoiAnomaly(values,
                    MetricRegistry.present(Metric.COST, row.value(Metric.COST)),
                    MetricRegistry.present(Metric.REVENUE, row.value(Metric.REVENUE)),
                    MetricRegistry.present(Metric.ROI, row.value(Metric.ROI)),
                    "ROI %s 低于 %s".formatted(Numbers.percent(row.value(Metric.ROI), 1),
                            Numbers.percent(query.roiBelow(), 0)));
        }).toList();

        String rule = "ROI < %s 且消耗 ≥ %s".formatted(Numbers.percent(query.roiBelow(), 0),
                Numbers.general(query.minCost()));
        return new RoiAnomalyResult(rule, items, facts.loadedAt(conditions));
    }

    // ---------- 原始明细 ----------

    @Transactional(readOnly = true)
    public ReportResult rawDetail(CurrentUser user, RawDetailQuery query) {
        return rawDetail(user, query, null);
    }

    private ReportResult rawDetail(CurrentUser user, RawDetailQuery query, Integer limit) {
        checkRange(query.dateFrom(), query.dateTo());
        List<Metric> metrics = MetricRegistry.baseMetrics().stream()
                .filter(metric -> user.seesRealMetrics() || !metric.revenueSide())
                .toList();
        Conditions base = conditions(user, query.dateFrom(), query.dateTo(), query.filters());
        Conditions conditions = query.keyword() == null || query.keyword().isEmpty()
                ? base
                : FactQueryRepository.withKeyword(base,
                        List.of(Dimension.ACCOUNT, Dimension.CAMPAIGN, Dimension.PRODUCT), query.keyword());

        long totalRows = facts.count(conditions);
        if (limit != null && totalRows > limit) {
            throw new ApiException(ErrorCode.RESULT_TOO_LARGE, "导出行数过多", "超过 " + limit + " 行，请缩小范围");
        }

        List<SortSpec> specs = query.sort().isEmpty()
                ? List.of(SortSpec.desc("stat_date"), SortSpec.desc("stat_hour"), SortSpec.desc("cost"))
                : query.sort();
        String orderBy = orderBy(specs, metrics);

        int page = limit == null ? query.page() : 1;
        int pageSize = limit == null ? query.pageSize() : limit;
        List<Map<String, Object>> rows = facts.detail(conditions, metrics, orderBy, (page - 1) * pageSize, pageSize);
        Map<String, Object> sums = facts.sums(conditions, metrics);

        Map<String, Object> totals = new LinkedHashMap<>();
        metrics.forEach(metric -> totals.put(metric.key(),
                MetricRegistry.present(metric, (BigDecimal) sums.get(metric.key()))));
        return new ReportResult(
                columns(Dimension.RAW_DIMENSIONS, metrics),
                rows.stream().map(row -> detailRow(row, metrics)).toList(),
                totals, page, pageSize, totalRows, facts.loadedAt(base));
    }

    /** 排序列只能来自维度枚举与本次可见的指标，其余一律拒绝。 */
    private static String orderBy(List<SortSpec> specs, List<Metric> metrics) {
        Map<String, String> sortable = new LinkedHashMap<>();
        Dimension.RAW_DIMENSIONS.forEach(dimension -> sortable.put(dimension.key(), dimension.column()));
        metrics.forEach(metric -> sortable.put(metric.key(), "f." + metric.key()));
        List<String> parts = new ArrayList<>();
        for (SortSpec spec : specs) {
            String column = sortable.get(spec.field());
            if (column == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "排序字段不在结果列中", spec.field());
            }
            parts.add(column + (spec.descending() ? " desc" : " asc") + " nulls last");
        }
        return String.join(", ", parts);
    }

    private static Map<String, Object> detailRow(Map<String, Object> row, List<Metric> metrics) {
        Map<String, Object> presented = new LinkedHashMap<>();
        Dimension.RAW_DIMENSIONS.forEach(dimension -> presented.put(dimension.key(),
                dimensionValue(row.get(dimension.key()))));
        metrics.forEach(metric -> presented.put(metric.key(),
                MetricRegistry.present(metric, (BigDecimal) row.get(metric.key()))));
        return presented;
    }

    // ---------- 导出 ----------

    /** 导出内容与文件名；CSV 带 BOM，Excel 打开中文不乱码。 */
    public record Csv(String content, String filename) {
    }

    @Transactional(readOnly = true)
    public Csv exportCsv(CurrentUser user, String view, ReportQuery query) {
        ReportResult result = switch (view) {
            case "raw" -> {
                if (!Permissions.has(user.permissions(), Permissions.METRICS_REAL)) {
                    throw new ApiException(ErrorCode.FORBIDDEN, "没有导出原始明细的权限");
                }
                yield rawDetail(user, new RawDetailQuery(query.dateFrom(), query.dateTo(), query.filters(),
                        query.keyword(), query.sort(), 1, 100), MAX_EXPORT_ROWS);
            }
            case "daily" -> aggregate(user, query, List.of(Dimension.STAT_DATE),
                    List.of(SortSpec.desc("stat_date"), SortSpec.desc("cost")), MAX_EXPORT_ROWS);
            default -> aggregate(user, query, List.of(), List.of(SortSpec.desc("cost")), MAX_EXPORT_ROWS);
        };

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", result.columns().stream().map(column -> csvField(column.label())).toList()))
                .append('\n');
        for (Map<String, Object> row : result.rows()) {
            csv.append(String.join(",", result.columns().stream()
                    .map(column -> csvField(csvValue(column, row.get(column.key()))))
                    .toList())).append('\n');
        }
        String filename = "iaa-%s-%s_%s.csv".formatted(view, query.dateFrom(), query.dateTo());
        return new Csv('﻿' + csv.toString(), filename);
    }

    private static String csvValue(Column column, Object value) {
        if (value == null) {
            return "";
        }
        if ("dimension".equals(column.kind()) || column.precision() == null) {
            return String.valueOf(value);
        }
        return ((BigDecimal) value).setScale(column.precision(), java.math.RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String csvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    // ---------- 公共部分 ----------

    private Conditions conditions(CurrentUser user, LocalDate from, LocalDate to, Filters filters) {
        return facts.conditions(user, from, to, new FactQueryRepository.ReportFilterValues(
                filters.media(), filters.products(), filters.agencies(), filters.accounts(), filters.operators()));
    }

    private List<Map<String, Object>> groupedRows(Conditions conditions, List<Dimension> dimensions) {
        List<Map<String, Object>> rows = facts.grouped(conditions, dimensions, MAX_GROUPS);
        if (rows.size() > MAX_GROUPS) {
            throw new ApiException(ErrorCode.RESULT_TOO_LARGE, "结果分组过多",
                    "超过 " + MAX_GROUPS + " 组，请缩小日期范围或减少分组维度");
        }
        return rows;
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.validation("date_to 不能早于 date_from");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) >= 93) {
            throw ApiException.validation("单次查询跨度不超过 93 天");
        }
    }

    private static List<Dimension> dimensions(List<Dimension> leading, List<String> groupBy) {
        Set<Dimension> ordered = new LinkedHashSet<>(leading);
        groupBy.stream().map(ReportService::dimension).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private static Dimension dimension(String key) {
        try {
            return Dimension.byKey(key);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "未知维度", key);
        }
    }

    /** 默认列按可见性裁剪；显式请求越权指标一律拒绝，不静默丢弃。 */
    static List<Metric> resolveMetrics(CurrentUser user, List<String> requested) {
        List<Metric> visible = MetricRegistry.visibleTo(user.seesRealMetrics());
        if (requested == null) {
            return DEFAULT_METRICS.stream()
                    .map(MetricRegistry::byKey)
                    .filter(visible::contains)
                    .toList();
        }
        requested.stream()
                .filter(key -> MetricRegistry.all().stream().noneMatch(metric -> metric.key().equals(key)))
                .findFirst()
                .ifPresent(key -> {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "未知指标", key);
                });
        List<String> hidden = requested.stream()
                .filter(key -> visible.stream().noneMatch(metric -> metric.key().equals(key)))
                .toList();
        if (!hidden.isEmpty()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "没有查看收益类指标的权限",
                    "无权指标：" + String.join(", ", hidden));
        }
        return new ArrayList<>(new LinkedHashSet<>(requested)).stream().map(MetricRegistry::byKey).toList();
    }

    private static Set<String> allowedSortFields(List<Dimension> dimensions, List<Metric> metrics) {
        Set<String> allowed = new LinkedHashSet<>();
        dimensions.forEach(dimension -> allowed.add(dimension.key()));
        metrics.forEach(metric -> allowed.add(metric.key()));
        return allowed;
    }

    /** 多键稳定排序：从最后一个键排起；空值不论升降序都排在最后。 */
    private static List<Summaries.Row> sort(List<Summaries.Row> rows, List<SortSpec> specs, Set<String> allowed) {
        for (SortSpec spec : specs) {
            if (!allowed.contains(spec.field())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "排序字段不在结果列中", spec.field());
            }
        }
        List<Summaries.Row> items = new ArrayList<>(rows);
        for (int index = specs.size() - 1; index >= 0; index--) {
            SortSpec spec = specs.get(index);
            List<Summaries.Row> present = new ArrayList<>();
            List<Summaries.Row> missing = new ArrayList<>();
            for (Summaries.Row row : items) {
                (sortValue(row, spec.field()) == null ? missing : present).add(row);
            }
            Comparator<Summaries.Row> comparator =
                    Comparator.comparing(row -> asComparable(sortValue(row, spec.field())));
            present.sort(spec.descending() ? comparator.reversed() : comparator);
            items = new ArrayList<>(present);
            items.addAll(missing);
        }
        return items;
    }

    private static Object sortValue(Summaries.Row row, String field) {
        if (row.dimensions().containsKey(field)) {
            return row.dimension(field);
        }
        return row.value(MetricRegistry.byKey(field));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Comparable<Object> asComparable(Object value) {
        return (Comparable<Object>) value;
    }

    private static List<Column> columns(List<Dimension> dimensions, List<Metric> metrics) {
        List<Column> columns = new ArrayList<>();
        dimensions.forEach(dimension -> columns.add(
                new Column(dimension.key(), dimension.label(), "dimension", null, null)));
        metrics.forEach(metric -> columns.add(new Column(metric.key(), metric.label(),
                metric.kind().name().toLowerCase(java.util.Locale.ROOT),
                metric.unit().name().toLowerCase(java.util.Locale.ROOT), metric.precision())));
        return columns;
    }

    private static Map<String, Object> row(Summaries.Row item, List<Dimension> dimensions, List<Metric> metrics) {
        Map<String, Object> row = new LinkedHashMap<>();
        dimensions.forEach(dimension ->
                row.put(dimension.key(), dimensionValue(item.dimension(dimension.key()))));
        metrics.forEach(metric -> row.put(metric.key(), MetricRegistry.present(metric, item.value(metric))));
        return row;
    }

    private static Object dimensionValue(Object value) {
        return value instanceof LocalDate date ? date.toString() : value;
    }

    /** 数字的对外写法，与既有实现的格式化保持一致。 */
    static final class Numbers {

        private Numbers() {
        }

        /** 百分比：0.853 → "85.3%"（保留 digits 位小数，四舍六入五成双）。 */
        static String percent(BigDecimal value, int digits) {
            return value.multiply(BigDecimal.valueOf(100))
                    .setScale(digits, java.math.RoundingMode.HALF_EVEN)
                    .toPlainString() + "%";
        }

        /** 通用写法：200 → "200"，0.5 → "0.5"，保留 6 位有效数字并去掉尾随零。 */
        static String general(BigDecimal value) {
            BigDecimal rounded = value.round(new java.math.MathContext(6)).stripTrailingZeros();
            return rounded.scale() <= 0 && rounded.precision() - rounded.scale() <= 6
                    ? rounded.toBigInteger().toString()
                    : rounded.toPlainString();
        }
    }

    /** 导出视图取值范围，和契约里的枚举一致。 */
    public static boolean isKnownView(String view) {
        return List.of("aggregate", "daily", "raw").contains(view);
    }

    /** 最近一次数据落库时间，供页面显示数据新鲜度。 */
    public OffsetDateTime loadedAt(CurrentUser user, LocalDate from, LocalDate to) {
        return facts.loadedAt(conditions(user, from, to, Filters.EMPTY));
    }
}
