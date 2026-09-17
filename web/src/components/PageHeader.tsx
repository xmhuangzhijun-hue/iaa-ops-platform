import type { ReactNode } from "react";
import { formatDateTime } from "../lib/format";

type Props = { title: string; description: string; asOf?: string | null; actions?: ReactNode };

export function PageHeader({ title, description, asOf, actions }: Props) {
  const updated = formatDateTime(asOf);
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
        <p className="mt-1 text-sm text-muted">
          {description}
          {updated && <span className="ml-2 whitespace-nowrap">数据更新于 {updated}</span>}
        </p>
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
