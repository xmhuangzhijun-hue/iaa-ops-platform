import {
  AllCommunityModule,
  ModuleRegistry,
  themeQuartz,
  type ColDef,
  type SortChangedEvent,
  type ValueFormatterParams,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";
import { Rows3 } from "lucide-react";
import { useMemo, useState } from "react";
import type { Schemas } from "../api/client";
import { dimensionValueLabel, type ReportColumn, type Row } from "../lib/dimensions";
import { formatMetric } from "../lib/format";
import { readJson, writeJson } from "../lib/storage";

ModuleRegistry.registerModules([AllCommunityModule]);

const gridTheme = themeQuartz.withParams({
  backgroundColor: "transparent",
  foregroundColor: "var(--text)",
  headerTextColor: "var(--muted)",
  headerBackgroundColor: "var(--panel-strong)",
  borderColor: "var(--line)",
  rowHoverColor: "var(--primary-soft)",
  selectedRowBackgroundColor: "var(--primary-soft)",
  accentColor: "var(--accent)",
  oddRowBackgroundColor: "var(--row-alt)",
  fontFamily: "inherit",
  fontSize: 13,
  headerFontSize: 12,
  headerFontWeight: 600,
  wrapperBorderRadius: 12,
  spacing: 6,
});

// AG Grid 内置文案默认英文，这里覆盖用到的部分。
const LOCALE_TEXT: Record<string, string> = {
  pageSizeSelectorLabel: "每页",
  page: "第",
  to: "至",
  of: "共",
  firstPage: "首页",
  previousPage: "上一页",
  nextPage: "下一页",
  lastPage: "末页",
  noRowsToShow: "当前筛选范围没有数据",
  loadingOoo: "加载中…",
  ariaFilterColumn: "筛选列",
  ariaSortableColumn: "可排序列",
};

type Density = "compact" | "standard" | "comfortable";
const DENSITIES: { id: Density; label: string; rowHeight: number }[] = [
  { id: "compact", label: "紧凑", rowHeight: 30 },
  { id: "standard", label: "标准", rowHeight: 38 },
  { id: "comfortable", label: "宽松", rowHeight: 46 },
];

type Props = {
  columns: ReportColumn[];
  rows: Row[];
  totals?: Row;
  loading?: boolean;
  /** 传入时由服务端排序与分页，表格只展示当前页。 */
  onServerSort?: (sort: Schemas["SortSpec"][]) => void;
  pageSize?: number;
  height?: number;
  caption?: React.ReactNode;
};

export function DataGrid({ columns, rows, totals, loading, onServerSort, pageSize = 50, height = 560, caption }: Props) {
  const [density, setDensity] = useState<Density>(() => readJson<Density>("local", "iaa.grid.density") ?? "standard");
  const rowHeight = DENSITIES.find((item) => item.id === density)?.rowHeight ?? 38;

  const columnDefs = useMemo<ColDef<Row>[]>(
    () =>
      columns.map((column, index) => {
        const numeric = column.kind !== "dimension";
        const format = (params: ValueFormatterParams<Row>) => {
          if (numeric) return formatMetric(typeof params.value === "number" ? params.value : null, column.unit, column.precision);
          if (params.node?.isRowPinned()) return index === 0 ? "合计" : "";
          return dimensionValueLabel(column.key, params.value);
        };
        return {
          colId: column.key,
          field: column.key,
          headerName: column.label,
          pinned: index === 0 && !numeric ? "left" : undefined,
          type: numeric ? "rightAligned" : undefined,
          minWidth: numeric ? 104 : 120,
          valueFormatter: format,
          cellClass: numeric ? "tabular-nums" : undefined,
          cellClassRules:
            column.key === "roi"
              ? {
                  "cell-bad": (params) => typeof params.value === "number" && params.value < 1,
                  "cell-good": (params) => typeof params.value === "number" && params.value >= 1.1,
                }
              : undefined,
          // 服务端排序时不在本地重排当前页。
          comparator: onServerSort ? () => 0 : undefined,
        } satisfies ColDef<Row>;
      }),
    [columns, onServerSort],
  );

  const handleSort = (event: SortChangedEvent<Row>) => {
    if (!onServerSort) return;
    const sort = event.api
      .getColumnState()
      .filter((state) => state.sort)
      .sort((a, b) => (a.sortIndex ?? 0) - (b.sortIndex ?? 0))
      .map((state) => ({ field: state.colId, direction: state.sort as "asc" | "desc" }));
    onServerSort(sort);
  };

  const changeDensity = (next: Density) => {
    setDensity(next);
    writeJson("local", "iaa.grid.density", next);
  };

  return (
    <div className="card grid-host p-3">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-sm">
        <div className="text-muted">{caption}</div>
        <div className="flex items-center gap-1" role="radiogroup" aria-label="表格密度">
          <Rows3 className="size-4 text-muted" aria-hidden />
          {DENSITIES.map((item) => (
            <button
              key={item.id}
              type="button"
              role="radio"
              aria-checked={density === item.id}
              aria-pressed={density === item.id}
              className="chip"
              onClick={() => changeDensity(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
      <div style={{ height }}>
        <AgGridReact<Row>
          theme={gridTheme}
          localeText={LOCALE_TEXT}
          rowData={rows}
          columnDefs={columnDefs}
          pinnedBottomRowData={totals ? [totals] : undefined}
          rowHeight={rowHeight}
          headerHeight={38}
          loading={loading}
          pagination={!onServerSort}
          paginationPageSize={pageSize}
          paginationPageSizeSelector={[20, 50, 100, 200]}
          onSortChanged={handleSort}
          enableCellTextSelection
          autoSizeStrategy={columns.length <= 8 ? { type: "fitGridWidth", defaultMinWidth: 100 } : { type: "fitCellContents" }}
          overlayNoRowsTemplate="<span>当前筛选范围没有数据</span>"
        />
      </div>
    </div>
  );
}
