import { Download, LoaderCircle } from "lucide-react";
import { useState } from "react";
import { api, problemMessage, unwrap, type Schemas } from "../api/client";
import { downloadBlob, filenameFromDisposition } from "../lib/download";

type Props = { view: "aggregate" | "daily" | "raw"; body: Schemas["ReportQuery"]; disabled?: boolean };

export function ExportButton({ view, body, disabled }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      const pending = api.POST("/api/v1/reports/export", { params: { query: { view } }, body, parseAs: "blob" });
      const { response } = await pending;
      const blob = await unwrap(pending);
      downloadBlob(blob, filenameFromDisposition(response.headers.get("content-disposition")) ?? `iaa-${view}.csv`);
    } catch (reason) {
      setError(problemMessage(reason));
    } finally {
      setBusy(false);
    }
  };

  return (
    <span className="inline-flex items-center gap-2">
      {error && <span className="text-xs text-bad" role="alert">{error}</span>}
      <button type="button" className="control" onClick={run} disabled={busy || disabled} title="与当前页面相同的查询与范围">
        {busy ? <LoaderCircle className="size-4 animate-spin" aria-hidden /> : <Download className="size-4" aria-hidden />}
        导出 CSV
      </button>
    </span>
  );
}
