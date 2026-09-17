import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { Category } from "../api/draft";
import { useCategories } from "../api/draftHooks";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";

export function CategoriesPage() {
  const query = useCategories();
  const columns: ColumnsType<Category> = [
    { title: "品类", dataIndex: "name", width: 140 },
    { title: "产品数", dataIndex: "productCount", width: 100, align: "right" },
    { title: "说明", dataIndex: "remark" },
  ];

  return (
    <div className="space-y-4">
      <PageHeader title="品类管理" description="品类是聚合维度之一，用于横向比较不同类型快应用的回收表现。" />
      <div className="card p-3">
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <Table<Category>
            size="small"
            rowKey="id"
            loading={query.isLoading}
            dataSource={query.data?.items ?? []}
            columns={columns}
            pagination={false}
          />
        )}
      </div>
    </div>
  );
}
