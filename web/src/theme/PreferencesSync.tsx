import { useEffect, useRef } from "react";
import { usePreferences, useSavePreferences } from "../api/hooks";
import { useTheme } from "./ThemeProvider";

/** 登录后把主题与账号偏好双向同步：先采用服务端设置，之后的修改防抖保存。 */
export function PreferencesSync() {
  const { settings, replace } = useTheme();
  const preferences = usePreferences();
  const { mutate } = useSavePreferences();
  const applied = useRef(false);
  const lastSaved = useRef("");

  useEffect(() => {
    if (!preferences.data || applied.current) return;
    applied.current = true;
    const saved = preferences.data;
    if (saved.revision > 0) {
      const next = { mode: saved.theme_mode, preset: saved.theme_preset, customPrimary: saved.custom_primary ?? null };
      lastSaved.current = JSON.stringify(next);
      replace(next);
    }
  }, [preferences.data, replace]);

  useEffect(() => {
    if (!applied.current) return;
    const serialized = JSON.stringify(settings);
    if (serialized === lastSaved.current) return;
    const timer = window.setTimeout(() => {
      lastSaved.current = serialized;
      mutate({ theme_mode: settings.mode, theme_preset: settings.preset, custom_primary: settings.customPrimary });
    }, 600);
    return () => window.clearTimeout(timer);
  }, [settings, mutate]);

  return null;
}
