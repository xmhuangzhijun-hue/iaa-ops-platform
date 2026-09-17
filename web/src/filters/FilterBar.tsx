import { RefreshCw, RotateCcw, Search } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useFilterOptions } from "../api/hooks";
import { MultiSelect } from "../components/MultiSelect";
import { lastDays, QUICK_RANGES } from "../lib/dates";
import { defaultFilters, useFilters, type Filters } from "./FilterProvider";

type SelectKey = "media" | "products" | "agencies" | "accounts" | "operators";
const SELECTS: { key: SelectKey; label: string }[] = [
  { key: "media", label: "媒体" },
  { key: "products", label: "产品" },
  { key: "agencies", label: "代理" },
  { key: "accounts", label: "账户" },
  { key: "operators", label: "运营" },
];

export function FilterBar({ keywordPlaceholder = "搜索维度值" }: { keywordPlaceholder?: string }) {
  const { filters, apply, refresh } = useFilters();
  const [draft, setDraft] = useState<Filters>(filters);
  useEffect(() => setDraft(filters), [filters]);

  const options = useFilterOptions(draft.dateFrom, draft.dateTo);
  const invalidRange = !draft.dateFrom || !draft.dateTo || draft.dateTo < draft.dateFrom;
  const dirty = JSON.stringify(draft) !== JSON.stringify(filters);
  const set = (patch: Partial<Filters>) => setDraft((current) => ({ ...current, ...patch }));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!invalidRange) apply({ ...draft, keyword: draft.keyword.trim() });
  };

  return (
    <form onSubmit={submit} className="card flex flex-wrap items-end gap-x-3 gap-y-2.5 p-3" aria-label="筛选条件">
      <fieldset className="flex flex-wrap items-end gap-2">
        <legend className="sr-only">日期范围</legend>
        <label className="grid gap-1 text-xs text-muted">
          开始日期
          <input type="date" className="control" value={draft.dateFrom} max={draft.dateTo} onChange={(event) => set({ dateFrom: event.target.value })} />
        </label>
        <label className="grid gap-1 text-xs text-muted">
          结束日期
          <input type="date" className="control" value={draft.dateTo} min={draft.dateFrom} onChange={(event) => set({ dateTo: event.target.value })} />
        </label>
        <div className="flex gap-1">
          {QUICK_RANGES.map((range) => {
            const target = lastDays(range.days);
            const active = draft.dateFrom === target.dateFrom && draft.dateTo === target.dateTo;
            return (
              <button key={range.label} type="button" className="chip" aria-pressed={active} onClick={() => set(target)}>
                {range.label}
              </button>
            );
          })}
        </div>
      </fieldset>

      <div className="flex flex-wrap items-end gap-2">
        {SELECTS.map(({ key, label }) => (
          <MultiSelect
            key={key}
            label={label}
            options={options.data?.[key] ?? []}
            loading={options.isLoading}
            value={draft[key]}
            onChange={(value) => set({ [key]: value } as Partial<Filters>)}
          />
        ))}
      </div>

      <label className="relative min-w-40 flex-1">
        <span className="sr-only">关键词</span>
        <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted" aria-hidden />
        <input
          className="control w-full pl-8"
          value={draft.keyword}
          maxLength={100}
          placeholder={keywordPlaceholder}
          onChange={(event) => set({ keyword: event.target.value })}
        />
      </label>

      <div className="ml-auto flex items-center gap-2">
        {invalidRange && <span className="text-xs text-bad">结束日期不能早于开始日期</span>}
        <button type="button" className="control" onClick={() => setDraft(defaultFilters())} title="恢复默认筛选">
          <RotateCcw className="size-4" aria-hidden />
          重置
        </button>
        <button type="button" className="control" onClick={refresh} title="按当前条件重新读取数据">
          <RefreshCw className="size-4" aria-hidden />
          刷新
        </button>
        <button type="submit" className="btn-primary" disabled={invalidRange}>
          查询
          {dirty && <span className="size-1.5 rounded-full bg-[var(--on-primary)]" aria-label="有未生效的修改" />}
        </button>
      </div>
    </form>
  );
}
