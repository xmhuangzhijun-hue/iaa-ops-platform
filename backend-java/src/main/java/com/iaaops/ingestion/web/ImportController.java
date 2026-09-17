package com.iaaops.ingestion.web;

import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.Permissions;
import com.iaaops.ingestion.ImportService;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 媒体导出表的上传与任务查询。Excel 是兜底通道，常规链路是后端直接对接媒体 API。 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private static final int MEDIA_MAX_LENGTH = 32;

    private final ImportService imports;

    ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskResponse create(@AuthenticationPrincipal CurrentUser user,
            @RequestParam("file") MultipartFile file, @RequestParam("media") String media) {
        require(user);
        if (media.isBlank() || media.length() > MEDIA_MAX_LENGTH) {
            throw ApiException.validation("media 长度需在 1-" + MEDIA_MAX_LENGTH + " 之间");
        }
        try {
            return TaskResponse.of(imports.create(user, media, filename(file), file.getBytes()));
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "上传的文件读不出来");
        }
    }

    @GetMapping("/{importId}")
    public TaskResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable String importId) {
        require(user);
        return TaskResponse.of(imports.get(user, importId));
    }

    private static String filename(MultipartFile file) {
        String name = file.getOriginalFilename();
        // 客户端给的文件名只用于展示，去掉路径部分，避免把目录结构也存进去
        return name == null || name.isBlank() ? "未命名.xlsx" : name.replaceAll(".*[/\\\\]", "");
    }

    private static void require(CurrentUser user) {
        if (!Permissions.has(user.permissions(), Permissions.IMPORTS_MANAGE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "没有该操作的权限");
        }
    }

    public record TaskResponse(String id, String media, String fileName, String status, List<LocalDate> statDates,
            int rowsTotal, int rowsReplaced, List<RowErrorResponse> errors, OffsetDateTime createdAt,
            OffsetDateTime finishedAt) {

        static TaskResponse of(ImportService.Task task) {
            return new TaskResponse(task.id(), task.media(), task.fileName(), task.status(), task.statDates(),
                    task.rowsTotal(), task.rowsReplaced(),
                    task.errors().stream().map(RowErrorResponse::of).toList(),
                    task.createdAt(), task.finishedAt());
        }
    }

    public record RowErrorResponse(int row, String column, String message) {

        static RowErrorResponse of(ImportService.RowError error) {
            return new RowErrorResponse(error.row(), error.column(), error.message());
        }
    }
}
