import { usePreferences, useSavePreferences } from "../api/hooks";
import { usePrincipal } from "../auth/AuthProvider";
import type { MetricKey } from "../lib/dimensions";

/** 页面的指标列：来自账号偏好，按当前账号可见指标裁剪。 */
export function useScreenMetrics(screen: string, defaults: MetricKey[]) {
  const principal = usePrincipal();
  const preferences = usePreferences();
  const { mutate } = useSavePreferences();
  const visible = principal.visible_metrics;

  const saved = (preferences.data?.table_columns?.[screen] ?? []) as MetricKey[];
  const allowed = (keys: MetricKey[]) => keys.filter((key) => visible.includes(key));
  const value = allowed(saved).length ? allowed(saved) : allowed(defaults);

  const set = (next: MetricKey[]) =>
    mutate({ table_columns: { ...(preferences.data?.table_columns ?? {}), [screen]: next } });

  return { value, set, visible, ready: !preferences.isLoading };
}
