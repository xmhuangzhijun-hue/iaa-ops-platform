import * as Popover from "@radix-ui/react-popover";
import { ChevronDown } from "lucide-react";
import { useId, useMemo, useState } from "react";

type Option = { value: string; label: string };

type Props = {
  label: string;
  options: Option[];
  value: string[];
  onChange: (value: string[]) => void;
  loading?: boolean;
};

export function MultiSelect({ label, options, value, onChange, loading }: Props) {
  const [search, setSearch] = useState("");
  const searchId = useId();
  const selected = useMemo(() => new Set(value), [value]);
  const shown = options.filter((option) => option.label.toLowerCase().includes(search.trim().toLowerCase()));

  const toggle = (optionValue: string) =>
    onChange(selected.has(optionValue) ? value.filter((item) => item !== optionValue) : [...value, optionValue]);

  return (
    <Popover.Root onOpenChange={(open) => !open && setSearch("")}>
      <Popover.Trigger asChild>
        <button type="button" className="control">
          <span className="text-muted">{label}</span>
          {value.length > 0 ? (
            <span className="rounded-full bg-[var(--primary-soft)] px-1.5 text-xs font-semibold text-accent">{value.length}</span>
          ) : (
            <span>全部</span>
          )}
          <ChevronDown className="size-4 text-muted" aria-hidden />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="start" sideOffset={6} className="popover w-64 p-2">
          <label htmlFor={searchId} className="sr-only">
            搜索{label}
          </label>
          <input
            id={searchId}
            className="control mb-2 w-full"
            placeholder={`搜索${label}`}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <div className="mb-1 flex justify-between px-1 text-xs">
            <button type="button" className="text-accent" onClick={() => onChange([...new Set([...value, ...shown.map((o) => o.value)])])}>
              全选{search ? "匹配项" : ""}
            </button>
            <button type="button" className="text-muted" onClick={() => onChange([])}>
              清空
            </button>
          </div>
          <ul className="max-h-64 overflow-auto" aria-label={`${label}选项`}>
            {shown.map((option) => (
              <li key={option.value}>
                <label className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-[var(--primary-soft)]">
                  <input
                    type="checkbox"
                    className="accent-[var(--accent)]"
                    checked={selected.has(option.value)}
                    onChange={() => toggle(option.value)}
                  />
                  <span className="truncate">{option.label}</span>
                </label>
              </li>
            ))}
          </ul>
          {!shown.length && <p className="px-2 py-3 text-center text-xs text-muted">{loading ? "加载中…" : "没有可选项"}</p>}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
