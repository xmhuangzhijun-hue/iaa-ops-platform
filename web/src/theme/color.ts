const HEX = /^#?([0-9a-f]{6})$/i;
const RGB = /^(?:rgb\s*\()?\s*(\d{1,3})\s*[, ]\s*(\d{1,3})\s*[, ]\s*(\d{1,3})\s*\)?$/i;
const HSL = /^hsl\s*\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)%\s*,\s*(\d+(?:\.\d+)?)%\s*\)$/i;

function toHex(channels: number[]): string {
  return `#${channels.map((value) => Math.round(value).toString(16).padStart(2, "0")).join("").toUpperCase()}`;
}

/** 接受 HEX / RGB / HSL，返回 #RRGGBB；无法识别返回 null。 */
export function normalizeColor(input: string): string | null {
  const raw = input.trim();
  const hex = raw.match(HEX);
  if (hex) return `#${hex[1].toUpperCase()}`;

  const rgb = raw.match(RGB);
  if (rgb) {
    const channels = rgb.slice(1).map(Number);
    return channels.every((value) => value <= 255) ? toHex(channels) : null;
  }

  const hsl = raw.match(HSL);
  if (hsl) {
    const hue = ((Number(hsl[1]) % 360) + 360) % 360;
    const saturation = Number(hsl[2]) / 100;
    const lightness = Number(hsl[3]) / 100;
    if (saturation > 1 || lightness > 1) return null;
    const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
    const x = chroma * (1 - Math.abs(((hue / 60) % 2) - 1));
    const m = lightness - chroma / 2;
    const [r, g, b] =
      hue < 60 ? [chroma, x, 0] : hue < 120 ? [x, chroma, 0] : hue < 180 ? [0, chroma, x]
        : hue < 240 ? [0, x, chroma] : hue < 300 ? [x, 0, chroma] : [chroma, 0, x];
    return toHex([(r + m) * 255, (g + m) * 255, (b + m) * 255]);
  }
  return null;
}

function channels(hex: string): [number, number, number] {
  const value = hex.replace("#", "");
  return [0, 2, 4].map((index) => parseInt(value.slice(index, index + 2), 16)) as [number, number, number];
}

function luminance(hex: string): number {
  const [r, g, b] = channels(hex).map((value) => {
    const s = value / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

function mix(a: string, b: string, weight: number): string {
  const [ca, cb] = [channels(a), channels(b)];
  return toHex(ca.map((value, index) => value + (cb[index] - value) * weight));
}

/** 用于文字与选中态的强调色：在当前背景上对比度至少 4.5:1（WCAG AA）。 */
export function accentFor(primary: string, mode: "light" | "dark"): string {
  const background = mode === "light" ? "#FFFFFF" : "#101D2D";
  const target = mode === "light" ? "#000000" : "#FFFFFF";
  for (let step = 0; step <= 20; step += 1) {
    const candidate = mix(primary, target, step / 20);
    if (contrast(candidate, background) >= 4.5) return candidate;
  }
  return target;
}

/** 主色按钮上的文字颜色：取对比度更高的一方。 */
export function onPrimaryFor(primary: string): string {
  return contrast(primary, "#0B1622") >= contrast(primary, "#FFFFFF") ? "#0B1622" : "#FFFFFF";
}
