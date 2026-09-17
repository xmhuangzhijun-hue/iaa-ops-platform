import { clsx } from "clsx";
import { ChevronDown, LogOut, Menu, Search, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, Outlet, useLocation } from "react-router";
import { useAuth, usePrincipal } from "../auth/AuthProvider";
import { EmptyBlock } from "../components/States";
import { FilterProvider } from "../filters/FilterProvider";
import { ROLE_LABELS } from "../lib/dimensions";
import { readJson, writeJson } from "../lib/storage";
import { PreferencesSync } from "../theme/PreferencesSync";
import { ThemePanel } from "../theme/ThemePanel";
import { findEntry, visibleMenu } from "./menu";
import { NAV_ICONS } from "./navIcons";

export function Shell() {
  const principal = usePrincipal();
  const { logout } = useAuth();
  const location = useLocation();
  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>(() => readJson("local", "iaa.nav.collapsed") ?? {});
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => setDrawerOpen(false), [location.pathname]);
  useEffect(() => writeJson("local", "iaa.nav.collapsed", collapsed), [collapsed]);

  // 菜单结构由前端维护并按权限裁剪；接口鉴权仍在后端。
  const groups = useMemo(() => visibleMenu(principal.permissions), [principal.permissions]);
  // 直接输入地址也按当前账号裁剪，避免挂载没有权限的页面。
  const current = findEntry(location.pathname, visibleMenu(principal.permissions, true));
  const isWorkspace = location.pathname === "/workspace";
  const keyword = search.trim().toLowerCase();

  return (
    <FilterProvider>
      <PreferencesSync />
      <div className="app-ambient" aria-hidden />
      <div className={clsx("relative flex min-h-dvh", isWorkspace && "workspace-shell")}>
        <aside
          className={clsx(
            "glass-sidebar fixed inset-y-0 left-0 z-40 flex w-64 flex-col px-3 py-4 transition-transform duration-200",
            "lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0",
            drawerOpen ? "translate-x-0" : "-translate-x-full",
          )}
          aria-label="主导航"
        >
          <div className="mb-4 flex items-center gap-2.5 px-2">
            <span
              aria-hidden
              className="grid size-9 place-items-center rounded-xl text-sm font-bold text-[var(--on-primary)]"
              style={{ background: "linear-gradient(135deg, var(--primary), var(--glow))" }}
            >
              IA
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">IAA 运营中台</p>
              <p className="truncate text-xs text-[var(--sidebar-muted)]">虚构数据 · 本地模拟执行</p>
            </div>
            <button type="button" className="ml-auto lg:hidden" onClick={() => setDrawerOpen(false)} aria-label="关闭导航">
              <X className="size-5" />
            </button>
          </div>

          <label className="relative mb-3 block px-1">
            <span className="sr-only">搜索页面</span>
            <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-[var(--sidebar-muted)]" aria-hidden />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="搜索页面"
              className="w-full rounded-xl border border-[var(--sidebar-border)] bg-transparent py-2 pr-3 pl-8 text-sm placeholder:text-[var(--sidebar-muted)]"
            />
          </label>

          <nav className="-mx-1 flex-1 space-y-3 overflow-y-auto px-1">
            {groups.map((group) => {
              const items = group.items.filter((page) => !keyword || page.title.toLowerCase().includes(keyword));
              if (!items.length) return null;
              const folded = !keyword && collapsed[group.title];
              return (
                <div key={group.title}>
                  <button
                    type="button"
                    className="flex w-full items-center justify-between px-2.5 pb-1 text-xs font-medium tracking-wider text-[var(--sidebar-muted)]"
                    aria-expanded={!folded}
                    onClick={() => setCollapsed((state) => ({ ...state, [group.title]: !state[group.title] }))}
                  >
                    {group.title}
                    <ChevronDown className={clsx("size-3.5 transition-transform", folded && "-rotate-90")} aria-hidden />
                  </button>
                  {!folded && (
                    <ul className="space-y-0.5">
                      {items.map((page) => {
                        const Icon = NAV_ICONS[page.id];
                        return (
                          <li key={page.id}>
                            <Link to={page.route} className="nav-item" aria-current={current?.id === page.id ? "page" : undefined}>
                              {Icon && <Icon className="size-4 shrink-0" aria-hidden />}
                              <span className="truncate">{page.title}</span>
                            </Link>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              );
            })}
          </nav>

          <div className="mt-3 flex items-center gap-2 rounded-xl border border-[var(--sidebar-border)] px-3 py-2.5">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{principal.display_name}</p>
              <p className="truncate text-xs text-[var(--sidebar-muted)]">
                {principal.roles.map((role) => ROLE_LABELS[role] ?? role).join("、")}
              </p>
            </div>
            <button type="button" onClick={logout} className="rounded-lg p-1.5 hover:bg-[var(--primary-soft)]" aria-label="退出登录" title="退出登录">
              <LogOut className="size-4" />
            </button>
          </div>
        </aside>

        {drawerOpen && (
          <button type="button" className="fixed inset-0 z-30 bg-black/35 lg:hidden" aria-label="关闭导航" onClick={() => setDrawerOpen(false)} />
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-line bg-[color-mix(in_srgb,var(--bg)_72%,transparent)] px-4 backdrop-blur-xl lg:px-6">
            <button type="button" className="control px-2 lg:hidden" onClick={() => setDrawerOpen(true)} aria-label="打开导航">
              <Menu className="size-5" />
            </button>
            <div className="min-w-0 leading-tight">
              <p className="truncate text-xs text-muted">{current?.group ?? "IAA 运营中台"}</p>
              <p className="truncate text-sm font-semibold">{current?.title ?? ""}</p>
            </div>
            <div className="ml-auto flex items-center gap-2">
              <ThemePanel />
            </div>
          </header>

          <main className={isWorkspace ? "workspace-main" : "mx-auto w-full max-w-[1680px] flex-1 px-4 py-5 lg:px-6"}>
            {current || location.pathname === "/" ? (
              <Outlet />
            ) : (
              <EmptyBlock title="页面不存在或当前账号无权访问">
                <p>请从导航选择可用页面。</p>
                <Link className="mt-4 inline-flex text-primary underline underline-offset-4" to="/">返回首页</Link>
              </EmptyBlock>
            )}
          </main>
        </div>
      </div>
    </FilterProvider>
  );
}
