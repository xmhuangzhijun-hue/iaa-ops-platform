import type { Schemas } from "../api/client";

export type ThemeMode = Schemas["PreferencesUpdate"]["theme_mode"];
export type PresetId = Schemas["PreferencesUpdate"]["theme_preset"];

export const THEME_PRESETS: { id: PresetId; label: string; primary: string; glow: string }[] = [
  { id: "aurora-blue", label: "极光蓝", primary: "#42D9FF", glow: "#6C63FF" },
  { id: "arc-purple", label: "电弧紫", primary: "#B78CFF", glow: "#FF55D7" },
  { id: "quantum-cyan", label: "量子青", primary: "#45F0CF", glow: "#17A8FF" },
  { id: "pulse-green", label: "脉冲绿", primary: "#8AF27C", glow: "#35D7B7" },
  { id: "corona-gold", label: "日冕金", primary: "#FFD166", glow: "#FF8A4C" },
  { id: "molten-orange", label: "熔芯橙", primary: "#FF9B62", glow: "#FF4D6D" },
  { id: "rose-wave", label: "玫瑰波", primary: "#FF7CAA", glow: "#A978FF" },
  { id: "glacier-blue", label: "冰川蓝", primary: "#8FB8FF", glow: "#55E6FF" },
  { id: "nebula-purple", label: "星云紫", primary: "#9B7BFF", glow: "#4FD8FF" },
  { id: "deep-space-gray", label: "深空灰", primary: "#A7B4C4", glow: "#66788F" },
  { id: "neon-cyan", label: "荧光青", primary: "#00F0FF", glow: "#00A3FF" },
  { id: "custom", label: "自定义", primary: "#66E3FF", glow: "#7B61FF" },
];

export function presetById(id: PresetId) {
  return THEME_PRESETS.find((preset) => preset.id === id) ?? THEME_PRESETS[0];
}

/** 图表无法读取 CSS 变量的即时值（子组件副作用先于主题写入），按明暗给定常量。 */
export const CHART_TOKENS = {
  light: { text: "#5e6e84", line: "rgba(91,111,136,.22)", tooltip: "#ffffff" },
  dark: { text: "#91a2b9", line: "rgba(139,171,205,.18)", tooltip: "#101d2d" },
} as const;
