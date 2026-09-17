import { useQuery } from "@tanstack/react-query";
import { Database, FileCheck2, MessageSquare, RefreshCw, SlidersHorizontal } from "lucide-react";
import { useMemo, useState } from "react";
import { agentApi, type Campaign } from "../api/agent";
import { problemMessage } from "../api/client";
import { usePrincipal } from "../auth/AuthProvider";
import { queryBase, useFilters, type Filters } from "../filters/FilterProvider";
import { AgentDock } from "../workspace/AgentDock";
import { ChangeDiff } from "../workspace/ChangeDiff";
import { AggregatePage } from "./AggregatePage";
import { RawDetailPage } from "./RawDetailPage";
import "../workspace/workspace.css";

type Tab = "data" | "campaigns" | "receipts";
const TABS = [{ id: "data" as const, title: "原始数据", Icon: Database }, { id: "campaigns" as const, title: "计划操作", Icon: SlidersHorizontal }, { id: "receipts" as const, title: "操作回执", Icon: FileCheck2 }];
const money = (value: number) => new Intl.NumberFormat("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);

function matches(campaign: Campaign, filters: Filters) {
  const pairs: [string[], string | null][] = [[filters.media, campaign.media], [filters.accounts, campaign.account], [filters.products, campaign.product], [filters.agencies, campaign.agency], [filters.operators, campaign.operator]];
  return pairs.every(([values, value]) => !values.length || (value !== null && values.includes(value))) && (!filters.keyword || `${campaign.name} ${campaign.account} ${campaign.campaign}`.toLowerCase().includes(filters.keyword.toLowerCase()));
}

export function WorkspacePage() {
  const principal = usePrincipal();
  // 切换身份必须卸载任务/输入/来源面板，历史只从当前账号的接口读取。
  return <Workspace key={`${principal.tenant_id}:${principal.user_id}`} />;
}

function Workspace() {
  const principal = usePrincipal();
  const identity = `${principal.tenant_id}:${principal.user_id}`;
  const { filters } = useFilters();
  const [tab, setTab] = useState<Tab>("data");
  const [mobilePane, setMobilePane] = useState<"data" | "assistant">("data");
  const [selected, setSelected] = useState<string[]>([]);
  const realMetrics = principal.permissions.includes("*") || principal.permissions.includes("metrics.real");
  const capabilities = useQuery({ queryKey: ["agent", identity, "capabilities"], queryFn: ({ signal }) => agentApi.capabilities(signal), retry: false });
  const campaigns = useQuery({ queryKey: ["agent", identity, "campaigns"], queryFn: ({ signal }) => agentApi.campaigns(signal) });
  const receipts = useQuery({ queryKey: ["agent", identity, "receipts"], queryFn: ({ signal }) => agentApi.receipts(signal), enabled: tab === "receipts" });
  const visibleCampaigns = useMemo(() => (campaigns.data?.items ?? []).filter((campaign) => matches(campaign, filters)), [campaigns.data, filters]);
  const visibleSelection = selected.filter((id) => visibleCampaigns.some((campaign) => campaign.id === id));
  const allSelected = visibleCampaigns.length > 0 && visibleCampaigns.every((campaign) => visibleSelection.includes(campaign.id));
  const context = { ...queryBase(filters), keyword: filters.keyword, selected_campaign_ids: visibleSelection };

  return <div className="operations-workspace" data-mobile-pane={mobilePane}>
    <div className="workspace-mobile-switch" aria-label="工作区视图"><button type="button" aria-pressed={mobilePane === "data"} onClick={() => setMobilePane("data")}><Database size={15} /> 数据</button><button type="button" aria-pressed={mobilePane === "assistant"} onClick={() => setMobilePane("assistant")}><MessageSquare size={15} /> 助手</button></div>
    <section className="workspace-data" aria-label="运营数据工作区">
      <div className="workspace-tabs" aria-label="数据视图">{TABS.map(({ id, title, Icon }) => <button key={id} type="button" aria-pressed={tab === id} onClick={() => setTab(id)}><Icon size={15} aria-hidden /><span>{title}</span>{id === "campaigns" && visibleSelection.length > 0 && <small>{visibleSelection.length}</small>}</button>)}<span className="workspace-mode">SANDBOX</span></div>
      <div className="workspace-data-content">
        {tab === "data" && (realMetrics ? <RawDetailPage embedded /> : <><div className="workspace-scope-note">当前账号使用对外口径，仅查询已授权指标；数据按所选维度汇总。</div><AggregatePage embedded /></>)}
        {tab === "campaigns" && <>
          <div className="workspace-data-toolbar"><div><strong>计划配置</strong><span> · {visibleCampaigns.length} 个可见计划</span></div><button type="button" className="control" onClick={() => void campaigns.refetch()} disabled={campaigns.isFetching}><RefreshCw size={14} /> 刷新</button></div>
          <p className="workspace-scope-note">{capabilities.data?.can_execute === false ? "当前账号仅可查看本机模拟计划，可以选中计划向助手提问，没有配置调整权限。" : "勾选计划后，向右侧助手描述要调整的出价或日预算。配置来自本机模拟环境，修改前需要你批准。"}</p>
          {campaigns.isError ? <div className="workspace-state" role="alert">{problemMessage(campaigns.error)}</div> : campaigns.isPending ? <div className="workspace-state" role="status">正在读取计划配置…</div> : <div className="campaign-table-wrap"><table className="campaign-table"><thead><tr><th><input type="checkbox" aria-label="选择当前筛选的全部计划" checked={allSelected} disabled={!visibleCampaigns.length} onChange={(event) => setSelected(event.target.checked ? visibleCampaigns.map((campaign) => campaign.id) : [])} /></th><th>计划 / 账户</th><th>媒体</th><th>出价 / 元</th><th>日预算 / 元</th><th>版本</th></tr></thead><tbody>{visibleCampaigns.map((campaign) => <tr key={campaign.id} data-selected={visibleSelection.includes(campaign.id)}><td><input type="checkbox" aria-label={`选择 ${campaign.name}`} checked={visibleSelection.includes(campaign.id)} onChange={(event) => setSelected((current) => event.target.checked ? [...new Set([...current, campaign.id])] : current.filter((id) => id !== campaign.id))} /></td><th><span>{campaign.name}</span><small>{campaign.account}</small></th><td>{campaign.media}</td><td>{money(campaign.bid)}</td><td>{money(campaign.daily_budget)}</td><td><code>v{campaign.revision}</code></td></tr>)}</tbody></table>{!visibleCampaigns.length && <div className="workspace-state">当前权限或筛选范围没有计划。可回到原始数据调整筛选。</div>}</div>}
          <div className="workspace-table-footer"><span>已选 {visibleSelection.length} 个计划 · 随下一条指令发送</span>{visibleSelection.length > 0 && <button type="button" onClick={() => setSelected([])}>清除选择</button>}</div>
        </>}
        {tab === "receipts" && <>
          <div className="workspace-data-toolbar"><strong>操作回执</strong><button type="button" className="control" onClick={() => void receipts.refetch()} disabled={receipts.isFetching}><RefreshCw size={14} /> 刷新</button></div>
          <p className="workspace-scope-note">只记录本账号确认后完成的本地配置修改。此处的执行成功不代表媒体平台发生变化。</p>
          {receipts.isError ? <div className="workspace-state" role="alert">{problemMessage(receipts.error)}</div> : receipts.isPending ? <div className="workspace-state">正在读取回执…</div> : !receipts.data?.items.length ? <div className="workspace-state"><FileCheck2 size={26} /><strong>还没有执行回执</strong><p>助手提出调整，你确认执行后，修改前后值和版本会显示在这里。</p></div> : <div className="receipt-list">{receipts.data.items.map((receipt) => <article className="receipt-item" key={receipt.id}><header><strong>本地模拟执行完成</strong><time>{new Date(receipt.created_at).toLocaleString("zh-CN")}</time></header><ChangeDiff changes={receipt.changes} /><footer><span>回执</span><code>{receipt.id}</code></footer></article>)}</div>}
        </>}
      </div>
      <footer className="workspace-statusbar"><span><span className="workspace-status-dot" />虚构数据 · 本地模拟执行</span><span>{realMetrics ? "真实收益口径" : "对外可见口径"} · {principal.display_name}</span></footer>
    </section>
    <AgentDock identity={identity} context={context} capabilities={capabilities.data} capabilitiesError={capabilities.isError ? capabilities.error : undefined} retryCapabilities={() => void capabilities.refetch()} />
  </div>;
}
