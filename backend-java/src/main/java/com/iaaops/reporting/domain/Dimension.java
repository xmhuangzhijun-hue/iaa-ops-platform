package com.iaaops.reporting.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 报表可用的分组维度。
 *
 * 枚举同时承担 SQL 列名白名单：分组、排序与关键词匹配只能落在这些列上，
 * 请求里的字符串永远不会拼进 SQL。
 */
public enum Dimension {

    STAT_DATE("stat_date", "日期", "f.stat_date"),
    STAT_HOUR("stat_hour", "小时", "f.stat_hour"),
    MEDIA("media", "媒体", "f.media"),
    PRODUCT("product", "产品", "m.product"),
    AGENCY("agency", "代理", "m.agency"),
    ACCOUNT("account", "账户", "f.account"),
    OPERATOR("operator", "运营", "m.operator"),
    CAMPAIGN("campaign", "推广计划", "f.campaign");

    /** 原始明细固定输出的维度列，顺序即列顺序。 */
    public static final List<Dimension> RAW_DIMENSIONS =
            List.of(STAT_DATE, STAT_HOUR, MEDIA, ACCOUNT, CAMPAIGN, PRODUCT, AGENCY, OPERATOR);

    public static final Map<String, String> MEDIA_LABELS = Map.of(
            "vivo", "vivo", "oppo", "OPPO", "huawei", "华为", "xiaomi", "小米", "honor", "荣耀");

    public static final String UNMAPPED_LABEL = "未映射";

    private final String key;
    private final String label;
    private final String column;

    Dimension(String key, String label, String column) {
        this.key = key;
        this.label = label;
        this.column = column;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** 带表别名的列名；只来自本枚举，不接受外部字符串。 */
    public String column() {
        return column;
    }

    public static Dimension byKey(String key) {
        return Arrays.stream(values())
                .filter(dimension -> dimension.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知维度：" + key));
    }
}
