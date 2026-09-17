import { KeyRound, LoaderCircle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { problemMessage } from "../api/client";
import { useAuth } from "../auth/AuthProvider";
import { ThemePanel } from "../theme/ThemePanel";

// 本机开发时列出虚构数据的演示账号；生产构建不显示。
const DEMO_ACCOUNTS = import.meta.env.DEV
  ? [
      { username: "demo.admin", role: "超级管理员" },
      { username: "demo.company", role: "公司管理员" },
      { username: "demo.operator", role: "运营（运营甲）" },
      { username: "demo.agency", role: "代理管理员（星河代理）" },
      { username: "demo.readonly", role: "只读" },
    ]
  : [];
const DEMO_PASSWORD = "iaa-demo-2026";

export function LoginPage() {
  const { status, principal, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === "authenticated" && principal) {
    return <Navigate to={principal.must_change_password ? "/password" : "/"} replace />;
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const signedIn = await login(username.trim(), password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(signedIn?.must_change_password ? "/password" : from && from !== "/login" ? from : "/", { replace: true });
    } catch (reason) {
      setError(problemMessage(reason));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="relative grid min-h-dvh place-items-center px-4 py-10">
      <div className="app-ambient" aria-hidden />
      <div className="absolute top-4 right-4">
        <ThemePanel />
      </div>
      <main className="relative w-full max-w-sm">
        <div className="mb-6 flex items-center gap-3">
          <span
            aria-hidden
            className="grid size-11 place-items-center rounded-2xl text-base font-bold text-[var(--on-primary)]"
            style={{ background: "linear-gradient(135deg, var(--primary), var(--glow))" }}
          >
            IA
          </span>
          <div>
            <h1 className="text-lg font-semibold">IAA 投放运营中台</h1>
            <p className="text-sm text-muted">作品集演示 · 数据均为虚构</p>
          </div>
        </div>

        <form onSubmit={submit} className="card space-y-4 p-6">
          <label className="grid gap-1.5 text-sm">
            账号
            <input className="control" autoComplete="username" required value={username} onChange={(event) => setUsername(event.target.value)} />
          </label>
          <label className="grid gap-1.5 text-sm">
            口令
            <input
              className="control"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error && (
            <p className="text-sm text-bad" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting ? <LoaderCircle className="size-4 animate-spin" aria-hidden /> : <KeyRound className="size-4" aria-hidden />}
            登录
          </button>
        </form>

        {DEMO_ACCOUNTS.length > 0 && (
          <section className="card mt-4 p-4 text-sm" aria-label="演示账号">
            <p className="mb-2 text-xs text-muted">本机演示账号（虚构数据，口令 {DEMO_PASSWORD}）</p>
            <div className="flex flex-wrap gap-1.5">
              {DEMO_ACCOUNTS.map((account) => (
                <button
                  key={account.username}
                  type="button"
                  className="chip"
                  title={account.role}
                  onClick={() => {
                    setUsername(account.username);
                    setPassword(DEMO_PASSWORD);
                  }}
                >
                  {account.role}
                </button>
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
