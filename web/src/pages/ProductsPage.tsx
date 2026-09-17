import { Input, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import type { Product } from "../api/draft";
import { useProducts } from "../api/draftHooks";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";

const STATUS: Record<Product["status"], { color: string; label: string }> = {
  online: { color: "green", label: "在投" },
  paused: { color: "orange", label: "暂停" },
  offline: { color: "default", label: "下线" },
};

export function ProductsPage() {
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const query = useProducts(keyword, { page, pageSize: 20 });

  const columns: ColumnsType<Product> = [
    { title: "产品", dataIndex: "name", width: 140 },
    { title: "包名", dataIndex: "packageName", width: 190, render: (value: string) => <code className="text-xs text-muted">{value}</code> },
    { title: "品类", dataIndex: "category", width: 100, render: (value: string) => <Tag>{value}</Tag> },
    { title: "厂商", dataIndex: "vendor", width: 90 },
    { title: "跳转产品", dataIndex: "jumpProduct", width: 130, render: (value: string | null) => value ?? "—" },
    { title: "负责人", dataIndex: "owner", width: 100 },
    { title: "上线日期", dataIndex: "onlineAt", width: 120 },
    {
      title: "状态", dataIndex: "status", width: 90,
      render: (status: Product["status"]) => <Tag color={STATUS[status].color}>{STATUS[status].label}</Tag>,
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader title="产品列表" description="投放与变现的共同主体：包名决定数据归属，跳转产品影响归因链路。" />
      <div className="card p-3">
        <Input.Search
          className="mb-3 max-w-72"
          allowClear
          placeholder="搜索产品或包名"
          onSearch={(value) => {
            setKeyword(value);
            setPage(1);
          }}
        />
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <Table<Product>
            size="small"
            rowKey="id"
            loading={query.isFetching}
            dataSource={query.data?.items ?? []}
            columns={columns}
            pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage, showTotal: (total) => `共 ${total} 个产品` }}
            scroll={{ x: 1000 }}
          />
        )}
      </div>
    </div>
  );
}
