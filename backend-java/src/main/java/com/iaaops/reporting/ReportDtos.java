package com.iaaops.reporting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 看盘接口的请求与响应模型，字段名经 SNAKE_CASE 策略与契约一致。 */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record Filters(
            List<String> media,
            List<String> products,
            List<String> agencies,
            List<String> accounts,
            List<String> operators) {

        public static final Filters EMPTY = new Filters(List.of(), List.of(), List.of(), List.of(), List.of());

        public Filters {
            media = media == null ? List.of() : media;
            products = products == null ? List.of() : products;
            agencies = agencies == null ? List.of() : agencies;
            accounts = accounts == null ? List.of() : accounts;
            operators = operators == null ? List.of() : operators;
        }
    }

    public record SortSpec(@NotNull String field, @Pattern(regexp = "asc|desc") String direction) {

        public SortSpec {
            direction = direction == null ? "desc" : direction;
        }

        public boolean descending() {
            return "desc".equals(direction);
        }

        public static SortSpec desc(String field) {
            return new SortSpec(field, "desc");
        }
    }

    public record ReportQuery(
            @NotNull LocalDate dateFrom,
            @NotNull LocalDate dateTo,
            @Valid Filters filters,
            @Size(max = 100) String keyword,
            @Size(min = 1, max = 4) List<String> groupBy,
            List<String> metrics,
            @Valid @Size(max = 3) List<SortSpec> sort,
            @Min(1) Integer page,
            @Min(1) @jakarta.validation.constraints.Max(500) Integer pageSize) {

        public ReportQuery {
            filters = filters == null ? Filters.EMPTY : filters;
            groupBy = groupBy == null ? List.of("product") : groupBy;
            sort = sort == null ? List.of() : sort;
            page = page == null ? 1 : page;
            pageSize = pageSize == null ? 50 : pageSize;
        }
    }

    public record RawDetailQuery(
            @NotNull LocalDate dateFrom,
            @NotNull LocalDate dateTo,
            @Valid Filters filters,
            @Size(max = 100) String keyword,
            @Valid @Size(max = 3) List<SortSpec> sort,
            @Min(1) Integer page,
            @Min(1) @jakarta.validation.constraints.Max(500) Integer pageSize) {

        public RawDetailQuery {
            filters = filters == null ? Filters.EMPTY : filters;
            sort = sort == null ? List.of() : sort;
            page = page == null ? 1 : page;
            pageSize = pageSize == null ? 100 : pageSize;
        }
    }

    public record TrendQuery(
            @NotNull LocalDate dateFrom,
            @NotNull LocalDate dateTo,
            @Valid Filters filters,
            @Pattern(regexp = "day|hour") String granularity,
            @Size(min = 1, max = 4) List<String> metrics,
            String splitBy) {

        public TrendQuery {
            filters = filters == null ? Filters.EMPTY : filters;
            granularity = granularity == null ? "day" : granularity;
            metrics = metrics == null ? List.of("cost", "roi") : metrics;
        }

        public boolean hourly() {
            return "hour".equals(granularity);
        }
    }

    public record RoiAnomalyQuery(
            @NotNull LocalDate dateFrom,
            @NotNull LocalDate dateTo,
            @Valid Filters filters,
            @Size(min = 1, max = 3) List<String> groupBy,
            @Positive @DecimalMax("10") BigDecimal roiBelow,
            @DecimalMin("0") BigDecimal minCost) {

        public RoiAnomalyQuery {
            filters = filters == null ? Filters.EMPTY : filters;
            groupBy = groupBy == null ? List.of("account") : groupBy;
            roiBelow = roiBelow == null ? new BigDecimal("1.1") : roiBelow;
            minCost = minCost == null ? new BigDecimal("200") : minCost;
        }
    }

    public record Column(String key, String label, String kind, String unit, Integer precision) {
    }

    public record ReportResult(
            List<Column> columns,
            List<Map<String, Object>> rows,
            Map<String, Object> totals,
            int page,
            int pageSize,
            long totalRows,
            OffsetDateTime dataAsOf) {
    }

    public record TrendPoint(String bucket, Map<String, Object> values) {
    }

    public record TrendSeries(String key, String label, List<TrendPoint> points) {
    }

    public record TrendResult(List<String> metrics, List<TrendSeries> series, OffsetDateTime dataAsOf) {
    }

    public record RoiAnomaly(Map<String, String> dimensions, BigDecimal cost, BigDecimal revenue, BigDecimal roi,
            String reason) {
    }

    public record RoiAnomalyResult(String rule, List<RoiAnomaly> items, OffsetDateTime dataAsOf) {
    }

    public record Option(String value, String label) {
    }

    public record FilterOptions(List<Option> media, List<Option> products, List<Option> agencies,
            List<Option> accounts, List<Option> operators) {
    }
}
