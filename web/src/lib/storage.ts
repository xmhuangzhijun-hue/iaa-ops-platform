import { useEffect, useState } from "react";

type Area = "local" | "session";

function area(kind: Area): Storage {
  return kind === "local" ? window.localStorage : window.sessionStorage;
}

// 浏览器存储可能被禁用（隐私模式、策略），读写失败时退回默认值，不影响页面。
export function readJson<T>(kind: Area, key: string): T | null {
  try {
    const raw = area(kind).getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function writeJson(kind: Area, key: string, value: unknown): void {
  try {
    if (value === null || value === undefined) area(kind).removeItem(key);
    else area(kind).setItem(key, JSON.stringify(value));
  } catch {
    /* 忽略 */
  }
}

export function useSessionState<T>(key: string, initial: T): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => readJson<T>("session", key) ?? initial);
  useEffect(() => writeJson("session", key, value), [key, value]);
  return [value, setValue];
}
