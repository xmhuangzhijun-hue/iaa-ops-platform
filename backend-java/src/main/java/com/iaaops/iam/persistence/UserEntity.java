package com.iaaops.iam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_scope", nullable = false)
    private Map<String, Object> dataScope;

    @Column(nullable = false)
    private int revision;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected UserEntity() {
    }

    public UserEntity(String id, String tenantId, String username, String displayName, String passwordHash,
            boolean mustChangePassword, Map<String, Object> dataScope) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.status = "active";
        this.mustChangePassword = mustChangePassword;
        this.dataScope = dataScope;
        this.revision = 1;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Map<String, Object> getDataScope() {
        return dataScope;
    }

    public int getRevision() {
        return revision;
    }

    public void applyScope(Map<String, Object> dataScope) {
        this.dataScope = dataScope;
    }

    public void bumpRevision() {
        this.revision += 1;
    }
}
