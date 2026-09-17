package com.iaaops.ingestion.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 一次导入的受理与结果。文件本身不入库，只留下它覆盖了哪几天、替换了多少行。 */
@Entity
@Table(name = "import_tasks")
public class ImportTaskEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String media;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stat_dates", nullable = false)
    private List<String> statDates;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_replaced", nullable = false)
    private int rowsReplaced;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> errors;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    protected ImportTaskEntity() {
    }

    public ImportTaskEntity(String id, String tenantId, String media, String fileName, String createdBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.media = media;
        this.fileName = fileName;
        this.createdBy = createdBy;
        this.status = "pending";
        this.statDates = List.of();
        this.errors = List.of();
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getMedia() {
        return media;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getStatDates() {
        return statDates;
    }

    public int getRowsTotal() {
        return rowsTotal;
    }

    public int getRowsReplaced() {
        return rowsReplaced;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void markProcessing() {
        this.status = "processing";
    }

    public void finish(String status, List<LocalDate> dates, int rowsTotal, int rowsReplaced,
            List<Map<String, Object>> errors, OffsetDateTime finishedAt) {
        this.status = status;
        this.statDates = dates.stream().map(LocalDate::toString).toList();
        this.rowsTotal = rowsTotal;
        this.rowsReplaced = rowsReplaced;
        this.errors = errors;
        this.finishedAt = finishedAt;
    }
}
