import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "../api/client";
import { useScreenMetrics } from "../app/useScreenMetrics";
import { DataGrid } from "../components/DataGrid";
import { DimensionPicker } from "../components/DimensionPicker";
import { ExportButton } from "../components/ExportButton";
import { KpiRow } from "../components/KpiRow";
import { MetricPicker } from "../components/MetricPicker";
import { PageHeader } from "../components/PageHeader";
import { RowCount } from "../components/RowCount";
import { ErrorBlock } from "../components/States";
import { FilterBar } from "../filters/FilterBar";
import { queryBase, useFilters } from "../filters/FilterProvider";
import type { Dimension, MetricKey } from "../lib/dimensions";
import { useSessionState } from "../lib/storage";

const SCREEN = "report-aggregate";
const DIMENSIONS: Dimension[] = ["media", "product", "agency", "account", "operator", "campaign", "stat_date", "stat_hour"];
const DEFAULT_METRICS: MetricKey[] = ["cost", "revenue", "roi", "clicks", "cpc", "click_arpu", "ctr", "cvr"];
const ROW_LIMIT = 500;

export function AggregatePage({ embedded = false }: { embedded?: boolean }) {
  const { filters, refreshToken } = useFilters();
  const metrics = useScreenMetrics(SCREEN, DEFAULT_METRICS);
  const [groupBy, setGroupBy] = useSessionState<Dimension[]>(`iaa.group.${SCREEN}`, ["product"]);

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
    queryFn: () => unwrap(api.POST("/api/v1/reports/aggregate", { body })),
    enabled: metrics.ready,
    placeholderData: keepPreviousData,
  });
  const data = report.data;

  return (
    <div className="space-y-4">
      {!embedded && <PageHeader
        title="聚合"
        description="按所选维度汇总当前筛选范围，合计与比率均由汇总值重算。"
        asOf={data?.data_as_of}
        actions={<ExportButton view="aggregate" body={body} disabled={!data} />}
      />}
      {embedded && <div className="workspace-data-toolbar"><span>当前账号的可见数据 · 服务端按权限返回指标</span><ExportButton view="aggregate" body={body} disabled={!data} /></div>}
      <FilterBar />
      <div className="card flex flex-wrap items-center justify-between gap-3 p-3">
        <DimensionPicker available={DIMENSIONS} value={groupBy} onChange={setGroupBy} />
        <MetricPicker visible={metrics.visible} value={metrics.value} onChange={metrics.set} />
      </div>
      {report.isError ? (
        <ErrorBlock error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <>
          {!embedded && <KpiRow columns={data?.columns} totals={data?.totals} />}
          <DataGrid
            columns={data?.columns ?? []}
            rows={data?.rows ?? []}
            totals={data?.totals}
            loading={report.isFetching}
            caption={data && <RowCount total={data.total_rows} shown={data.rows.length} />}
          />
        </>
      )}
    </div>
  );
}
