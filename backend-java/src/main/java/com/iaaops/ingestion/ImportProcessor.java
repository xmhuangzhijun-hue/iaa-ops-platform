package com.iaaops.ingestion;

import com.iaaops.ingestion.persistence.FactWriteRepository;
import com.iaaops.ingestion.persistence.ImportTaskEntity;
import com.iaaops.ingestion.persistence.ImportTaskRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 解析与入库。
 *
 * 按天整体替换：文件覆盖了哪几天，就把这几天在该媒体下的既有行全删掉再写入——
 * 重复导入同一天不会累加。替换与任务状态在同一个事务里，失败就整批不落地。
 */
@Component
public class ImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);

    private final ImportTaskRepository tasks;
    private final FactWriteRepository facts;
    private final WorkbookParser parser;

    ImportProcessor(ImportTaskRepository tasks, FactWriteRepository facts, WorkbookParser parser) {
        this.tasks = tasks;
        this.facts = facts;
        this.parser = parser;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String taskId, byte[] content) {
        ImportTaskEntity task = tasks.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("导入任务不存在，跳过处理 taskId={}", taskId);
            return;
        }
        task.markProcessing();
        try {
            WorkbookParser.Result result = parser.parse(content);
            if (result.fatal()) {
                task.finish("failed", List.of(), 0, 0, result.errors(), finishedAt());
                return;
            }
            Set<LocalDate> dates = new LinkedHashSet<>(result.rows().stream().map(ParsedRow::statDate).sorted().toList());
            int replaced = facts.deleteDays(task.getTenantId(), task.getMedia(), dates);
            facts.insert(task.getTenantId(), task.getMedia(), result.rows());
            task.finish("succeeded", List.copyOf(dates), result.rows().size(), replaced, result.errors(),
                    finishedAt());
        } catch (RuntimeException exception) {
            // 处理线程里的异常没人接，必须自己落成任务状态，否则任务会永远停在 processing
            log.error("导入处理失败 taskId={}", taskId, exception);
            task.finish("failed", List.of(), 0, 0,
                    List.of(WorkbookParser.error(0, null, "处理失败：" + exception.getClass().getSimpleName())),
                    finishedAt());
        }
    }

    /**
     * 结束时刻取库里的钟：created_at 由数据库默认值写入，两个时间戳来自两个钟就会出现
     * "完成早于创建"。取不到也不能让任务卡在 processing——终态一定要写下去，宁可退回 JVM 时钟。
     */
    private OffsetDateTime finishedAt() {
        try {
            return tasks.databaseNow().atOffset(ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            log.warn("取库时钟失败，改用 JVM 时钟", exception);
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    /** 供任务记录使用的错误结构，与契约的 ImportRowError 一致。 */
    static Map<String, Object> error(int row, String column, String message) {
        return WorkbookParser.error(row, column, message);
    }
}
