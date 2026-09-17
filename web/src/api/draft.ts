/**
 * 尚未实现的后端模块的契约草案。
 *
 * 界面先行：这里的类型与 src/mocks 的 handler 一一对应，是「由界面倒推出来的接口」。
 * 后端就位后，这些类型由 contracts/openapi.json 生成的 schema 取代，本文件删除。
 */

export type Compare = {
  /** 当前值 */
  value: number | null;
  /** 对比值（昨日 / 上周期） */
  compare: number | null;
  /** 差值 */
  diff: number | null;
  /** 环比，0.12 表示 +12% */
  ratio: number | null;
};

export type CampaignView = "delivery" | "revenue";

export type CampaignFilters = {
  keyword?: string;
  products?: string[];
  vendors?: string[];
  platforms?: string[];
  subPlatforms?: string[];
  landings?: string[];
  agencies?: string[];
  teams?: string[];
  convertRules?: string[];
  callbackRules?: string[];
  convertTargets?: string[];
  freshness?: string[];
  costTypes?: string[];
  strategies?: string[];
  playable?: string[];
  interaction?: string[];
  creativeTypes?: string[];
  creativeStyles?: string[];
  slots?: string[];
  enabled?: boolean | null;
  costMin?: number | null;
  costMax?: number | null;
  roiMin?: number | null;
  roiMax?: number | null;
};

export type CampaignSettings = {
  bid: number;
  budget: number | null;
  convertRule: string;
  callbackRule: string;
  callbackLevel: string;
  convertTarget: string;
  strategy: string;
  enabled: boolean;
};

export type CampaignProfile = {
  id: string;
  channelName: string;
  product: string;
  category: string;
  vendor: string;
  platform: string;
  subPlatform: string;
  advertiser: string;
  landing: string;
  remark: string;
  agency: string;
  team: string;
  freshness: string;
  costType: string;
  playable: string;
  interaction: string;
  creativeType: string;
  creativeStyle: string;
  slot: string;
  onlineDays: number;
  settings: CampaignSettings;
};

/** 投放视角的一行：推广档案 + 投放侧度量 */
export type CampaignDeliveryRow = CampaignProfile & {
  cost: number;
  costYoy: number | null;
  balance: number;
  quality: number;
  delivered: number;
  deliverCost: number | null;
  costFrame: number | null;
  exposure: number;
  clicks: number;
  cvr: number | null;
  ctr: number | null;
  cpm: number | null;
  cpc: number | null;
};

/** 收益与回传视角的一行 */
export type CampaignRevenueRow = CampaignProfile & {
  pmIncome: number;
  cpcIncome: number;
  totalRoi: Compare;
  cost: number;
  costYoy: number | null;
  costDiff: number | null;
  realCost: number;
  agentCost: number;
  payout: number;
  spark: number;
  balance: number;
  shouldCallback: number;
  callbacked: number;
  callbackArpu: number | null;
  convCallbackRatio: number | null;
  in5min: number | null;
  extraCallback: number;
};

export type Page<T> = { items: T[]; total: number; summary?: Record<string, number | null> };

export type TimelineRow = {
  bucket: string;
  cost: Compare;
  quality: Compare;
  ctr: Compare;
  cvr: Compare;
  cpm: Compare;
};

export type BulkRuleUpdate = {
  ids: string[];
  convertRule?: string;
  callbackRule?: string;
  callbackLevel?: string;
  bid?: number;
  budget?: number | null;
  enabled?: boolean;
};

export type BulkCallback = { ids: string[]; mode: "missing" | "all"; hours: number };

export type Product = {
  id: string;
  name: string;
  packageName: string;
  category: string;
  vendor: string;
  jumpProduct: string | null;
  status: "online" | "paused" | "offline";
  onlineAt: string;
  owner: string;
};

export type Category = { id: string; name: string; productCount: number; remark: string };

export type MediaAccount = {
  id: string;
  advertiser: string;
  platform: string;
  subPlatform: string;
  agency: string;
  owner: string;
  team: string;
  freshness: string;
  balance: number;
  dailyBudget: number | null;
  status: "active" | "paused" | "disabled";
  credentialRef: string | null;
  lastSyncAt: string | null;
};

export type IngestSource = {
  id: string;
  platform: string;
  kind: "delivery" | "revenue" | "callback";
  enabled: boolean;
  cron: string;
  credentialRef: string;
  authStatus: "ok" | "expiring" | "invalid";
  authExpireAt: string | null;
  lastRunAt: string | null;
  lastRunState: "succeeded" | "partial" | "failed" | "never";
  coveredUntil: string | null;
};

export type IngestRun = {
  id: string;
  sourceId: string;
  platform: string;
  kind: IngestSource["kind"];
  window: string;
  state: "running" | "succeeded" | "partial" | "failed";
  rows: number;
  errors: number;
  message: string | null;
  startedAt: string;
  durationMs: number | null;
};

export type FieldMapping = {
  id: string;
  platform: string;
  sourceField: string;
  targetField: string;
  targetLabel: string;
  transform: string | null;
  status: "mapped" | "unmapped" | "conflict";
  updatedAt: string;
};
