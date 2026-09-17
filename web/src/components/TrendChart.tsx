import { LineChart } from "echarts/charts";
import { DataZoomComponent, GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { useEffect, useRef } from "react";
import type { Schemas } from "../api/client";
import { formatMetric } from "../lib/format";
import { CHART_TOKENS } from "../theme/presets";
import { useTheme } from "../theme/ThemeProvider";

echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, DataZoomComponent, CanvasRenderer]);

type Meta = Record<string, { label: string; unit: Schemas["MetricDefinition"]["unit"]; precision: number }>;
const RIGHT_AXIS_UNITS = new Set(["ratio", "per_unit"]);

export function TrendChart({ result, meta, height = 400 }: { result: Schemas["TrendResult"]; meta: Meta; height?: number }) {
  const container = useRef<HTMLDivElement>(null);
  const chart = useRef<echarts.ECharts | null>(null);
  const { resolvedMode, primary } = useTheme();

  useEffect(() => {
    if (!container.current) return;
    const instance = echarts.init(container.current);
    chart.current = instance;
    const observer = new ResizeObserver(() => instance.resize());
    observer.observe(container.current);
    return () => {
      observer.disconnect();
      instance.dispose();
      chart.current = null;
    };
  }, []);

  useEffect(() => {
    if (!chart.current) return;
    const tokens = CHART_TOKENS[resolvedMode];
    // 主色之后用固定的对比色，不接主题辅色：部分预设的主辅色相近，两条线会分不清。
    const palette = [primary, "#F0A03C", "#3DBE8B", "#E4607A", "#6F9BFF", "#B57BFF", "#8A98AA", "#E8C547", "#4FD8FF"];
    const buckets = [...new Set(result.series.flatMap((series) => series.points.map((point) => point.bucket)))].sort();
    const split = result.series.length > 1 || (result.series[0] && result.series[0].key !== "全部");
    const metrics = split ? result.metrics.slice(0, 1) : result.metrics;
    const hasRight = !split && metrics.some((key) => RIGHT_AXIS_UNITS.has(meta[key]?.unit ?? ""));
    const hasLeft = split || metrics.some((key) => !RIGHT_AXIS_UNITS.has(meta[key]?.unit ?? ""));

    const valueAt = (series: Schemas["TrendSeries"], bucket: string, key: string) =>
      series.points.find((point) => point.bucket === bucket)?.values[key] ?? null;

    const lines = split
      ? result.series.map((series) => ({ name: series.label, key: metrics[0], data: buckets.map((b) => valueAt(series, b, metrics[0])) }))
      : metrics.map((key) => ({ name: meta[key]?.label ?? key, key, data: buckets.map((b) => valueAt(result.series[0], b, key)) }));

    const axisLabel = (key: string) => (value: number) => formatMetric(value, meta[key]?.unit, meta[key]?.precision);
    const leftKey = split ? metrics[0] : metrics.find((key) => !RIGHT_AXIS_UNITS.has(meta[key]?.unit ?? "")) ?? metrics[0];
    const rightKey = metrics.find((key) => RIGHT_AXIS_UNITS.has(meta[key]?.unit ?? "")) ?? metrics[0];
    const axisStyle = { axisLabel: { color: tokens.text }, splitLine: { lineStyle: { color: tokens.line } } };

    chart.current.setOption(
      {
        color: palette,
        animationDuration: 300,
        grid: { left: 12, right: 12, top: 44, bottom: buckets.length > 20 ? 56 : 24, containLabel: true },
        legend: { top: 0, textStyle: { color: tokens.text }, type: "scroll" },
        tooltip: {
          trigger: "axis",
          backgroundColor: tokens.tooltip,
          borderColor: tokens.line,
          textStyle: { color: resolvedMode === "dark" ? "#eef5ff" : "#172231" },
          valueFormatter: undefined,
          formatter: (items: { axisValueLabel: string; marker: string; seriesName: string; seriesIndex: number; value: number | null }[]) =>
            [items[0]?.axisValueLabel, ...items.map((item) => {
              const key = lines[item.seriesIndex]?.key ?? metrics[0];
              return `${item.marker}${item.seriesName}　<b>${formatMetric(item.value, meta[key]?.unit, meta[key]?.precision)}</b>`;
            })].join("<br/>"),
        },
        xAxis: { type: "category", data: buckets, axisLabel: { color: tokens.text }, axisLine: { lineStyle: { color: tokens.line } } },
        yAxis: [
          { type: "value", show: hasLeft, ...axisStyle, axisLabel: { color: tokens.text, formatter: axisLabel(leftKey) } },
          { type: "value", show: hasRight, ...axisStyle, splitLine: { show: false }, axisLabel: { color: tokens.text, formatter: axisLabel(rightKey) } },
        ],
        dataZoom: buckets.length > 20 ? [{ type: "slider", height: 18, bottom: 8 }, { type: "inside" }] : [],
        series: lines.map((line) => ({
          name: line.name,
          type: "line",
          smooth: true,
          showSymbol: buckets.length <= 31,
          connectNulls: false,
          yAxisIndex: !split && RIGHT_AXIS_UNITS.has(meta[line.key]?.unit ?? "") && hasLeft ? 1 : 0,
          data: line.data,
        })),
      },
      true,
    );
  }, [result, meta, resolvedMode, primary]);

  return <div ref={container} style={{ height }} role="img" aria-label="趋势图" />;
}
