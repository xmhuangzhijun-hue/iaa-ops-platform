import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { useMemo } from "react";
import { api, unwrap, type Schemas } from "../api/client";
import { useMetricCatalog } from "../api/hooks";
import { usePrincipal } from "../auth/AuthProvider";
import { PageHeader } from "../components/PageHeader";
import { EmptyBlock, ErrorBlock } from "../components/States";
import { TrendChart } from "../components/TrendChart";
import { FilterBar } from "../filters/FilterBar";
import { queryBase, useFilters } from "../filters/FilterProvider";
import { daysBetween } from "../lib/dates";
import { DIMENSION_LABELS, type Dimension, type MetricKey } from "../lib/dimensions";
import { useSessionState } from "../lib/storage";

const SPLITS: Dimension[] = ["media", "product", "agency", "account", "operator", "campaign"];
const MAX_METRICS = 4;

export function TrendPage() {
  const principal = usePrincipal();
  const { filters, refreshToken } = useFilters();
  const catalog = useMetricCatalog();
  const [chosen, setChosen] = useSessionState<MetricKey[]>("iaa.trend.metrics", ["cost", "roi"]);
  const [granularity, setGranularity] = useSessionState<"day" | "hour">("iaa.trend.granularity", "day");
  const [splitBy, setSplitBy] = useSessionState<Dimension | null>("iaa.trend.split", null);

  const visible = principal.visible_metrics;
  const allowedChosen = chosen.filter((key) => visible.includes(key));
  const metrics: MetricKey[] = allowedChosen.length ? allowedChosen : ["cost"];
  const hourAllowed = daysBetween(filters.dateFrom, filters.dateTo) < 7;
  const effectiveGranularity = hourAllowed ? granularity : "day";

  const body: Schemas["TrendQuery"] = { ...queryBase(filters), granularity: effectiveGranularity, metrics, split_by: splitBy };
  const trend = useQuery({
    queryKey: ["report", "trend", body, refreshToken],
    queryFn: () => unwrap(api.POST("/api/v1/reports/trend", { body })),
    placeholderData: keepPreviousData,
  });

  const options = (catalog.data?.items ?? []).filter((item) => visible.includes(item.key as MetricKey));
  const meta = useMemo(
    () => Object.fromEntries((catalog.data?.items ?? []).map((item) => [item.key, { label: item.label, unit: item.unit, precision: item.precision }])),
    [catalog.data],
  );

  const toggleMetric = (key: MetricKey) => {
    if (metrics.includes(key)) {
      if (metrics.length > 1) setChosen(metrics.filter((item) => item !== key));
    } else if (metrics.length < MAX_METRICS) {
      setChosen([...metrics, key]);
    }
  };

  return (
    <div className="space-y-4">
      <PageHeader title="趋势图" description="按天或小时查看指标走势；可按一个维度拆成多条线，最多 8 条，其余并入「其他」。" asOf={trend.data?.data_as_of} />
      <FilterBar />
      <div className="card space-y-3 p-3">
        <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="指标">
          <span className="mr-1 text-xs font-semibold text-muted">
            指标（最多 {MAX_METRICS} 个{splitBy ? "，拆线时只画第 1 个" : ""}）
          </span>
          {options.map((item) => (
            <button
              key={item.key}
              type="button"
              className="chip"
              aria-pressed={metrics.includes(item.key as MetricKey)}
              disabled={!metrics.includes(item.key as MetricKey) && metrics.length >= MAX_METRICS}
              onClick={() => toggleMetric(item.key as MetricKey)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex gap-1" role="radiogroup" aria-label="时间粒度">
            {(["day", "hour"] as const).map((value) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={effectiveGranularity === value}
                aria-pressed={effectiveGranularity === value}
                className="chip"
                disabled={value === "hour" && !hourAllowed}
                onClick={() => setGranularity(value)}
              >
                {value === "day" ? "按天" : "按小时"}
              </button>
            ))}
          </div>
          <label className="flex items-center gap-2 text-sm">
            <span className="text-xs font-semibold text-muted">拆分</span>
            <select className="control" value={splitBy ?? ""} onChange={(event) => setSplitBy((event.target.value || null) as Dimension | null)}>
              <option value="">不拆分</option>
              {SPLITS.map((dimension) => (
                <option key={dimension} value={dimension}>
                  按{DIMENSION_LABELS[dimension]}
                </option>
              ))}
            </select>
          </label>
          {!hourAllowed && <span className="text-xs text-muted">按小时需日期跨度不超过 7 天</span>}
        </div>
      </div>

      {trend.isError ? (
        <ErrorBlock error={trend.error} onRetry={() => void trend.refetch()} />
      ) : trend.data && catalog.data ? (
        trend.data.series.some((series) => series.points.length) ? (
          <div className="card p-3">
            <TrendChart result={trend.data} meta={meta} />
          </div>
        ) : (
          <EmptyBlock title="当前筛选范围没有数据" />
        )
      ) : (
        <div className="card grid h-[426px] place-items-center text-muted" role="status">
          <LoaderCircle className="size-6 animate-spin" aria-label="加载中" />
        </div>
      )}
    </div>
  );
}
