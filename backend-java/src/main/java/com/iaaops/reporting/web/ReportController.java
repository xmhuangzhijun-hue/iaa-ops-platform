package com.iaaops.reporting.web;

import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.Permissions;
import com.iaaops.reporting.ReportDtos;
import com.iaaops.reporting.ReportService;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 看盘接口。
 *
 * 权限在这里先判：看盘只需 dashboard.read，涉及真实口径的趋势、ROI 异常与原始明细需要 metrics.real；
 * 数据范围由服务层落到 SQL 条件，不在这里过滤。
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/filter-options")
    public ReportDtos.FilterOptions filterOptions(@AuthenticationPrincipal CurrentUser user,
            @RequestParam("date_from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam("date_to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        require(user, Permissions.DASHBOARD_READ);
        return reports.filterOptions(user, dateFrom, dateTo);
    }

    @PostMapping("/reports/aggregate")
    public ReportDtos.ReportResult aggregate(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ReportDtos.ReportQuery query) {
        require(user, Permissions.DASHBOARD_READ);
        return reports.aggregate(user, query);
    }

    @PostMapping("/reports/daily")
    public ReportDtos.ReportResult daily(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ReportDtos.ReportQuery query) {
        require(user, Permissions.DASHBOARD_READ);
        return reports.daily(user, query);
    }

    @PostMapping("/reports/trend")
    public ReportDtos.TrendResult trend(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ReportDtos.TrendQuery query) {
        require(user, Permissions.METRICS_REAL);
        return reports.trend(user, query);
    }

    @PostMapping("/reports/roi-anomalies")
    public ReportDtos.RoiAnomalyResult roiAnomalies(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ReportDtos.RoiAnomalyQuery query) {
        require(user, Permissions.METRICS_REAL);
        return reports.roiAnomalies(user, query);
    }

    @PostMapping("/reports/raw")
    public ReportDtos.ReportResult rawDetail(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ReportDtos.RawDetailQuery query) {
        require(user, Permissions.METRICS_REAL);
        return reports.rawDetail(user, query);
    }

    @PostMapping("/reports/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal CurrentUser user,
            @RequestParam("view") String view, @Valid @RequestBody ReportDtos.ReportQuery query) {
        require(user, Permissions.DASHBOARD_READ);
        if (!ReportService.isKnownView(view)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "未知导出视图", view);
        }
        ReportService.Csv csv = reports.exportCsv(user, view, query);
        String disposition = "attachment; filename*=UTF-8''"
                + URLEncoder.encode(csv.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(csv.content());
    }

    private static void require(CurrentUser user, String permission) {
        if (!Permissions.has(user.permissions(), permission)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "没有该操作的权限");
        }
    }
}
