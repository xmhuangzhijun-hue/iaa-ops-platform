package com.iaaops.shared.metrics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 派生指标的计算入口。与 Python 侧原实现逐条对齐：先汇总基础指标，再用汇总值重算比值。
 */
public final class MetricRegistry {

    // 与既有 Python 实现的 Decimal 默认上下文一致：28 位有效数字、四舍六入五成双。
    // 两边口径必须逐位可比，精度与舍入策略不能各写各的。
    private static final MathContext DIVISION = new MathContext(28, RoundingMode.HALF_EVEN);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private static final Map<Metric, Function<Map<Metric, BigDecimal>, BigDecimal>> RULES =
            new EnumMap<>(Metric.class);

    static {
        RULES.put(Metric.ROI, bases -> ratio(bases, Metric.REVENUE, Metric.COST, BigDecimal.ONE));
        RULES.put(Metric.CTR, bases -> ratio(bases, Metric.CLICKS, Metric.IMPRESSIONS, BigDecimal.ONE));
        RULES.put(Metric.CPM, bases -> ratio(bases, Metric.COST, Metric.IMPRESSIONS, THOUSAND));
        RULES.put(Metric.CPC, bases -> ratio(bases, Metric.COST, Metric.CLICKS, BigDecimal.ONE));
        RULES.put(Metric.CLICK_ARPU, bases -> ratio(bases, Metric.REVENUE, Metric.CLICKS, BigDecimal.ONE));
        RULES.put(Metric.LAUNCH_RATE, bases -> ratio(bases, Metric.LAUNCHES, Metric.CLICKS, BigDecimal.ONE));
        RULES.put(Metric.CVR, bases -> ratio(bases, Metric.CONVERSIONS, Metric.CLICKS, BigDecimal.ONE));
        RULES.put(Metric.CONVERSION_COST, bases -> ratio(bases, Metric.COST, Metric.CONVERSIONS, BigDecimal.ONE));
        RULES.put(Metric.CALLBACK_COST, bases -> ratio(bases, Metric.COST, Metric.CALLBACKS, BigDecimal.ONE));
        RULES.put(Metric.LAUNCH_CALLBACK_RATE,
                bases -> ratio(bases, Metric.CALLBACKS, Metric.LAUNCHES, BigDecimal.ONE));
        RULES.put(Metric.REVENUE_GAP, bases -> value(bases, Metric.REVENUE).subtract(value(bases, Metric.COST)));
    }

    private MetricRegistry() {
    }

    public static List<Metric> all() {
        return List.of(Metric.values());
    }

    public static List<Metric> baseMetrics() {
        return Arrays.stream(Metric.values()).filter(Metric::isBase).toList();
    }

    /** 当前账号可见的指标：没有真实口径权限时去掉收益类。 */
    public static List<Metric> visibleTo(boolean realMetrics) {
        return Arrays.stream(Metric.values()).filter(metric -> realMetrics || !metric.revenueSide()).toList();
    }

    public static Metric byKey(String key) {
        return Arrays.stream(Metric.values())
                .filter(metric -> metric.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知指标：" + key));
    }

    /** 由汇总后的基础指标重算全部派生指标；分母为 0 返回 null。 */
    public static Map<Metric, BigDecimal> derive(Map<Metric, BigDecimal> bases) {
        Map<Metric, BigDecimal> derived = new EnumMap<>(Metric.class);
        RULES.forEach((metric, rule) -> derived.put(metric, rule.apply(bases)));
        return derived;
    }

    private static BigDecimal ratio(Map<Metric, BigDecimal> bases, Metric numerator, Metric denominator,
            BigDecimal scale) {
        BigDecimal divisor = value(bases, denominator);
        if (divisor.signum() == 0) {
            return null;
        }
        return value(bases, numerator).multiply(scale).divide(divisor, DIVISION);
    }

    /** 按指标精度输出给接口；null 保持为空，精度为 0 时给整数。 */
    public static BigDecimal present(Metric metric, BigDecimal value) {
        return value == null ? null : value.setScale(metric.precision(), RoundingMode.HALF_EVEN);
    }

    private static BigDecimal value(Map<Metric, BigDecimal> bases, Metric metric) {
        BigDecimal value = bases.get(metric);
        return value == null ? BigDecimal.ZERO : value;
    }
}
