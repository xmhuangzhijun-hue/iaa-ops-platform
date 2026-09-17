import type { Schemas } from "../api/client";

export type Dimension = NonNullable<Schemas["ReportQuery"]["group_by"]>[number];
export type MetricKey = NonNullable<Schemas["ReportQuery"]["metrics"]>[number];
export type ReportColumn = Schemas["ReportColumn"];
export type Cell = string | number | null;
export type Row = Record<string, Cell>;

export const DIMENSION_LABELS: Record<Dimension, string> = {
  stat_date: "日期",
  stat_hour: "小时",
  media: "媒体",
  product: "产品",
  agency: "代理",
  account: "账户",
  operator: "运营",
  campaign: "推广计划",
};

const MEDIA_LABELS: Record<string, string> = { vivo: "vivo", oppo: "OPPO", huawei: "华为", xiaomi: "小米", honor: "荣耀" };

export function dimensionValueLabel(dimension: string, value: unknown): string {
  if (value === null || value === undefined || value === "") return "未映射";
  if (dimension === "media") return MEDIA_LABELS[String(value)] ?? String(value);
  if (dimension === "stat_hour" && typeof value === "number") return `${String(value).padStart(2, "0")}:00`;
  return String(value);
}

export const ROLE_LABELS: Record<string, string> = {
  super_admin: "超级管理员",
  company_admin: "公司管理员",
  operator: "运营",
  agency_admin: "代理管理员",
  customer: "客户",
  readonly: "只读",
};
