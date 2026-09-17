import * as Popover from "@radix-ui/react-popover";
import { Check, Monitor, Moon, Palette, Sun } from "lucide-react";
import { useState, type FormEvent } from "react";
import { normalizeColor } from "./color";
import { THEME_PRESETS, type ThemeMode } from "./presets";
import { useTheme } from "./ThemeProvider";

const MODES: { id: ThemeMode; label: string; icon: typeof Sun }[] = [
  { id: "light", label: "浅色", icon: Sun },
  { id: "dark", label: "深色", icon: Moon },
  { id: "system", label: "跟随系统", icon: Monitor },
];

export function ThemePanel() {
  const { settings, update, primary, glow } = useTheme();
  const [customInput, setCustomInput] = useState(settings.customPrimary ?? "");
  const [customError, setCustomError] = useState<string | null>(null);

  const applyCustom = (event: FormEvent) => {
    event.preventDefault();
    const color = normalizeColor(customInput);
    if (!color) {
      setCustomError("请输入 #RRGGBB、rgb(r,g,b) 或 hsl(h,s%,l%)");
      return;
    }
    setCustomError(null);
    setCustomInput(color);
    update({ preset: "custom", customPrimary: color });
  };

  return (
    <Popover.Root>
      <Popover.Trigger asChild>
        <button type="button" className="control" aria-label="视觉主题">
          <span
            aria-hidden
            className="size-4 rounded-full"
            style={{ background: `linear-gradient(135deg, ${primary}, ${glow})` }}
          />
          <Palette className="size-4" aria-hidden />
          <span className="hidden sm:inline">视觉主题</span>
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="end" sideOffset={8} className="popover w-[min(22rem,calc(100vw-2rem))] p-4">
          <p className="mb-2 text-xs font-semibold text-muted">显示模式</p>
          <div className="mb-4 grid grid-cols-3 gap-1 rounded-xl border border-line p-1" role="radiogroup" aria-label="显示模式">
            {MODES.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                type="button"
                role="radio"
                aria-checked={settings.mode === id}
                onClick={() => update({ mode: id })}
                className="chip justify-center border-transparent"
                aria-pressed={settings.mode === id}
              >
                <Icon className="size-3.5" aria-hidden />
                {label}
              </button>
            ))}
          </div>

          <p className="mb-2 text-xs font-semibold text-muted">主题预设</p>
          <div className="mb-4 grid grid-cols-4 gap-2">
            {THEME_PRESETS.filter((preset) => preset.id !== "custom").map((preset) => {
              const active = settings.preset === preset.id;
              return (
                <button
                  key={preset.id}
                  type="button"
                  onClick={() => update({ preset: preset.id })}
                  aria-pressed={active}
                  className="flex flex-col items-center gap-1 rounded-xl p-1.5 text-xs hover:bg-[var(--primary-soft)]"
                >
                  <span
                    className="relative grid size-9 place-items-center rounded-full ring-offset-2 ring-offset-[var(--panel-strong)]"
                    style={{
                      background: `linear-gradient(135deg, ${preset.primary}, ${preset.glow})`,
                      boxShadow: active ? `0 0 0 2px ${preset.primary}` : undefined,
                    }}
                  >
                    {active && <Check className="size-4 text-[#0b1622]" aria-hidden />}
                  </span>
                  {preset.label}
                </button>
              );
            })}
          </div>

          <form onSubmit={applyCustom}>
            <label htmlFor="custom-primary" className="mb-2 block text-xs font-semibold text-muted">
              自定义主色
            </label>
            <div className="flex gap-2">
              <input
                id="custom-primary"
                value={customInput}
                onChange={(event) => setCustomInput(event.target.value)}
                placeholder="#42D9FF / rgb(66,217,255)"
                className="control min-w-0 flex-1"
                aria-invalid={Boolean(customError)}
              />
              <button type="submit" className="btn-primary">
                应用
              </button>
            </div>
            {customError && <p className="mt-1.5 text-xs text-bad">{customError}</p>}
          </form>
          <p className="mt-3 text-xs text-muted">主题保存在账号偏好中，换设备登录后保持一致。</p>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
