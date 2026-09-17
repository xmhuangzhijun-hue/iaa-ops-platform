type Unit = "money" | "count" | "ratio" | "per_unit" | null | undefined;

const numberFormatters = new Map<number, Intl.NumberFormat>();

function fixed(digits: number): Intl.NumberFormat {
  let formatter = numberFormatters.get(digits);
  if (!formatter) {
    formatter = new Intl.NumberFormat("zh-CN", { minimumFractionDigits: digits, maximumFractionDigits: digits });
    numberFormatters.set(digits, formatter);
  }
  return formatter;
}

/** 按指标单位与精度展示；空值显示破折号，不显示 0。 */
export function formatMetric(value: number | null | undefined, unit: Unit, precision?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  switch (unit) {
    case "ratio":
      return `${fixed(Math.max((precision ?? 4) - 2, 1)).format(value * 100)}%`;
    case "money":
      return fixed(2).format(value);
    case "count":
      return fixed(0).format(value);
    default:
      return fixed(precision ?? 2).format(value);
  }
}

export function formatDateTime(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
