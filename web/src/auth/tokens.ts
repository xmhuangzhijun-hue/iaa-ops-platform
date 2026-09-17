import { readJson, writeJson } from "../lib/storage";

export type Tokens = { access: string; refresh: string };

const KEY = "iaa.tokens";
export const SESSION_EXPIRED = "iaa:session-expired";

export function readTokens(): Tokens | null {
  return readJson<Tokens>("local", KEY);
}

export function writeTokens(tokens: Tokens | null): void {
  writeJson("local", KEY, tokens);
}

async function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  // 刷新令牌每次轮换、重放即吊销全部会话：多个标签页必须串行刷新，后到者复用先到者的新令牌。
  if ("locks" in navigator) return navigator.locks.request("iaa-refresh", task);
  return task();
}

let inFlight: Promise<Tokens | null> | null = null;

export function refreshTokens(stale: Tokens): Promise<Tokens | null> {
  inFlight ??= withRefreshLock(async () => {
    const current = readTokens();
    if (!current) return null;
    if (current.refresh !== stale.refresh) return current;
    const response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: current.refresh }),
    });
    if (!response.ok) {
      writeTokens(null);
      return null;
    }
    const body = (await response.json()) as { access_token: string; refresh_token: string };
    const renewed = { access: body.access_token, refresh: body.refresh_token };
    writeTokens(renewed);
    return renewed;
  }).finally(() => {
    inFlight = null;
  });
  return inFlight;
}
