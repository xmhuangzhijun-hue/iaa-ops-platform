/**
 * 界面先行阶段的固定种子数据集。
 *
 * 只服务于尚未接后端的模块；接上真实接口后整个 mocks 目录删除。
 * 维度词表来自原型实测（21 个聚合维度），全部为虚构值。
 */

import type {
  CampaignDeliveryRow, CampaignProfile, CampaignRevenueRow, Category, Compare, FieldMapping,
  IngestRun, IngestSource, MediaAccount, Product, TimelineRow,
} from "../api/draft";

function mulberry32(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function rng(seed: number) {
  const rand = mulberry32(seed);
  return {
    rand,
    pick: <T>(list: readonly T[]): T => list[Math.floor(rand() * list.length)],
    int: (min: number, max: number) => Math.floor(min + rand() * (max - min)),
    num: (min: number, max: number, digits = 2) => Number((min + rand() * (max - min)).toFixed(digits)),
  };
}

import {
  AGENCIES, CALLBACK_LEVELS, CALLBACK_RULES, CATEGORIES, CONVERT_RULES, CONVERT_TARGETS, COST_TYPES,
  CREATIVE_STYLES, CREATIVE_TYPES, FRESHNESS, LANDINGS, OWNERS, PLATFORMS, PRODUCTS, SLOTS,
  STRATEGIES, SUB_PLATFORMS, TEAMS, VENDORS, YES_NO,
} from "../lib/vocabulary";

const PLAYABLE = YES_NO;
const INTERACTION = YES_NO;

const CAMPAIGN_COUNT = 160;

function compare(current: number | null, previous: number | null, digits = 2): Compare {
  if (current === null || previous === null) return { value: current, compare: previous, diff: null, ratio: null };
  const diff = Number((current - previous).toFixed(digits));
  return {
    value: current,
    compare: previous,
    diff,
    ratio: previous === 0 ? null : Number((diff / previous).toFixed(4)),
  };
}

function buildProfiles(): CampaignProfile[] {
  const r = rng(20260916);
  return Array.from({ length: CAMPAIGN_COUNT }, (_, index) => {
    const product = r.pick(PRODUCTS);
    const vendorIndex = index % VENDORS.length;
    const vendor = VENDORS[vendorIndex];
    return {
      id: String(690000 + index * 37 + r.int(0, 30)),
      channelName: `${r.pick(["北京", "上海", "厦门", "成都"])}${vendor}-${product}`,
      product,
      category: CATEGORIES[PRODUCTS.indexOf(product) % CATEGORIES.length],
      vendor,
      platform: PLATFORMS[vendorIndex],
      subPlatform: r.pick(SUB_PLATFORMS),
      advertiser: `adv-${vendor.toLowerCase()}-${String(r.int(1, 99)).padStart(3, "0")}`,
      landing: r.pick(LANDINGS),
      remark: `${r.pick(["roi", "拉新", "回收"])}-${r.pick(OWNERS)}`,
      agency: r.pick(AGENCIES),
      team: r.pick(TEAMS),
      freshness: r.pick(FRESHNESS),
      costType: r.pick(COST_TYPES),
      playable: r.pick(PLAYABLE),
      interaction: r.pick(INTERACTION),
      creativeType: r.pick(CREATIVE_TYPES),
      creativeStyle: r.pick(CREATIVE_STYLES),
      slot: r.pick(SLOTS),
      onlineDays: r.int(1, 60),
      settings: {
        bid: r.num(0.8, 3.5),
        budget: r.rand() > 0.3 ? r.int(2000, 30000) : null,
        convertRule: r.pick(CONVERT_RULES),
        callbackRule: r.pick(CALLBACK_RULES),
        callbackLevel: r.pick(CALLBACK_LEVELS),
        convertTarget: r.pick(CONVERT_TARGETS),
        strategy: r.pick(STRATEGIES),
        enabled: r.rand() > 0.18,
      },
    } satisfies CampaignProfile;
  });
}

export const profiles = buildProfiles();

export const deliveryRows: CampaignDeliveryRow[] = profiles.map((profile, index) => {
  const r = rng(1000 + index);
  const cost = r.num(300, 19000);
  const exposure = r.int(20000, 460000);
  const clicks = r.int(1200, Math.max(1300, Math.round(exposure * 0.12)));
  const delivered = r.int(60, Math.max(70, Math.round(clicks * 0.14)));
  return {
    ...profile,
    cost,
    costYoy: r.num(-0.8, 3.2, 4),
    balance: r.num(100, 96000),
    quality: r.num(52, 92),
    delivered,
    deliverCost: Number((cost / delivered).toFixed(2)),
    costFrame: r.num(0.6, 2.4, 3),
    exposure,
    clicks,
    cvr: Number((delivered / clicks).toFixed(4)),
    ctr: Number((clicks / exposure).toFixed(4)),
    cpm: Number(((cost / exposure) * 1000).toFixed(2)),
    cpc: Number((cost / clicks).toFixed(4)),
  };
});

export const revenueRows: CampaignRevenueRow[] = profiles.map((profile, index) => {
  const r = rng(5000 + index);
  const delivery = deliveryRows[index];
  const cost = delivery.cost;
  const roi = r.num(0.72, 1.35, 4);
  const pmIncome = Number((cost * roi * r.num(0.55, 0.75, 3)).toFixed(2));
  const cpcIncome = Number((cost * roi - pmIncome).toFixed(2));
  const shouldCallback = r.int(0, 40);
  const callbacked = Math.max(0, shouldCallback - r.int(0, 6));
  const hasArpu = r.rand() > 0.35;
  return {
    ...profile,
    pmIncome,
    cpcIncome,
    totalRoi: compare(roi, Number((roi * r.num(0.88, 1.12, 3)).toFixed(4)), 4),
    cost,
    costYoy: delivery.costYoy,
    costDiff: r.num(-9200, 3200),
    realCost: Number((cost * r.num(0.9, 0.99, 3)).toFixed(2)),
    agentCost: r.rand() > 0.85 ? r.num(100, 2000) : 0,
    payout: r.rand() > 0.9 ? r.num(50, 800) : 0,
    spark: r.rand() > 0.88 ? r.num(30, 600) : 0,
    balance: delivery.balance,
    shouldCallback,
    callbacked,
    callbackArpu: hasArpu ? r.num(0.08, 0.28, 3) : null,
    convCallbackRatio: shouldCallback ? Number((callbacked / shouldCallback).toFixed(4)) : null,
    in5min: hasArpu ? r.num(0.6, 1, 3) : null,
    extraCallback: r.int(0, 4),
  };
});

export function timeline(campaignId: string): TimelineRow[] {
  const r = rng(Number(campaignId.slice(-4)) || 7);
  const rows: TimelineRow[] = [];
  for (let hour = 23; hour >= 0; hour -= 1) {
    const cost = r.num(60, 900);
    const quality = r.num(55, 88);
    const ctr = r.num(0.05, 0.16, 4);
    const cvr = r.num(0.005, 0.03, 4);
    rows.push({
      bucket: `${String(hour).padStart(2, "0")}:00~${String(hour).padStart(2, "0")}:59`,
      cost: compare(cost, r.num(60, 900)),
      quality: compare(quality, r.num(55, 88)),
      ctr: compare(ctr, r.num(0.05, 0.16, 4), 4),
      cvr: compare(cvr, r.num(0.005, 0.03, 4), 4),
      cpm: compare(r.num(8, 20), r.num(8, 20)),
    });
  }
  return rows;
}

export const products: Product[] = PRODUCTS.map((name, index) => {
  const r = rng(300 + index);
  return {
    id: `prd_${String(index + 1).padStart(3, "0")}`,
    name,
    packageName: `com.demo.${name.length}${index}`,
    category: CATEGORIES[index % CATEGORIES.length],
    vendor: r.pick(VENDORS),
    jumpProduct: r.rand() > 0.7 ? PRODUCTS[(index + 3) % PRODUCTS.length] : null,
    status: r.rand() > 0.15 ? "online" : r.rand() > 0.5 ? "paused" : "offline",
    onlineAt: `2026-0${r.int(1, 9)}-${String(r.int(10, 28)).padStart(2, "0")}`,
    owner: r.pick(OWNERS),
  };
});

export const categories: Category[] = CATEGORIES.map((name, index) => ({
  id: `cat_${index + 1}`,
  name,
  productCount: products.filter((product) => product.category === name).length,
  remark: `${name}类快应用`,
}));

export const mediaAccounts: MediaAccount[] = Array.from({ length: 48 }, (_, index) => {
  const r = rng(900 + index);
  const platform = PLATFORMS[index % PLATFORMS.length];
  return {
    id: `acc_${String(index + 1).padStart(3, "0")}`,
    advertiser: `adv-${platform.slice(0, 4).toLowerCase()}-${String(index + 1).padStart(3, "0")}`,
    platform,
    subPlatform: r.pick(SUB_PLATFORMS),
    agency: r.pick(AGENCIES),
    owner: r.pick(OWNERS),
    team: r.pick(TEAMS),
    freshness: r.pick(FRESHNESS),
    balance: r.num(0, 86000),
    dailyBudget: r.rand() > 0.4 ? r.int(3000, 50000) : null,
    status: r.rand() > 0.12 ? "active" : r.rand() > 0.5 ? "paused" : "disabled",
    credentialRef: r.rand() > 0.2 ? `SEC-DEMO-${String(index + 1).padStart(3, "0")}` : null,
    lastSyncAt: r.rand() > 0.1 ? `2026-09-16 ${String(r.int(8, 18)).padStart(2, "0")}:${String(r.int(0, 59)).padStart(2, "0")}` : null,
  };
});

export const ingestSources: IngestSource[] = PLATFORMS.flatMap((platform, index) => {
  const kinds: IngestSource["kind"][] = ["delivery", "revenue", "callback"];
  return kinds.map((kind, kindIndex) => {
    const r = rng(2000 + index * 10 + kindIndex);
    const state = r.rand() > 0.2 ? "succeeded" : r.rand() > 0.5 ? "partial" : "failed";
    return {
      id: `src_${index + 1}_${kind}`,
      platform,
      kind,
      enabled: r.rand() > 0.12,
      cron: kind === "delivery" ? "每小时 :10" : kind === "revenue" ? "每小时 :25" : "每 10 分钟",
      credentialRef: `SEC-DEMO-${platform}-${kind}`,
      authStatus: r.rand() > 0.75 ? "expiring" : r.rand() > 0.92 ? "invalid" : "ok",
      authExpireAt: `2026-1${r.int(0, 2)}-${String(r.int(10, 28)).padStart(2, "0")}`,
      lastRunAt: `2026-09-16 ${String(r.int(8, 18)).padStart(2, "0")}:${String(r.int(10, 59)).padStart(2, "0")}`,
      lastRunState: state as IngestSource["lastRunState"],
      coveredUntil: `2026-09-16 ${String(r.int(8, 18)).padStart(2, "0")}:00`,
    } satisfies IngestSource;
  });
});

export const ingestRuns: IngestRun[] = Array.from({ length: 60 }, (_, index) => {
  const r = rng(4000 + index);
  const source = ingestSources[index % ingestSources.length];
  const state = r.rand() > 0.22 ? "succeeded" : r.rand() > 0.5 ? "partial" : "failed";
  return {
    id: `run_${String(index + 1).padStart(4, "0")}`,
    sourceId: source.id,
    platform: source.platform,
    kind: source.kind,
    window: `2026-09-16 ${String(r.int(0, 22)).padStart(2, "0")}:00 ~ +1h`,
    state: state as IngestRun["state"],
    rows: state === "failed" ? 0 : r.int(120, 9800),
    errors: state === "succeeded" ? 0 : r.int(1, 40),
    message: state === "succeeded" ? null : r.pick(["令牌过期，已跳过 3 个账户", "上游 429 限流，已重试 2 次", "字段 conversion_type 未映射"]),
    startedAt: `2026-09-16 ${String(r.int(0, 22)).padStart(2, "0")}:${String(r.int(0, 59)).padStart(2, "0")}`,
    durationMs: r.int(800, 42000),
  } satisfies IngestRun;
});

export const fieldMappings: FieldMapping[] = [
  ["cost", "消耗", "money"], ["show", "曝光", "int"], ["click", "点击", "int"],
  ["convert", "投放数", "int"], ["convert_cost", "投放成本", "money"], ["bid", "出价", "money"],
  ["quality_score", "质量分", "float"], ["income", "总收益", "money"], ["cpc_income", "CPC收益", "money"],
  ["pm_income", "PM收益", "money"], ["callback", "回传数", "int"], ["callback_arpu", "回传ARPU", "money"],
].flatMap(([source, label], index) =>
  PLATFORMS.slice(0, 3).map((platform, platformIndex) => {
    const r = rng(6000 + index * 5 + platformIndex);
    const status = r.rand() > 0.16 ? "mapped" : r.rand() > 0.5 ? "unmapped" : "conflict";
    return {
      id: `fm_${index}_${platformIndex}`,
      platform,
      sourceField: `${source}${platformIndex === 1 ? "_amount" : ""}`,
      targetField: source,
      targetLabel: label,
      transform: r.rand() > 0.8 ? "分转元" : null,
      status: status as FieldMapping["status"],
      updatedAt: `2026-09-1${r.int(0, 6)}`,
    } satisfies FieldMapping;
  }),
);
