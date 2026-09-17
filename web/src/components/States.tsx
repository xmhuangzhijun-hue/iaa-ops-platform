import { CircleAlert, Inbox, LoaderCircle } from "lucide-react";
import { problemMessage } from "../api/client";

export function FullScreenLoading() {
  return (
    <div className="grid min-h-dvh place-items-center text-muted" role="status">
      <LoaderCircle className="size-6 animate-spin" aria-label="加载中" />
    </div>
  );
}

export function ErrorBlock({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="card flex flex-wrap items-center gap-3 p-4 text-sm" role="alert">
      <CircleAlert className="size-5 text-bad" aria-hidden />
      <span className="flex-1">{problemMessage(error)}</span>
      {onRetry && (
        <button type="button" className="control" onClick={onRetry}>
          重试
        </button>
      )}
    </div>
  );
}

export function EmptyBlock({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="card grid place-items-center gap-2 px-4 py-12 text-center">
      <Inbox className="size-8 text-muted" aria-hidden />
      <p className="font-medium">{title}</p>
      {children && <div className="max-w-md text-sm text-muted">{children}</div>}
    </div>
  );
}
