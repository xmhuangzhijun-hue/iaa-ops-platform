import { Segmented, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import type { FieldMapping } from "../api/draft";
import { useFieldMappings } from "../api/draftHooks";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { options, PLATFORMS } from "../lib/vocabulary";

const STATUS: Record<FieldMapping["status"], { color: string; label: string }> = {
  mapped: { color: "green", label: "已映射" },
  unmapped: { color: "orange", label: "未映射" },
  conflict: { color: "red", label: "冲突" },
};

export function FieldMappingsPage() {
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [status, setStatus] = useState("全部");
  const query = useFieldMappings(platforms, status === "全部" ? undefined : status, { page: 1, pageSize: 100 });

  const columns: ColumnsType<FieldMapping> = [
    { title: "平台", dataIndex: "platform", width: 120 },
    { title: "上游字段", dataIndex: "sourceField", width: 180, render: (value: string) => <code className="text-xs">{value}</code> },
    { title: "统一字段", dataIndex: "targetField", width: 160, render: (value: string) => <code className="text-xs">{value}</code> },
    { title: "字段含义", dataIndex: "targetLabel", width: 120 },
    { title: "转换", dataIndex: "transform", width: 110, render: (value: string | null) => value ?? "—" },
    {
      title: "状态", dataIndex: "status", width: 100,
      render: (value: FieldMapping["status"]) => <Tag color={STATUS[value].color}>{STATUS[value].label}</Tag>,
    },
    { title: "更新时间", dataIndex: "updatedAt", width: 120 },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="字段映射"
        description="各媒体字段名与口径不同，统一映射到同一套字段后才能跨平台比较；未映射或冲突的字段会让指标口径漂移。"
      />
      <div className="card p-3">
        <Space className="mb-3" wrap>
          <Select
            mode="multiple"
            allowClear
            className="min-w-56"
            placeholder="投放平台"
            maxTagCount="responsive"
            options={options(PLATFORMS)}
            value={platforms}
            onChange={setPlatforms}
          />
          <Segmented
            value={status}
            onChange={(value) => setStatus(String(value))}
            options={["全部", "mapped", "unmapped", "conflict"].map((value) => ({
              value,
              label: value === "全部" ? "全部" : STATUS[value as FieldMapping["status"]].label,
            }))}
          />
        </Space>
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <Table<FieldMapping>
            size="small"
            rowKey="id"
            loading={query.isFetching}
            dataSource={query.data?.items ?? []}
            columns={columns}
            pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条映射` }}
            scroll={{ x: 1000 }}
          />
        )}
      </div>
    </div>
  );
}
