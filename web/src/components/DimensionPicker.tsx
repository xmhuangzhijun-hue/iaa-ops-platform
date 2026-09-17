import { DIMENSION_LABELS, type Dimension } from "../lib/dimensions";

type Props = {
  available: Dimension[];
  value: Dimension[];
  onChange: (value: Dimension[]) => void;
  max?: number;
  label?: string;
};

/** 维度按点击顺序分组，序号即分组层级。 */
export function DimensionPicker({ available, value, onChange, max = 4, label = "分组维度" }: Props) {
  const toggle = (dimension: Dimension) => {
    if (value.includes(dimension)) {
      if (value.length > 1) onChange(value.filter((item) => item !== dimension));
    } else if (value.length < max) {
      onChange([...value, dimension]);
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label={label}>
      <span className="mr-1 text-xs font-semibold text-muted">{label}</span>
      {available.map((dimension) => {
        const index = value.indexOf(dimension);
        const active = index >= 0;
        return (
          <button
            key={dimension}
            type="button"
            className="chip"
            aria-pressed={active}
            disabled={!active && value.length >= max}
            onClick={() => toggle(dimension)}
          >
            {active && value.length > 1 && (
              <span className="grid size-4 place-items-center rounded-full bg-[var(--accent)] text-[10px] text-[var(--panel-strong)]">
                {index + 1}
              </span>
            )}
            {DIMENSION_LABELS[dimension]}
          </button>
        );
      })}
    </div>
  );
}
