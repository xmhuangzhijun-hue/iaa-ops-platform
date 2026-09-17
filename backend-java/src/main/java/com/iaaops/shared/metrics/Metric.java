package com.iaaops.shared.metrics;

/**
 * 指标口径的唯一定义处（共享内核）。
 *
 * 基础指标按行求和；派生指标只用汇总后的基础指标重算，从不平均明细比值；分母为 0 时为空，不以 0 冒充。
 * reporting 模块用它计算，iam 模块用 {@link #revenueSide()} 判断对外口径的可见性——
 * 两边共用同一份定义，避免出现两套 ROI / CPC 实现。
 */
public enum Metric {

    COST("cost", "消耗", Kind.BASE, Unit.MONEY, "sum(cost)", "媒体平台同步的投放消耗。", 2, false),
    REVENUE("revenue", "预估收益", Kind.BASE, Unit.MONEY, "sum(revenue)", "客户端变现的预估收益。", 2, true),
    IMPRESSIONS("impressions", "曝光", Kind.BASE, Unit.COUNT, "sum(impressions)", "媒体平台同步的曝光次数。", 0, false),
    CLICKS("clicks", "点击", Kind.BASE, Unit.COUNT, "sum(clicks)", "媒体平台同步的点击次数。", 0, false),
    LAUNCHES("launches", "启动数", Kind.BASE, Unit.COUNT, "sum(launches)", "快应用被拉起的次数。", 0, false),
    CALLBACKS("callbacks", "回传数", Kind.BASE, Unit.COUNT, "sum(callbacks)", "按回传规则回传给媒体的转化数。", 0, false),
    CONVERSIONS("conversions", "转化数", Kind.BASE, Unit.COUNT, "sum(conversions)", "满足转化规则的转化数。", 0, false),

    ROI("roi", "ROI", Kind.DERIVED, Unit.RATIO, "revenue / cost",
            "预估收益与消耗之比；汇总行用总收益 / 总消耗重算。", 3, true),
    CTR("ctr", "点击率", Kind.DERIVED, Unit.RATIO, "clicks / impressions", "曝光到点击的比例。", 4, false),
    CPM("cpm", "CPM", Kind.DERIVED, Unit.PER_UNIT, "cost / impressions × 1000", "千次曝光成本。", 2, false),
    CPC("cpc", "CPC", Kind.DERIVED, Unit.PER_UNIT, "cost / clicks", "每次点击成本。", 4, false),
    CLICK_ARPU("click_arpu", "点击 ARPU", Kind.DERIVED, Unit.PER_UNIT, "revenue / clicks",
            "每次点击带来的预估收益。", 4, true),
    LAUNCH_RATE("launch_rate", "启动率", Kind.DERIVED, Unit.RATIO, "launches / clicks", "点击到启动的跳转率。", 4, false),
    CVR("cvr", "转化率", Kind.DERIVED, Unit.RATIO, "conversions / clicks", "点击到转化的比例。", 4, false),
    CONVERSION_COST("conversion_cost", "转化成本", Kind.DERIVED, Unit.PER_UNIT, "cost / conversions",
            "每次转化对应的消耗。", 4, false),
    CALLBACK_COST("callback_cost", "回传成本", Kind.DERIVED, Unit.PER_UNIT, "cost / callbacks",
            "每次回传对应的消耗。", 4, false),
    LAUNCH_CALLBACK_RATE("launch_callback_rate", "启动→回传率", Kind.DERIVED, Unit.RATIO, "callbacks / launches",
            "启动到回传的比例。", 4, false),
    REVENUE_GAP("revenue_gap", "回收差额", Kind.DERIVED, Unit.MONEY, "revenue - cost", "预估收益减消耗。", 2, true);

    public enum Kind { BASE, DERIVED }

    public enum Unit { MONEY, COUNT, RATIO, PER_UNIT }

    private final String key;
    private final String label;
    private final Kind kind;
    private final Unit unit;
    private final String formula;
    private final String description;
    private final int precision;
    private final boolean revenueSide;

    Metric(String key, String label, Kind kind, Unit unit, String formula, String description, int precision,
            boolean revenueSide) {
        this.key = key;
        this.label = label;
        this.kind = kind;
        this.unit = unit;
        this.formula = formula;
        this.description = description;
        this.precision = precision;
        this.revenueSide = revenueSide;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public Unit unit() {
        return unit;
    }

    public String formula() {
        return formula;
    }

    public String description() {
        return description;
    }

    public int precision() {
        return precision;
    }

    /** 收益类指标：对外口径账号不可见。 */
    public boolean revenueSide() {
        return revenueSide;
    }

    public boolean isBase() {
        return kind == Kind.BASE;
    }
}
