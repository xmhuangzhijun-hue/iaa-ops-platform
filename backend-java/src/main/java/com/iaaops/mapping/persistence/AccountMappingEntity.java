package com.iaaops.mapping.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 账户到产品 / 代理 / 运营的映射。
 *
 * 映射不落在事实表里：改了映射，历史数据的归属跟着变，这是业务要的行为——
 * 账户换了代理之后，历史报表也应按新归属看。
 */
@Entity
@Table(name = "account_mappings")
@IdClass(AccountMappingEntity.Key.class)
public class AccountMappingEntity {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    @Id
    @Column(name = "media")
    private String media;

    @Id
    @Column(name = "account")
    private String account;

    @Column
    private String agency;

    @Column
    private String product;

    @Column
    private String operator;

    @Column(nullable = false)
    private Integer revision;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected AccountMappingEntity() {
    }

    public AccountMappingEntity(String tenantId, String media, String account, String agency, String product,
            String operator) {
        this.tenantId = tenantId;
        this.media = media;
        this.account = account;
        this.agency = agency;
        this.product = product;
        this.operator = operator;
        this.revision = 1;
    }

    public String getMedia() {
        return media;
    }

    public String getAccount() {
        return account;
    }

    public String getAgency() {
        return agency;
    }

    public String getProduct() {
        return product;
    }

    public String getOperator() {
        return operator;
    }

    public Integer getRevision() {
        return revision;
    }

    /** 三个归属字段与传入值是否完全一致；一致则本次不算修改。 */
    public boolean sameAs(String agency, String product, String operator) {
        return Objects.equals(this.agency, agency)
                && Objects.equals(this.product, product)
                && Objects.equals(this.operator, operator);
    }

    public void apply(String agency, String product, String operator) {
        this.agency = agency;
        this.product = product;
        this.operator = operator;
        this.revision += 1;
    }

    public static class Key implements Serializable {

        private String tenantId;
        private String media;
        private String account;

        public Key() {
        }

        public Key(String tenantId, String media, String account) {
            this.tenantId = tenantId;
            this.media = media;
            this.account = account;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key && Objects.equals(tenantId, key.tenantId)
                    && Objects.equals(media, key.media) && Objects.equals(account, key.account);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, media, account);
        }
    }
}
