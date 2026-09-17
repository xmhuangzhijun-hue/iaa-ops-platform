/**
 * v1 仅开放已接业务后端的页面；界面原型保留在源码中，不注册到演示入口。
 * 菜单与页面挂载共用权限裁剪，接口与数据范围仍由服务端独立强制。
 */
export type MenuEntry = {
  id: string;
  title: string;
  route: string;
  /** 需要的权限之一；留空表示登录即可见 */
  requires?: string[];
};

export type MenuGroup = { title: string; items: MenuEntry[] };

const DASHBOARD = ["dashboard.read"];
const REAL_METRICS = ["metrics.real"];

export const MENU: MenuGroup[] = [
  {
    title: "看盘分析",
    items: [
      { id: "report-aggregate", title: "聚合看盘", route: "/analysis/aggregate", requires: DASHBOARD },
      { id: "report-daily", title: "分天明细", route: "/analysis/daily", requires: DASHBOARD },
      { id: "report-trend", title: "趋势图", route: "/analysis/trend", requires: REAL_METRICS },
      { id: "roi-anomalies", title: "ROI 异常清单", route: "/analysis/roi-anomalies", requires: REAL_METRICS },
      { id: "raw-detail", title: "原始明细", route: "/analysis/raw", requires: REAL_METRICS },
    ],
  },
  {
    title: "系统管理",
    items: [
      { id: "user-manage", title: "用户与权限", route: "/system/users", requires: ["users.team.manage", "users.agency.manage"] },
      { id: "mapping-manage", title: "账户分配", route: "/system/assignments", requires: ["mappings.manage"] },
      { id: "data-import", title: "数据导入", route: "/system/imports", requires: ["imports.manage"] },
      { id: "metric-catalog", title: "指标与字段说明", route: "/system/metrics", requires: DASHBOARD },
      { id: "audit-log", title: "审计日志", route: "/system/audit", requires: ["audit.read"] },
    ],
  },
];

export function visibleMenu(permissions: string[]): MenuGroup[] {
  const granted = new Set(permissions);
  const allowed = (entry: MenuEntry) =>
    !entry.requires || granted.has("*") || entry.requires.some((permission) => granted.has(permission));
  return MENU.map((group) => ({ ...group, items: group.items.filter(allowed) })).filter((group) => group.items.length);
}

export function findEntry(pathname: string, groups: MenuGroup[]): (MenuEntry & { group: string }) | undefined {
  const route = pathname.replace(/\/+$/, "");
  for (const group of groups) {
    const item = group.items.find((entry) => route === entry.route);
    if (item) return { ...item, group: group.title };
  }
  return undefined;
}
