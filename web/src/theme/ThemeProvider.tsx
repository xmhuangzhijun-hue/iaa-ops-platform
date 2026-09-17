import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { readJson, writeJson } from "../lib/storage";
import { accentFor, onPrimaryFor } from "./color";
import { presetById, type PresetId, type ThemeMode } from "./presets";

export type ThemeSettings = { mode: ThemeMode; preset: PresetId; customPrimary: string | null };

type ThemeValue = {
  settings: ThemeSettings;
  resolvedMode: "light" | "dark";
  primary: string;
  glow: string;
  update: (patch: Partial<ThemeSettings>) => void;
  replace: (settings: ThemeSettings) => void;
};

const STORAGE_KEY = "iaa.theme";
const DEFAULT_SETTINGS: ThemeSettings = { mode: "system", preset: "aurora-blue", customPrimary: null };
const ThemeContext = createContext<ThemeValue | null>(null);

function useSystemDark(): boolean {
  const query = "(prefers-color-scheme: dark)";
  const [dark, setDark] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const listener = (event: MediaQueryListEvent) => setDark(event.matches);
    media.addEventListener("change", listener);
    return () => media.removeEventListener("change", listener);
  }, []);
  return dark;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<ThemeSettings>(() => ({
    ...DEFAULT_SETTINGS,
    ...readJson<Partial<ThemeSettings>>("local", STORAGE_KEY),
  }));
  const systemDark = useSystemDark();
  const resolvedMode = settings.mode === "system" ? (systemDark ? "dark" : "light") : settings.mode;
  const preset = presetById(settings.preset);
  const primary = settings.preset === "custom" && settings.customPrimary ? settings.customPrimary : preset.primary;
  const glow = preset.glow;

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.mode = resolvedMode;
    root.style.setProperty("--primary", primary);
    root.style.setProperty("--glow", glow);
    root.style.setProperty("--accent", accentFor(primary, resolvedMode));
    root.style.setProperty("--on-primary", onPrimaryFor(primary));
    writeJson("local", STORAGE_KEY, settings);
  }, [settings, resolvedMode, primary, glow]);

  const value = useMemo<ThemeValue>(
    () => ({
      settings,
      resolvedMode,
      primary,
      glow,
      update: (patch) => setSettings((current) => ({ ...current, ...patch })),
      replace: setSettings,
    }),
    [settings, resolvedMode, primary, glow],
  );
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeValue {
  const value = useContext(ThemeContext);
  if (!value) throw new Error("useTheme 必须在 ThemeProvider 内使用");
  return value;
}
