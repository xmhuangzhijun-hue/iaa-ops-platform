/**
 * 界面先行阶段的接口 mock。
 *
 * 在 fetch 层拦截（不用 Service Worker：内嵌浏览器等环境会拒绝注册 SW）。
 * 只处理尚未实现的路径，其余请求原样打到真实后端。
 * 后端就位后删除整个 mocks 目录，页面代码不动。
 */

import type { BulkCallback, BulkRuleUpdate, CampaignProfile } from "../api/draft";
import {
  categories, deliveryRows, fieldMappings, ingestRuns, ingestSources, mediaAccounts, products,
  profiles, revenueRows, timeline,
} from "./dataset";

const LATENCY = 220;

type Handler = (request: Request, url: URL, params: string[]) => Promise<unknown> | unknown;
type Route = { method: string; pattern: RegExp; handler: Handler };

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });

const delay = () => new Promise((resolve) => setTimeout(resolve, LATENCY));

function list(url: URL, key: string): string[] {
  return url.searchParams.getAll(key).filter(Boolean);
}

function paginate<T>(items: T[], url: URL) {
  const page = Number(url.searchParams.get("page") ?? 1);
  const pageSize = Number(url.searchParams.get("pageSize") ?? 50);
  return { items: items.slice((page - 1) * pageSize, page * pageSize), total: items.length };
}

function matches(row: CampaignProfile, url: URL): boolean {
  const checks: [string, string][] = [
    ["products", row.product], ["vendors", row.vendor], ["platforms", row.platform],
    ["subPlatforms", row.subPlatform], ["landings", row.landing], ["agencies", row.agency],
    ["teams", row.team], ["convertRules", row.settings.convertRule], ["callbackRules", row.settings.callbackRule],
    ["convertTargets", row.settings.convertTarget], ["freshness", row.freshness], ["costTypes", row.costType],
    ["strategies", row.settings.strategy], ["playable", row.playable], ["interaction", row.interaction],
    ["creativeTypes", row.creativeType], ["creativeStyles", row.creativeStyle], ["slots", row.slot],
  ];
  for (const [key, value] of checks) {
    const selected = list(url, key);
    if (selected.length && !selected.includes(value)) return false;
  }
  const enabled = url.searchParams.get("enabled");
  if (enabled === "true" && !row.settings.enabled) return false;
  if (enabled === "false" && row.settings.enabled) return false;

  const keyword = (url.searchParams.get("keyword") ?? "").trim().toLowerCase();
  if (keyword) {
    const haystack = `${row.id}${row.channelName}${row.product}${row.advertiser}${row.remark}`.toLowerCase();
    if (!haystack.includes(keyword)) return false;
  }
  return true;
}

function withinRange(value: number, min: string | null, max: string | null): boolean {
  if (min && value < Number(min)) return false;
  if (max && value > Number(max)) return false;
  return true;
}

function sortRows<T extends Record<string, unknown>>(rows: T[], url: URL): T[] {
  const field = url.searchParams.get("sortField");
  if (!field) return rows;
  const desc = url.searchParams.get("sortOrder") !== "asc";
  return [...rows].sort((a, b) => {
    const left = a[field];
    const right = b[field];
    if (left === null || left === undefined) return 1;
    if (right === null || right === undefined) return -1;
    if (typeof left === "number" && typeof right === "number") return desc ? right - left : left - right;
    return desc ? String(right).localeCompare(String(left)) : String(left).localeCompare(String(right));
  });
}

const routes: Route[] = [
  {
    method: "GET",
    pattern: /^\/api\/v1\/campaigns$/,
    handler: async (_request, url) => {
      const view = url.searchParams.get("view") ?? "delivery";
      const source = view === "revenue" ? revenueRows : deliveryRows;
      const filtered = source.filter(
        (row) => matches(row, url) && withinRange(row.cost, url.searchParams.get("costMin"), url.searchParams.get("costMax")),
      );
      const sorted = sortRows(filtered as unknown as Record<string, unknown>[], url);
      await delay();
      return {
        ...paginate(sorted, url),
        summary: {
          cost: Number(filtered.reduce((total, row) => total + row.cost, 0).toFixed(2)),
          campaigns: filtered.length,
          enabled: filtered.filter((row) => row.settings.enabled).length,
        },
      };
    },
  },
  {
    method: "GET",
    pattern: /^\/api\/v1\/campaigns\/([^/]+)\/timeline$/,
    handler: (_request, _url, params) => ({ items: timeline(params[0]) }),
  },
  {
    method: "POST",
    pattern: /^\/api\/v1\/campaigns\/bulk-rules$/,
    handler: async (request) => {
      const body = (await request.json()) as BulkRuleUpdate;
      const targets = new Set(body.ids);
      for (const profile of profiles) {
        if (!targets.has(profile.id)) continue;
        if (body.convertRule) profile.settings.convertRule = body.convertRule;
        if (body.callbackRule) profile.settings.callbackRule = body.callbackRule;
        if (body.callbackLevel) profile.settings.callbackLevel = body.callbackLevel;
        if (body.bid !== undefined) profile.settings.bid = body.bid;
        if (body.budget !== undefined) profile.settings.budget = body.budget;
        if (body.enabled !== undefined) profile.settings.enabled = body.enabled;
      }
      await delay();
      return { updated: body.ids.length };
    },
  },
  {
    method: "POST",
    pattern: /^\/api\/v1\/campaigns\/bulk-callback$/,
    handler: async (request) => {
      const body = (await request.json()) as BulkCallback;
      await delay();
      return {
        accepted: body.ids.length,
        estimated: body.ids.length * (body.mode === "all" ? 12 : 3),
        taskId: `cb_${Date.now()}`,
      };
    },
  },
  {
    method: "GET",
    pattern: /^\/api\/v1\/products$/,
    handler: (_request, url) => {
      const keyword = (url.searchParams.get("keyword") ?? "").toLowerCase();
      return paginate(
        products.filter((product) => !keyword || `${product.name}${product.packageName}`.toLowerCase().includes(keyword)),
        url,
      );
    },
  },
  {
    method: "GET",
    pattern: /^\/api\/v1\/categories$/,
    handler: () => ({ items: categories, total: categories.length }),
  },
  {
    method: "GET",
    pattern: /^\/api\/v1\/media-accounts$/,
    handler: (_request, url) => {
      const platforms = list(url, "platforms");
      const keyword = (url.searchParams.get("keyword") ?? "").toLowerCase();
      return paginate(
        mediaAccounts.filter(
          (account) =>
            (!platforms.length || platforms.includes(account.platform)) &&
            (!keyword || account.advertiser.toLowerCase().includes(keyword)),
        ),
        url,
      );
    },
  },
  {
    method: "GET",
    pattern: /^\/api\/v1\/ingest\/sources$/,
    handler: () => ({ items: ingestSources, total: ingestSources.length }),
  },
  {
    method: "PATCH",
    pattern: /^\/api\/v1\/ingest\/sources\/([^/]+)$/,
    handler: async (request, _url, params) => {
      const body = (await request.json()) as { enabled?: boolean };
      const source = ingestSources.find((item) => item.id === params[0]);
      if (!source) return json({ title: "数据源不存在", status: 404, code: "NOT_FOUND" }, 404);
      if (body.enabled !== undefined) source.enabled = body.enabled;
      return source;
    },
  },
  {
    method: "GET",
    pattern: /^\/api\/v1\/ingest\/runs$/,
    handler: (_request, url) => {
      const state = url.searchParams.get("state");
      return paginate(state ? ingestRuns.filter((run) => run.state === state) : ingestRuns, url);
    },
  },
  {
    method: "POST",
    pattern: /^\/api\/v1\/ingest\/runs$/,
    handler: async (request) => {
      const body = (await request.json()) as { sourceId: string; window?: string };
      const source = ingestSources.find((item) => item.id === body.sourceId);
      const run = {
        id: `run_${Date.now()}`,
        sourceId: body.sourceId,
        platform: source?.platform ?? "未知平台",
        kind: source?.kind ?? ("delivery" as const),
        window: body.window ?? "手动补采",
        state: "running" as const,
        rows: 0,
        errors: 0,
        message: null,
        startedAt: new Date().toISOString().slice(0, 16).replace("T", " "),
        durationMs: null,
      };
      ingestRuns.unshift(run);
      await delay();
      return run;
    },
  },
  {
    method: "GET",
    pattern: /^\/api\/v1\/field-mappings$/,
    handler: (_request, url) => {
      const platforms = list(url, "platforms");
      const status = url.searchParams.get("status");
      return paginate(
        fieldMappings.filter(
          (mapping) =>
            (!platforms.length || platforms.includes(mapping.platform)) && (!status || mapping.status === status),
        ),
        url,
      );
    },
  },
];

/** 命中草案接口就返回 mock 响应；否则返回 null，交给真实后端。 */
export async function mockFetch(request: Request): Promise<Response | null> {
  const url = new URL(request.url, window.location.origin);
  for (const route of routes) {
    if (route.method !== request.method) continue;
    const match = url.pathname.match(route.pattern);
    if (!match) continue;
    const result = await route.handler(request, url, match.slice(1));
    return result instanceof Response ? result : json(result);
  }
  return null;
}
