package com.iaaops.reporting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 投放明细事实。
 *
 * 查询走 {@link FactQueryRepository} 的 SQL，这个实体不用于读写，只承担两件事：
 * 启动时 {@code ddl-auto: validate} 校验列与真实表一致，以及集成测试里按同一份定义建表。
 * 产品 / 代理 / 运营不落在事实表，查询时经账户映射补齐，映射修改对历史生效。
 */
@Entity
@Table(name = "ad_facts")
public class AdFactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "stat_hour")
    private Short statHour;

    @Column(nullable = false)
    private String media;

    @Column(nullable = false)
    private String account;

    @Column
    private String campaign;

    @Column(nullable = false)
    private BigDecimal cost;

    @Column(nullable = false)
    private BigDecimal revenue;

    @Column(nullable = false)
    private Long impressions;

    @Column(nullable = false)
    private Long clicks;

    @Column(nullable = false)
    private Long launches;

    @Column(nullable = false)
    private Long callbacks;

    @Column(nullable = false)
    private Long conversions;

    @Column(name = "loaded_at")
    private OffsetDateTime loadedAt;

    protected AdFactEntity() {
    }
}
