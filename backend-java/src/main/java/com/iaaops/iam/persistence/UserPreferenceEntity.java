package com.iaaops.iam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_preferences")
public class UserPreferenceEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "theme_mode", nullable = false)
    private String themeMode;

    @Column(name = "theme_preset", nullable = false)
    private String themePreset;

    @Column(name = "custom_primary")
    private String customPrimary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "table_columns", nullable = false)
    private Map<String, List<String>> tableColumns;

    @Column(nullable = false)
    private int revision;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected UserPreferenceEntity() {
    }

    public UserPreferenceEntity(String userId, String themeMode, String themePreset, String customPrimary,
            Map<String, List<String>> tableColumns, int revision) {
        this.userId = userId;
        this.themeMode = themeMode;
        this.themePreset = themePreset;
        this.customPrimary = customPrimary;
        this.tableColumns = tableColumns;
        this.revision = revision;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public String getThemePreset() {
        return themePreset;
    }

    public String getCustomPrimary() {
        return customPrimary;
    }

    public Map<String, List<String>> getTableColumns() {
        return tableColumns;
    }

    public int getRevision() {
        return revision;
    }

    public void apply(String themeMode, String themePreset, String customPrimary, Map<String, List<String>> tableColumns,
            int revision) {
        this.themeMode = themeMode;
        this.themePreset = themePreset;
        this.customPrimary = customPrimary;
        this.tableColumns = tableColumns;
        this.revision = revision;
    }
}
