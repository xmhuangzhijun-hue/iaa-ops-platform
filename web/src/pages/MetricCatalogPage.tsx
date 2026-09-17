import { useMetricCatalog } from "../api/hooks";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";

const UNIT_LABELS = { money: "金额", count: "次数", ratio: "比率", per_unit: "单价" } as const;

export function MetricCatalogPage() {
  const catalog = useMetricCatalog();
  const groups = [
    { title: "基础指标", note: "媒体导出表中的原始值，汇总时求和。", items: catalog.data?.items.filter((item) => item.kind === "base") ?? [] },
    { title: "派生指标", note: "一律用汇总后的基础指标重算，分母为 0 时留空。", items: catalog.data?.items.filter((item) => item.kind === "derived") ?? [] },
  ];

  return (
    <div className="space-y-4">
      <PageHeader title="指标与字段说明" description="与后端计算共用同一份定义，页面、合计与导出口径一致。" />
      {catalog.isError && <ErrorBlock error={catalog.error} onRetry={() => void catalog.refetch()} />}
      {groups.map((group) => (
        <section key={group.title} className="card overflow-hidden">
          <header className="border-b border-line px-4 py-3">
            <h3 className="font-semibold">{group.title}</h3>
            <p className="text-xs text-muted">{group.note}</p>
          </header>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="text-left text-xs text-muted">
                <tr>
                  <th className="px-4 py-2 font-medium">指标</th>
                  <th className="px-4 py-2 font-medium">字段</th>
                  <th className="px-4 py-2 font-medium">公式</th>
                  <th className="px-4 py-2 font-medium">单位</th>
                  <th className="px-4 py-2 font-medium">说明</th>
                </tr>
              </thead>
              <tbody>
                {group.items.map((item) => (
                  <tr key={item.key} className="border-t border-line">
                    <td className="px-4 py-2.5 font-medium">{item.label}</td>
                    <td className="px-4 py-2.5 font-mono text-xs text-muted">{item.key}</td>
                    <td className="px-4 py-2.5 font-mono text-xs">{item.formula}</td>
                    <td className="px-4 py-2.5">{UNIT_LABELS[item.unit]}</td>
                    <td className="px-4 py-2.5 text-muted">{item.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}
    </div>
  );
}
