package com.iaaops.reporting.domain;

import com.iaaops.shared.metrics.Metric;
import com.iaaops.shared.metrics.MetricRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按维度汇总基础指标并重算派生指标。
 *
 * 与既有实现同一套语义：基础指标按行求和，派生指标只用汇总值重算，从不平均明细比值；
 * 分组顺序沿用输入行的顺序（SQL 返回顺序），后续排序是稳定排序，因此并列项的相对次序可预期。
 */
public final class Summaries {

    private Summaries() {
    }

    /** 一行汇总结果：维度原值 + 基础指标合计 + 派生指标。 */
    public record Row(Map<String, Object> dimensions, Map<Metric, BigDecimal> values) {

        public BigDecimal value(Metric metric) {
            return values.get(metric);
        }

        public Object dimension(String key) {
            return dimensions.get(key);
        }
    }

    public static List<Row> summarize(List<Map<String, Object>> rows, List<Dimension> groupBy) {
        Map<List<Object>, Map<Metric, BigDecimal>> groups = new LinkedHashMap<>();
        Map<List<Object>, Map<String, Object>> keys = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = new ArrayList<>(groupBy.size());
            Map<String, Object> dimensions = new LinkedHashMap<>();
            for (Dimension dimension : groupBy) {
                Object value = row.get(dimension.key());
                key.add(value);
                dimensions.put(dimension.key(), value);
            }
            keys.putIfAbsent(key, dimensions);
            Map<Metric, BigDecimal> sums = groups.computeIfAbsent(key, ignored -> zeroes());
            for (Metric metric : MetricRegistry.baseMetrics()) {
                sums.merge(metric, number(row.get(metric.key())), BigDecimal::add);
            }
        }
        if (groupBy.isEmpty() && groups.isEmpty()) {
            // 没有任何数据时合计行仍要存在，全部为 0，与既有实现一致。
            groups.put(List.of(), zeroes());
            keys.put(List.of(), Map.of());
        }
        List<Row> summarized = new ArrayList<>(groups.size());
        groups.forEach((key, sums) -> {
            Map<Metric, BigDecimal> values = new LinkedHashMap<>(sums);
            values.putAll(MetricRegistry.derive(sums));
            summarized.add(new Row(keys.get(key), values));
        });
        return summarized;
    }

    private static Map<Metric, BigDecimal> zeroes() {
        Map<Metric, BigDecimal> sums = new LinkedHashMap<>();
        MetricRegistry.baseMetrics().forEach(metric -> sums.put(metric, BigDecimal.ZERO));
        return sums;
    }

    private static BigDecimal number(Object value) {
        return switch (value) {
            case null -> BigDecimal.ZERO;
            case BigDecimal decimal -> decimal;
            case Number other -> new BigDecimal(other.toString());
            default -> throw new IllegalArgumentException("指标值不是数字：" + value);
        };
    }

    /** 维度值转成展示用文本：日期取 ISO，未映射为空时给「未映射」。 */
    public static String label(Object value) {
        if (value == null) {
            return Dimension.UNMAPPED_LABEL;
        }
        return String.valueOf(value);
    }

    /** 全部指标键，供排序字段校验用。 */
    public static List<String> metricKeys() {
        return Arrays.stream(Metric.values()).map(Metric::key).toList();
    }
}
