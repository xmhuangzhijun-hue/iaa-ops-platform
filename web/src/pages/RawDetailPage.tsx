import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { api, unwrap, type Schemas } from "../api/client";
import { DataGrid } from "../components/DataGrid";
import { ExportButton } from "../components/ExportButton";
import { PageHeader } from "../components/PageHeader";
import { RowCount } from "../components/RowCount";
import { ErrorBlock } from "../components/States";
import { FilterBar } from "../filters/FilterBar";
import { queryBase, useFilters } from "../filters/FilterProvider";

const PAGE_SIZE = 100;

export function RawDetailPage({ embedded = false }: { embedded?: boolean }) {
  const { filters, refreshToken } = useFilters();
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<Schemas["SortSpec"][]>([]);

  useEffect(() => setPage(1), [filters]);

  const base = { ...queryBase(filters), keyword: filters.keyword || null, sort, page: 1, page_size: PAGE_SIZE };
  const body: Schemas["RawDetailQuery"] = { ...base, page };
  const result = useQuery({
    queryKey: ["report", "raw", body, refreshToken],
    queryFn: () => unwrap(api.POST("/api/v1/reports/raw", { body })),
    placeholderData: keepPreviousData,
  });
  const onServerSort = useCallback((next: Schemas["SortSpec"][]) => {
    setSort(next);
    setPage(1);
  }, []);

  const total = result.data?.total_rows ?? 0;
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="space-y-4">
      {!embedded && <PageHeader
        title="原始明细"
        description="不做汇总的入库明细，用于核对导入数据；关键词匹配账户、计划与产品。"
        asOf={result.data?.data_as_of}
        actions={<ExportButton view="raw" body={base} disabled={!result.data} />}
      />}
      {embedded && <div className="workspace-data-toolbar"><span>入库明细 · {result.data?.data_as_of ? `更新于 ${new Date(result.data.data_as_of).toLocaleString("zh-CN")}` : "读取数据中"}</span><ExportButton view="raw" body={base} disabled={!result.data} /></div>}
      <FilterBar keywordPlaceholder="搜索账户、计划或产品" />
      {result.isError ? (
        <ErrorBlock error={result.error} onRetry={() => void result.refetch()} />
      ) : (
        <>
          <DataGrid
            columns={result.data?.columns ?? []}
            rows={result.data?.rows ?? []}
            totals={result.data?.totals}
            loading={result.isFetching}
            onServerSort={onServerSort}
            height={embedded ? 530 : 600}
            caption={result.data && <RowCount total={total} shown={total} />}
          />
          <nav className="flex items-center justify-end gap-2 text-sm" aria-label="分页">
            <button type="button" className="control" disabled={page <= 1} onClick={() => setPage(page - 1)} aria-label="上一页">
              <ChevronLeft className="size-4" />
            </button>
            <span className="tabular-nums text-muted">
              第 {page} / {pages} 页
            </span>
            <button type="button" className="control" disabled={page >= pages} onClick={() => setPage(page + 1)} aria-label="下一页">
              <ChevronRight className="size-4" />
            </button>
          </nav>
        </>
      )}
    </div>
  );
}
