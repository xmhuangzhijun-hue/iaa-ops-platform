import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../api/client";
import { useScreenMetrics } from "../app/useScreenMetrics";
import { DataGrid } from "../components/DataGrid";
import { DimensionPicker } from "../components/DimensionPicker";
import { ExportButton } from "../components/ExportButton";
import { MetricPicker } from "../components/MetricPicker";
import { PageHeader } from "../components/PageHeader";
import { RowCount } from "../components/RowCount";
import { ErrorBlock } from "../components/States";
import { FilterBar } from "../filters/FilterBar";
import { queryBase, useFilters } from "../filters/FilterProvider";
import type { Dimension, MetricKey } from "../lib/dimensions";
import { useSessionState } from "../lib/storage";

const SCREEN = "report-daily";
const DIMENSIONS: Dimension[] = ["media", "product", "agency", "account", "operator", "campaign"];
const DEFAULT_METRICS: MetricKey[] = ["cost", "revenue", "roi", "clicks", "cpc", "click_arpu"];
const ROW_LIMIT = 500;

export function DailyPage() {
  const { filters, refreshToken } = useFilters();
  const metrics = useScreenMetrics(SCREEN, DEFAULT_METRICS);
  const [groupBy, setGroupBy] = useSessionState<Dimension[]>(`iaa.group.${SCREEN}`, ["media"]);

  const body: Schemas["ReportQuery"] = {
    ...queryBase(filters),
    keyword: filters.keyword || null,
    group_by: groupBy,
    metrics: metrics.value,
    page: 1,
    page_size: ROW_LIMIT,
  };
  const report = useQuery({
    queryKey: ["report", SCREEN, body, refreshToken],
    queryFn: () => unwrap(api.POST("/api/v1/reports/daily", { body })),
    enabled: metrics.ready,
    placeholderData: keepPreviousData,
  });
  const data = report.data;

  return (
    <div className="space-y-4">
      <PageHeader
        title="分天明细"
        description="日期固定在最前，其余维度逐日展开对比；默认最新日期在上。"
        asOf={data?.data_as_of}
        actions={<ExportButton view="daily" body={body} disabled={!data} />}
      />
      <FilterBar />
      <div className="card flex flex-wrap items-center justify-between gap-3 p-3">
        <DimensionPicker available={DIMENSIONS} value={groupBy} onChange={setGroupBy} max={3} label="日期之后按" />
        <MetricPicker visible={metrics.visible} value={metrics.value} onChange={metrics.set} />
      </div>
      {report.isError ? (
        <ErrorBlock error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <DataGrid
          columns={data?.columns ?? []}
          rows={data?.rows ?? []}
          totals={data?.totals}
          loading={report.isFetching}
          caption={data && <RowCount total={data.total_rows} shown={data.rows.length} />}
        />
      )}
    </div>
  );
}
