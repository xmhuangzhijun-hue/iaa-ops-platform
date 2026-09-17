export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function addDays(iso: string, days: number): string {
  const [year, month, day] = iso.split("-").map(Number);
  return isoDate(new Date(year, month - 1, day + days));
}

export function daysBetween(from: string, to: string): number {
  const toUtc = (iso: string) => {
    const [year, month, day] = iso.split("-").map(Number);
    return Date.UTC(year, month - 1, day);
  };
  return Math.round((toUtc(to) - toUtc(from)) / 86_400_000);
}

/** 以昨天为结束日的最近 n 天；投放数据通常次日才完整。 */
export function lastDays(count: number): { dateFrom: string; dateTo: string } {
  const yesterday = addDays(isoDate(new Date()), -1);
  return { dateFrom: addDays(yesterday, -(count - 1)), dateTo: yesterday };
}

export const QUICK_RANGES = [
  { label: "昨天", days: 1 },
  { label: "近 7 天", days: 7 },
  { label: "近 14 天", days: 14 },
  { label: "近 30 天", days: 30 },
] as const;
