package com.iaaops.iam.domain;

import java.util.List;
import java.util.Map;

/**
 * 数据范围：维度缺省/null 表示不限，空数组表示没有授权，非空数组取交集。
 * 报表范围在 SQL 条件中强制，不能套用普通请求筛选的空数组=不筛选语义。
 */
public record DataScope(List<String> agencies, List<String> products, List<String> operators) {

    public static final DataScope UNLIMITED = new DataScope(null, null, null);

    @SuppressWarnings("unchecked")
    public static DataScope fromJson(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return UNLIMITED;
        }
        return new DataScope(
                (List<String>) raw.get("agencies"),
                (List<String>) raw.get("products"),
                (List<String>) raw.get("operators"));
    }

    /** 回写成 JSONB 结构；不限的维度不写键，与既有数据的写法一致。 */
    public Map<String, Object> toJson() {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        if (agencies != null) {
            raw.put("agencies", agencies);
        }
        if (products != null) {
            raw.put("products", products);
        }
        if (operators != null) {
            raw.put("operators", operators);
        }
        return raw;
    }

    public boolean unlimited() {
        return agencies == null && products == null && operators == null;
    }
}
