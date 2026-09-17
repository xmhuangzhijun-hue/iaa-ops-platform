import type { CampaignChange } from "../api/agent";

const money = (value: number) => new Intl.NumberFormat("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);

export function ChangeDiff({ changes }: { changes: CampaignChange[] }) {
  return <div className="change-diff">{changes.map((change) => (
    <section key={change.campaign_id} className="change-file">
      <header><strong>{change.campaign_name}</strong><span>{change.account} · v{change.revision}{change.new_revision !== undefined ? ` → v${change.new_revision}` : ""}</span></header>
      <table aria-label={`${change.campaign_name} 修改前后`}>
        <thead><tr><th>字段</th><th>修改前</th><th>调整后</th></tr></thead>
        <tbody>{(["bid", "daily_budget"] as const).map((field) => {
          const changed = change.before[field] !== change.after[field];
          return <tr key={field} data-changed={changed}><th>{field === "bid" ? "出价 / 元" : "日预算 / 元"}</th><td className={changed ? "diff-before" : ""}>{money(change.before[field])}</td><td className={changed ? "diff-after" : ""}>{money(change.after[field])}</td></tr>;
        })}</tbody>
      </table>
    </section>
  ))}</div>;
}
