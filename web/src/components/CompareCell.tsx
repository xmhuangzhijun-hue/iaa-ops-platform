import type { Compare } from "../api/draft";
import { formatMetric } from "../lib/format";

type Props = { data: Compare | undefined; unit?: "money" | "count" | "ratio" | "per_unit"; precision?: number };

/** 原型把「今日 | 昨日 | 差值 | 环比」拼成字符串；这里改为结构化数据分层展示。 */
export function CompareCell({ data, unit = "money", precision = 2 }: Props) {
  if (!data) return <span className="text-muted">—</span>;
  const rising = (data.ratio ?? 0) > 0;
  return (
    <span className="inline-flex flex-col items-end leading-tight">
      <span className="tabular-nums">{formatMetric(data.value, unit, precision)}</span>
      {data.ratio !== null && (
        <span className={`text-[11px] tabular-nums ${rising ? "text-good" : "text-bad"}`}>
          {rising ? "▲" : "▼"} {formatMetric(Math.abs(data.ratio), "ratio", 4)}
        </span>
      )}
    </span>
  );
}
