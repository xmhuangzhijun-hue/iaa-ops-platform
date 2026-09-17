import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api, unwrap, type Schemas } from "../api/client";
import { DataGrid } from "../components/DataGrid";
import { DimensionPicker } from "../components/DimensionPicker";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { FilterBar } from "../filters/FilterBar";
import { queryBase, useFilters } from "../filters/FilterProvider";
import { DIMENSION_LABELS, type Dimension, type ReportColumn, type Row } from "../lib/dimensions";
import { useSessionState } from "../lib/storage";

const DIMENSIONS: Dimension[] = ["account", "product", "agency", "operator", "media", "campaign"];

export function RoiAnomaliesPage() {
  const { filters, refreshToken } = useFilters();
  const [groupBy, setGroupBy] = useSessionState<Dimension[]>("iaa.group.roi-anomalies", ["account"]);
  const [rule, setRule] = useSessionState("iaa.roi.rule", { roiBelow: 1.1, minCost: 200 });
  const [draft, setDraft] = useState({ roiPercent: String(Math.round(rule.roiBelow * 100)), minCost: String(rule.minCost) });

  const roiPercent = Number(draft.roiPercent);
  const minCost = Number(draft.minCost);
  const draftValid = roiPercent > 0 && roiPercent <= 1000 && minCost >= 0;

  const body: Schemas["RoiAnomalyQuery"] = { ...queryBase(filters), group_by: groupBy, roi_below: rule.roiBelow, min_cost: rule.minCost };
  const result = useQuery({
    queryKey: ["report", "roi-anomalies", body, refreshToken],
    queryFn: () => unwrap(api.POST("/api/v1/reports/roi-anomalies", { body })),
    placeholderData: keepPreviousData,
  });

  const columns: ReportColumn[] = [
    ...groupBy.map((dimension) => ({ key: dimension, label: DIMENSION_LABELS[dimension], kind: "dimension" as const })),
    { key: "cost", label: "消耗", kind: "base", unit: "money", precision: 2 },
    { key: "revenue", label: "预估收益", kind: "base", unit: "money", precision: 2 },
    { key: "roi", label: "ROI", kind: "derived", unit: "ratio", precision: 3 },
    { key: "reason", label: "原因", kind: "dimension" },
  ];
  const rows: Row[] = (result.data?.items ?? []).map((item) => ({
    ...item.dimensions,
    cost: item.cost,
    revenue: item.revenue,
    roi: item.roi,
    reason: item.reason,
  }));

  const applyRule = (event: FormEvent) => {
    event.preventDefault();
    if (draftValid) setRule({ roiBelow: roiPercent / 100, minCost });
  };

  return (
    <div className="space-y-4">
      <PageHeader title="ROI 异常清单" description="消耗达到门槛、ROI 低于阈值的对象，按消耗从高到低，优先处理花钱多的。" asOf={result.data?.data_as_of} />
      <FilterBar />
      <form onSubmit={applyRule} className="card flex flex-wrap items-end justify-between gap-3 p-3">
        <DimensionPicker available={DIMENSIONS} value={groupBy} onChange={setGroupBy} max={3} label="定位到" />
        <div className="flex flex-wrap items-end gap-2">
          <label className="grid gap-1 text-xs text-muted">
            ROI 低于（%）
            <input type="number" min={1} max={1000} step={1} className="control w-28" value={draft.roiPercent} onChange={(event) => setDraft({ ...draft, roiPercent: event.target.value })} />
          </label>
          <label className="grid gap-1 text-xs text-muted">
            消耗不低于
            <input type="number" min={0} step={50} className="control w-28" value={draft.minCost} onChange={(event) => setDraft({ ...draft, minCost: event.target.value })} />
          </label>
          <button type="submit" className="btn-primary" disabled={!draftValid}>
            应用规则
          </button>
        </div>
      </form>
      {result.isError ? (
        <ErrorBlock error={result.error} onRetry={() => void result.refetch()} />
      ) : (
        <DataGrid
          columns={columns}
          rows={rows}
          loading={result.isFetching}
          caption={
            result.data && (
              <span>
                规则：{result.data.rule}，命中 <b className="text-text">{result.data.items.length}</b> 个
              </span>
            )
          }
        />
      )}
    </div>
  );
}
