import { api, unwrap, type Schemas } from "./client";

// Agent DTO 与路径来自生成契约。浏览器只调用业务 API，不持有模型凭据。
export type AgentCapabilities = Schemas["AgentCapabilities"];
export type Campaign = Schemas["AgentCampaign"];
export type AgentEvent = Schemas["AgentEvent"];
export type AgentProposal = Schemas["AgentProposal"];
export type AgentReceipt = Schemas["AgentReceipt"];
export type AgentRun = Schemas["AgentRun"];
export type CampaignChange = Schemas["AgentChange"] & Partial<Pick<Schemas["AgentAppliedChange"], "new_revision">>;
export type AgentContext = Partial<Pick<Schemas["ReportQuery"], "date_from" | "date_to" | "filters" | "keyword">> & { selected_campaign_ids?: string[] };

export const agentApi = {
  capabilities: (signal?: AbortSignal) => unwrap(api.GET("/api/v1/agent/capabilities", { signal })),
  campaigns: (signal?: AbortSignal) => unwrap(api.GET("/api/v1/agent/campaigns", { signal })),
  runs: (signal?: AbortSignal) => unwrap(api.GET("/api/v1/agent/runs", { signal })),
  run: (id: string, signal?: AbortSignal) => unwrap(api.GET("/api/v1/agent/runs/{run_id}", { params: { path: { run_id: id } }, signal })),
  create: (body: Schemas["AgentSubmit"]) => unwrap(api.POST("/api/v1/agent/runs", { body })),
  decide: (id: string, body: Schemas["AgentDecision"]) => unwrap(api.POST("/api/v1/agent/runs/{run_id}/decision", { params: { path: { run_id: id } }, body })),
  receipts: (signal?: AbortSignal) => unwrap(api.GET("/api/v1/agent/receipts", { signal })),
};
