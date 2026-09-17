import { lazy, Suspense, type ReactNode } from "react";
import { createBrowserRouter, Navigate } from "react-router";
import { usePrincipal } from "../auth/AuthProvider";
import { RequireAuth } from "../auth/RequireAuth";
import { EmptyBlock, FullScreenLoading } from "../components/States";
import { LoginPage } from "../pages/LoginPage";
import { visibleMenu } from "./menu";
import { Shell } from "./Shell";

// 按路由分包：AG Grid 只在看盘页加载，ECharts 只在趋势图加载。
const AccountAssignmentsPage = lazy(() => import("../pages/AccountAssignmentsPage").then((m) => ({ default: m.AccountAssignmentsPage })));
const AggregatePage = lazy(() => import("../pages/AggregatePage").then((m) => ({ default: m.AggregatePage })));
const AuditLogPage = lazy(() => import("../pages/AuditLogPage").then((m) => ({ default: m.AuditLogPage })));
const ChangePasswordPage = lazy(() => import("../pages/ChangePasswordPage").then((m) => ({ default: m.ChangePasswordPage })));
const DailyPage = lazy(() => import("../pages/DailyPage").then((m) => ({ default: m.DailyPage })));
const ImportsPage = lazy(() => import("../pages/ImportsPage").then((m) => ({ default: m.ImportsPage })));
const MetricCatalogPage = lazy(() => import("../pages/MetricCatalogPage").then((m) => ({ default: m.MetricCatalogPage })));
const RawDetailPage = lazy(() => import("../pages/RawDetailPage").then((m) => ({ default: m.RawDetailPage })));
const RoiAnomaliesPage = lazy(() => import("../pages/RoiAnomaliesPage").then((m) => ({ default: m.RoiAnomaliesPage })));
const TrendPage = lazy(() => import("../pages/TrendPage").then((m) => ({ default: m.TrendPage })));
const UsersPage = lazy(() => import("../pages/UsersPage").then((m) => ({ default: m.UsersPage })));

const page = (element: ReactNode) => <Suspense fallback={<FullScreenLoading />}>{element}</Suspense>;

function HomeRedirect() {
  const principal = usePrincipal();
  const first = visibleMenu(principal.permissions).flatMap((group) => group.items)[0];
  return first ? <Navigate to={first.route} replace /> : (
    <EmptyBlock title="暂无可访问页面">当前账号还没有分配页面权限，请联系管理员。</EmptyBlock>
  );
}

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    path: "/password",
    element: (
      <RequireAuth allowPasswordChange>
        {page(<ChangePasswordPage />)}
      </RequireAuth>
    ),
  },
  {
    path: "/",
    element: (
      <RequireAuth>
        <Shell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomeRedirect /> },
      { path: "analysis/aggregate", element: page(<AggregatePage />) },
      { path: "analysis/daily", element: page(<DailyPage />) },
      { path: "analysis/trend", element: page(<TrendPage />) },
      { path: "analysis/roi-anomalies", element: page(<RoiAnomaliesPage />) },
      { path: "analysis/raw", element: page(<RawDetailPage />) },
      { path: "system/users", element: page(<UsersPage />) },
      { path: "system/assignments", element: page(<AccountAssignmentsPage />) },
      { path: "system/imports", element: page(<ImportsPage />) },
      { path: "system/metrics", element: page(<MetricCatalogPage />) },
      { path: "system/audit", element: page(<AuditLogPage />) },
      // 原型与建设中地址统一落到 Shell 的不可用提示，不运行模拟页面。
      { path: "*", element: <EmptyBlock title="页面不存在">请从导航选择可用页面。</EmptyBlock> },
    ],
  },
]);
