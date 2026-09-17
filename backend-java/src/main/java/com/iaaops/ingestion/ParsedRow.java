package com.iaaops.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 解析成功的一行明细。媒体来自表单字段，不从表里取。 */
public record ParsedRow(
        LocalDate statDate,
        Short statHour,
        String account,
        String campaign,
        BigDecimal cost,
        BigDecimal revenue,
        long impressions,
        long clicks,
        long launches,
        long callbacks,
        long conversions) {
}
