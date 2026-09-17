import { formatMetric } from "../lib/format";
import type { ReportColumn, Row } from "../lib/dimensions";

export function KpiRow({ columns, totals, limit = 6 }: { columns?: ReportColumn[]; totals?: Row; limit?: number }) {
  const metrics = (columns ?? []).filter((column) => column.kind !== "dimension").slice(0, limit);
  if (!metrics.length || !totals) return null;
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
      {metrics.map((column) => {
        const value = totals[column.key];
        const bad = column.key === "roi" && typeof value === "number" && value < 1;
        return (
          <div key={column.key} className="card px-4 py-3">
            <dt className="text-xs text-muted">{column.label}</dt>
            <dd className={`mt-1 text-lg font-semibold tabular-nums ${bad ? "text-bad" : ""}`}>
              {formatMetric(typeof value === "number" ? value : null, column.unit, column.precision)}
            </dd>
          </div>
        );
      })}
    </dl>
  );
}
