import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowUp, Check, ChevronRight, Circle, CircleCheck, History, LoaderCircle, Plus, SquareTerminal, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { agentApi, type AgentCapabilities, type AgentContext, type AgentEvent, type AgentRun } from "../api/agent";
import { ApiProblem, problemMessage } from "../api/client";
import { ChangeDiff } from "./ChangeDiff";
import { AnswerMarkdown } from "./AnswerMarkdown";
import { EvidenceDrawer } from "./EvidenceDrawer";

const LABELS: Record<AgentRun["status"], string> = { running: "运行中", awaiting_confirmation: "等待你确认", succeeded: "已完成", rejected: "已拒绝", failed: "运行失败" };
const inaccessible = (error: unknown) => error instanceof ApiProblem && (error.status === 401 || error.status === 403 || error.status === 404);

export function AgentDock({ identity, context, capabilities, capabilitiesError, retryCapabilities }: {
  identity: string;
  context: AgentContext;
  capabilities?: AgentCapabilities;
  capabilitiesError?: unknown;
  retryCapabilities: () => void;
}) {
  const queryClient = useQueryClient();
  const [prompt, setPrompt] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [evidence, setEvidence] = useState<AgentEvent | null>(null);
  const decisionIds = useRef(new Map<string, string>());
  const submission = useRef<{ fingerprint: string; request_id: string } | null>(null);
  const history = useQuery({ queryKey: ["agent", identity, "runs"], queryFn: ({ signal }) => agentApi.runs(signal) });
  const runQuery = useQuery({
    queryKey: ["agent", identity, "run", selectedId],
    queryFn: ({ signal }) => agentApi.run(selectedId!, signal),
    enabled: !!selectedId,
    refetchInterval: (query) => !inaccessible(query.state.error) && query.state.data?.status === "running" ? 2000 : false,
    retry: false,
  });
  // 撤权或指纹改变时，React Query 仍可能保留上次 data；禁止在错误旁继续展示。
  const accessLost = inaccessible(runQuery.error);
  const run = accessLost ? undefined : runQuery.data;
  useEffect(() => { if (accessLost) setEvidence(null); }, [accessLost]);
  useEffect(() => setEvidence(null), [selectedId]);
  const onRun = (next: AgentRun) => {
    queryClient.setQueryData(["agent", identity, "run", next.id], next);
    setSelectedId(next.id);
    void queryClient.invalidateQueries({ queryKey: ["agent", identity, "runs"] });
    if (next.receipt) {
      void queryClient.invalidateQueries({ queryKey: ["agent", identity, "campaigns"] });
      void queryClient.invalidateQueries({ queryKey: ["agent", identity, "receipts"] });
    }
  };
  const create = useMutation({ mutationFn: agentApi.create, onSuccess: (next) => { onRun(next); setPrompt(""); submission.current = null; }, retry: false });
  const decision = useMutation({
    mutationFn: ({ id, approve, proposal_id, request_id }: { id: string; approve: boolean; proposal_id: string; request_id: string }) => agentApi.decide(id, { approve, proposal_id, request_id }),
    onSuccess: onRun,
    onError: () => { void runQuery.refetch(); },
    retry: false,
  });
  const send = (event?: FormEvent) => {
    event?.preventDefault();
    if (!prompt.trim() || create.isPending || run?.status === "running" || !capabilities?.available) return;
    const body = { prompt: prompt.trim(), context };
    const fingerprint = JSON.stringify(body);
    if (submission.current?.fingerprint !== fingerprint) submission.current = { fingerprint, request_id: crypto.randomUUID() };
    create.mutate({ ...body, request_id: submission.current.request_id });
  };
  const decide = (approve: boolean) => {
    if (!run?.proposal || decision.isPending) return;
    const key = `${run.proposal.id}:${approve}`;
    if (!decisionIds.current.has(key)) decisionIds.current.set(key, crypto.randomUUID());
    decision.mutate({ id: run.id, approve, proposal_id: run.proposal.id, request_id: decisionIds.current.get(key)! });
  };
  const newTask = () => { setSelectedId(null); setHistoryOpen(false); create.reset(); decision.reset(); };
  const pending = run?.proposal?.status === "pending" && run.status === "awaiting_confirmation";
  const expired = !!run?.proposal && new Date(run.proposal.expires_at).getTime() <= Date.now();

  return <aside className="agent-dock" aria-label="运营助手">
    <header className="agent-dock-header"><SquareTerminal size={17} aria-hidden /><h2>运营助手</h2><span className="agent-dock-model" title={capabilities?.model ?? "模型未配置"}>{capabilities?.model ?? "未配置"}</span><button type="button" className="workspace-icon-button" onClick={() => { setHistoryOpen(!historyOpen); if (!historyOpen) void history.refetch(); }} aria-label="任务历史" aria-expanded={historyOpen}><History size={16} /></button><button type="button" className="workspace-icon-button" onClick={newTask} aria-label="新任务"><Plus size={17} /></button></header>
    {historyOpen && <div className="agent-history"><div className="agent-section-label">当前账号 · 最近任务</div>{history.isError ? <p role="alert">{problemMessage(history.error)}</p> : history.isPending ? <p>正在读取…</p> : !history.data?.items.length ? <p>还没有任务</p> : history.data.items.map((item) => <button key={item.id} type="button" onClick={() => { setSelectedId(item.id); setHistoryOpen(false); decision.reset(); create.reset(); }} aria-current={selectedId === item.id ? "true" : undefined}><span>{item.prompt}</span><small>{LABELS[item.status]} · {new Date(item.created_at).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })}</small></button>)}</div>}
    <div className="agent-run-content">
      {capabilitiesError ? <div className="agent-notice" role="alert"><strong>无法连接助手</strong><p>{problemMessage(capabilitiesError)}</p><button className="control" type="button" onClick={retryCapabilities}>重试连接</button></div> : capabilities && !capabilities.available && <div className="agent-notice"><strong>模型尚未就绪</strong><p>{capabilities.unavailable_reason ?? "请在服务端配置模型，再刷新连接。数据浏览仍可使用。"}</p><button className="control" type="button" onClick={retryCapabilities}>重新检查</button></div>}
      {!selectedId && <div className="agent-empty"><p className="agent-section-label">数据由你观察，行动由你决定</p><h3>先问数据，再决定怎么改。</h3><p>{capabilities?.can_execute === false ? "我可以查询当前范围、解释变化并提供建议。当前账号没有配置调整权限。" : "我可以查询当前范围、解释变化，也可以准备出价和预算调整。每次修改都会先给你审阅。"}</p><div className="agent-prompts">{["查看当前日期范围的消耗，找出值得关注的账户。", "列出可见计划的出价和日预算，先不要修改。"].map((text) => <button type="button" key={text} onClick={() => setPrompt(text)}><span>{text}</span><ChevronRight size={14} /></button>)}</div><small>每次新任务独立运行，已选计划和当前筛选会随问题发送。</small></div>}
      {selectedId && runQuery.isPending && <p className="agent-loading"><LoaderCircle size={16} className="animate-spin" /> 正在读取任务</p>}
      {runQuery.isError && <div className="agent-notice" role="alert"><p>{problemMessage(runQuery.error)}</p><button type="button" className="control" onClick={() => void runQuery.refetch()}>重新读取</button></div>}
      {run && <>
        <div className="agent-user-message"><span className="agent-section-label">你的指令</span><p>{run.prompt}</p></div>
        <div className="agent-run-status" role="status">{run.status === "running" ? <LoaderCircle size={14} className="animate-spin" /> : run.status === "failed" ? <X size={14} /> : <CircleCheck size={14} />}<span>{LABELS[run.status]}</span><time>{new Date(run.created_at).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}</time></div>
        {run.events.length > 0 && <div className="agent-events" aria-label="真实运行记录">{run.events.map((item) => <button className="agent-event" type="button" key={item.id} onClick={() => setEvidence(item)}><span className="agent-event-index">{item.id}</span>{item.status === "succeeded" ? <Check size={13} /> : <X size={13} />}<span><strong>{item.tool}</strong><small>{item.summary}</small></span><ChevronRight size={13} /></button>)}</div>}
        {run.status === "running" && <p className="agent-running-note">后台任务正在运行，最长约 3 分钟。可离开页面，稍后从历史回看。</p>}
        {run.answer && <div className="agent-answer"><span className="agent-section-label">助手结果</span><AnswerMarkdown>{run.answer}</AnswerMarkdown>{run.events.length > 0 && <div className="agent-evidence-links">{run.events.map((item) => <button type="button" key={item.id} onClick={() => setEvidence(item)} title={item.id}>[{item.id}] 查看来源</button>)}</div>}</div>}
        {run.error && <p className="agent-run-error" role="alert">{run.error}</p>}
        {run.proposal && <section className="agent-proposal" aria-label="待审阅调整"><div className="agent-proposal-heading"><strong>{pending ? "审阅调整" : run.proposal.status === "rejected" ? "调整已拒绝" : "调整记录"}</strong><span>仅本地模拟</span></div><p>{run.proposal.summary}</p><ChangeDiff changes={run.proposal.changes} /><p className="agent-proposal-boundary">确认后只修改本机虚构计划配置，不会发送至媒体平台。</p>{pending && <><small>{expired ? "提案已过期，请创建新任务重新获取当前配置。" : `有效至 ${new Date(run.proposal.expires_at).toLocaleString("zh-CN")}`}</small><div className="agent-decision"><button type="button" className="control" disabled={decision.isPending} onClick={() => decide(false)}>拒绝调整</button><button type="button" className="btn-primary" disabled={decision.isPending || expired || !capabilities?.can_execute} onClick={() => decide(true)}>{decision.isPending ? "提交中…" : "批准并模拟执行"}</button></div>{!capabilities?.can_execute && <small>当前账号可查看建议，没有执行权限。</small>}</>}</section>}
        {run.receipt && <section className="agent-receipt" role="status"><CircleCheck size={16} /><div><strong>本地模拟执行完成</strong><p>{run.receipt.changes.length} 个计划已更新。可在「操作回执」核对修改。</p><code>{run.receipt.id}</code></div></section>}
      </>}
      {(create.isError || decision.isError) && <p className="agent-run-error" role="alert">{problemMessage(create.error ?? decision.error)}。可先从历史确认任务状态。</p>}
    </div>
    <form className="agent-composer" onSubmit={send}>
      <div className="agent-context"><Circle size={7} fill="currentColor" /><span>{context.date_from} → {context.date_to}</span>{context.keyword && <span>· 关键词：{context.keyword}</span>}{!!context.selected_campaign_ids?.length && <span>· 已选 {context.selected_campaign_ids.length} 个计划</span>}</div>
      <label className="sr-only" htmlFor="agent-prompt">给运营助手的指令</label><textarea id="agent-prompt" value={prompt} onChange={(event) => setPrompt(event.target.value)} maxLength={4000} rows={3} placeholder="询问数据，或明确描述要调整的计划…" onKeyDown={(event) => { if ((event.ctrlKey || event.metaKey) && event.key === "Enter") { event.preventDefault(); send(); } }} />
      <div className="agent-composer-footer"><span>{create.isPending ? "正在创建任务…" : "Ctrl / ⌘ + Enter 发送"}</span><button type="submit" className="agent-send" aria-label="发送指令" disabled={!prompt.trim() || !capabilities?.available || create.isPending || run?.status === "running"}><ArrowUp size={17} /></button></div>
    </form>
    <EvidenceDrawer event={accessLost ? null : evidence} onClose={() => setEvidence(null)} />
  </aside>;
}
