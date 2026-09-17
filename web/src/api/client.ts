import createClient from "openapi-fetch";
import { SESSION_EXPIRED, readTokens, refreshTokens } from "../auth/tokens";
import type { components, paths } from "./schema";

export type Schemas = components["schemas"];

const NO_AUTH_PATHS = new Set(["/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"]);

export class ApiProblem extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly detail?: string | null,
  ) {
    super(message);
  }
}

async function authFetch(request: Request): Promise<Response> {
  const path = new URL(request.url, window.location.origin).pathname;

  const tokens = readTokens();
  if (NO_AUTH_PATHS.has(path) || !tokens) return fetch(request);

  const retry = request.clone();
  request.headers.set("Authorization", `Bearer ${tokens.access}`);
  const response = await fetch(request);
  if (response.status !== 401) return response;

  const renewed = await refreshTokens(tokens);
  if (!renewed) {
    window.dispatchEvent(new Event(SESSION_EXPIRED));
    return response;
  }
  retry.headers.set("Authorization", `Bearer ${renewed.access}`);
  return fetch(retry);
}

export const api = createClient<paths>({ baseUrl: "", fetch: authFetch });

export async function unwrap<T>(pending: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await pending;
  if (error !== undefined || data === undefined) {
    const problem = (typeof error === "object" && error !== null ? error : {}) as Partial<Schemas["Error"]>;
    throw new ApiProblem(problem.title ?? `请求失败（${response.status}）`, response.status, problem.code, problem.detail);
  }
  return data;
}

export type QueryValue = string | number | boolean | null | undefined | (string | number)[];

/**
 * 调用尚未进入 OpenAPI 契约的草案接口（界面先行阶段由 mock 承载）。
 * 后端实现后这些路径会进入契约，改用生成类型的 api 客户端，本函数删除。
 */
export async function draftRequest<T>(path: string, init?: RequestInit & { query?: Record<string, QueryValue> }): Promise<T> {
  const url = new URL(path, window.location.origin);
  for (const [key, value] of Object.entries(init?.query ?? {})) {
    if (value === null || value === undefined || value === "") continue;
    if (Array.isArray(value)) value.forEach((item) => url.searchParams.append(key, String(item)));
    else url.searchParams.set(key, String(value));
  }
  const headers = new Headers(init?.headers);
  if (init?.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await authFetch(new Request(url, { ...init, headers }));
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as Partial<Schemas["Error"]>;
    throw new ApiProblem(problem.title ?? `请求失败（${response.status}）`, response.status, problem.code, problem.detail);
  }
  return (await response.json()) as T;
}

export function problemMessage(error: unknown): string {
  if (error instanceof ApiProblem) return error.detail ? `${error.message}：${error.detail}` : error.message;
  return "网络异常，请确认后端服务已启动";
}
