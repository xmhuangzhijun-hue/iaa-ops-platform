package com.iaaops.ingestion;

import com.iaaops.governance.AuditLog;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.ingestion.persistence.ImportTaskEntity;
import com.iaaops.ingestion.persistence.ImportTaskRepository;
import com.iaaops.shared.Ids;
import com.iaaops.shared.error.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导入的受理与查询。
 *
 * 受理只做三件事：记任务、留痕、把文件内容交给后台处理，然后立刻返回 202——
 * 解析与入库放在请求线程里做，文件一大请求就挂着，前端也没法显示进度。
 */
@Service
public class ImportService {

    private final ImportTaskRepository tasks;
    private final ImportProcessor processor;
    private final AuditLog audit;
    private final Executor executor;

    ImportService(ImportTaskRepository tasks, ImportProcessor processor, AuditLog audit, TaskExecutor executor) {
        this.tasks = tasks;
        this.processor = processor;
        this.audit = audit;
        this.executor = executor;
    }

    @Transactional
    public Task create(CurrentUser user, String media, String fileName, byte[] content) {
        if (content.length == 0) {
            throw ApiException.validation("上传的文件是空的");
        }
        ImportTaskEntity entity = new ImportTaskEntity(Ids.next("imp"), user.tenantId(), media, fileName, user.id());
        tasks.save(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("media", media);
        detail.put("file_name", fileName);
        detail.put("bytes", content.length);
        audit.record(user, AuditLog.IMPORT_CREATE, "import", entity.getId(), detail);

        String taskId = entity.getId();
        // 事务提交后再交给后台线程：否则后台可能先读到还没落库的任务
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executor.execute(() -> processor.process(taskId, content));
                    }
                });
        return toTask(entity);
    }

    @Transactional(readOnly = true)
    public Task get(CurrentUser user, String taskId) {
        return tasks.findByIdAndTenantId(taskId, user.tenantId())
                .map(ImportService::toTask)
                .orElseThrow(() -> ApiException.notFound("导入任务不存在"));
    }

    static Task toTask(ImportTaskEntity entity) {
        return new Task(entity.getId(), entity.getMedia(), entity.getFileName(), entity.getStatus(),
                entity.getStatDates().stream().map(LocalDate::parse).toList(),
                entity.getRowsTotal(), entity.getRowsReplaced(),
                entity.getErrors().stream().map(RowError::from).toList(),
                entity.getCreatedAt(), entity.getFinishedAt());
    }

    public record Task(String id, String media, String fileName, String status, List<LocalDate> statDates,
            int rowsTotal, int rowsReplaced, List<RowError> errors, OffsetDateTime createdAt,
            OffsetDateTime finishedAt) {
    }

    public record RowError(int row, String column, String message) {

        static RowError from(Map<String, Object> raw) {
            Object row = raw.get("row");
            return new RowError(row instanceof Number number ? number.intValue() : 0,
                    (String) raw.get("column"), String.valueOf(raw.get("message")));
        }
    }
}
