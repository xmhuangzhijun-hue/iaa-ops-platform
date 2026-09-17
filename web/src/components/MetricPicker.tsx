import * as Popover from "@radix-ui/react-popover";
import { Columns3 } from "lucide-react";
import { useMetricCatalog } from "../api/hooks";
import type { MetricKey } from "../lib/dimensions";

type Props = { visible: MetricKey[]; value: MetricKey[]; onChange: (value: MetricKey[]) => void; max?: number };

export function MetricPicker({ visible, value, onChange, max = 12 }: Props) {
  const catalog = useMetricCatalog();
  const items = (catalog.data?.items ?? []).filter((item) => visible.includes(item.key as MetricKey));
  const groups = [
    { title: "基础指标", items: items.filter((item) => item.kind === "base") },
    { title: "派生指标", items: items.filter((item) => item.kind === "derived") },
  ];

  const toggle = (key: MetricKey) => {
    if (value.includes(key)) {
      if (value.length > 1) onChange(value.filter((item) => item !== key));
    } else if (value.length < max) {
      // 按目录顺序保存，列顺序稳定。
      onChange(items.map((item) => item.key as MetricKey).filter((item) => item === key || value.includes(item)));
    }
  };

  return (
    <Popover.Root>
      <Popover.Trigger asChild>
        <button type="button" className="control">
          <Columns3 className="size-4" aria-hidden />
          指标列
          <span className="rounded-full bg-[var(--primary-soft)] px-1.5 text-xs font-semibold text-accent">{value.length}</span>
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="start" sideOffset={6} className="popover w-72 p-3">
          {groups.map((group) => (
            <fieldset key={group.title} className="mb-2 last:mb-0">
              <legend className="mb-1 text-xs font-semibold text-muted">{group.title}</legend>
              <div className="grid grid-cols-2 gap-x-2">
                {group.items.map((item) => (
                  <label key={item.key} className="flex cursor-pointer items-center gap-2 rounded-lg px-1.5 py-1 text-sm hover:bg-[var(--primary-soft)]" title={item.formula}>
                    <input
                      type="checkbox"
                      className="accent-[var(--accent)]"
                      checked={value.includes(item.key as MetricKey)}
                      onChange={() => toggle(item.key as MetricKey)}
                    />
                    {item.label}
                  </label>
                ))}
              </div>
            </fieldset>
          ))}
          <p className="mt-2 text-xs text-muted">列选择按账号保存；收益类指标仅对有真实口径权限的账号显示。</p>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
