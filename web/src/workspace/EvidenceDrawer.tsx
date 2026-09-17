import { Drawer } from "antd";
import type { AgentEvent } from "../api/agent";

export function EvidenceDrawer({ event, onClose }: { event: AgentEvent | null; onClose: () => void }) {
  const result = event?.result;
  const rows = typeof result === "object" && result !== null && "rows" in result ? result.rows : null;
  return <Drawer title="查询来源与工具回执" open={!!event} onClose={onClose} size={560} destroyOnHidden>
    {event && <div className="evidence-detail">
      <dl><dt>来源编号</dt><dd>{event.id}</dd><dt>工具</dt><dd>{event.tool}</dd><dt>时间</dt><dd>{new Date(event.created_at).toLocaleString("zh-CN")}</dd><dt>状态</dt><dd>{event.status === "succeeded" ? "完成" : "失败"}</dd>{Array.isArray(rows) && <><dt>返回行数</dt><dd>{rows.length}</dd></>}</dl>
      <p>{event.summary}</p>
      <h3>查询参数</h3><pre>{JSON.stringify(event.arguments, null, 2)}</pre>
      <h3>真实返回结果</h3><pre>{JSON.stringify(event.result, null, 2)}</pre>
    </div>}
  </Drawer>;
}
