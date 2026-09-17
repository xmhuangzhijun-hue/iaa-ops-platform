import { LoaderCircle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { problemMessage } from "../api/client";
import { useAuth, usePrincipal } from "../auth/AuthProvider";

const MIN_LENGTH = 10;

export function ChangePasswordPage() {
  const principal = usePrincipal();
  const { changePassword, logout } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ current: "", next: "", confirm: "" });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const localError =
    form.next && form.next.length < MIN_LENGTH ? `新口令至少 ${MIN_LENGTH} 位`
      : form.confirm && form.next !== form.confirm ? "两次输入的新口令不一致"
        : form.next && form.next === form.current ? "新口令不能与当前口令相同"
          : null;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (localError || !form.current || !form.next) return;
    setSubmitting(true);
    setError(null);
    try {
      await changePassword(form.current, form.next);
      navigate("/", { replace: true });
    } catch (reason) {
      setError(problemMessage(reason));
    } finally {
      setSubmitting(false);
    }
  };

  const field = (key: keyof typeof form, label: string, autoComplete: string) => (
    <label className="grid gap-1.5 text-sm">
      {label}
      <input
        className="control"
        type="password"
        autoComplete={autoComplete}
        required
        value={form[key]}
        onChange={(event) => setForm({ ...form, [key]: event.target.value })}
      />
    </label>
  );

  return (
    <div className="relative grid min-h-dvh place-items-center px-4 py-10">
      <div className="app-ambient" aria-hidden />
      <main className="relative w-full max-w-sm">
        <h1 className="mb-1 text-lg font-semibold">修改口令</h1>
        <p className="mb-5 text-sm text-muted">
          {principal.must_change_password ? "你正在使用临时口令，修改后才能继续使用。" : `为 ${principal.display_name} 设置新口令。`}
        </p>
        <form onSubmit={submit} className="card space-y-4 p-6">
          {field("current", "当前口令", "current-password")}
          {field("next", "新口令", "new-password")}
          {field("confirm", "再次输入新口令", "new-password")}
          {(localError || error) && (
            <p className="text-sm text-bad" role="alert">
              {localError ?? error}
            </p>
          )}
          <button type="submit" className="btn-primary w-full" disabled={submitting || Boolean(localError)}>
            {submitting && <LoaderCircle className="size-4 animate-spin" aria-hidden />}
            保存新口令
          </button>
          <button type="button" className="w-full text-sm text-muted" onClick={logout}>
            退出登录
          </button>
        </form>
      </main>
    </div>
  );
}
