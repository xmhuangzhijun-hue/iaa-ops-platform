import { Input, Select, Space, Table, Tag, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import type { MediaAccount } from "../api/draft";
import { useMediaAccounts } from "../api/draftHooks";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { formatMetric } from "../lib/format";
import { options, PLATFORMS } from "../lib/vocabulary";

const STATUS: Record<MediaAccount["status"], { color: string; label: string }> = {
  active: { color: "green", label: "正常" },
  paused: { color: "orange", label: "暂停" },
  disabled: { color: "default", label: "停用" },
};

export function MediaAccountsPage() {
  const [keyword, setKeyword] = useState("");
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const query = useMediaAccounts(keyword, platforms, { page, pageSize: 20 });

  const columns: ColumnsType<MediaAccount> = [
    { title: "广告主账户", dataIndex: "advertiser", width: 180, fixed: "left" },
    { title: "投放平台", dataIndex: "platform", width: 110 },
    { title: "子平台", dataIndex: "subPlatform", width: 90 },
    { title: "代理", dataIndex: "agency", width: 100 },
    { title: "归属投手", dataIndex: "owner", width: 100 },
    { title: "投放组", dataIndex: "team", width: 100 },
    { title: "账号鲜度", dataIndex: "freshness", width: 100, render: (value: string) => <Tag>{value}</Tag> },
    {
      title: "余额", dataIndex: "balance", width: 120, align: "right",
      render: (value: number) => (
        <span className={value < 2000 ? "text-bad" : undefined}>{formatMetric(value, "money", 2)}</span>
      ),
    },
    {
      title: "日预算", dataIndex: "dailyBudget", width: 110, align: "right",
      render: (value: number | null) => (value === null ? "不限" : formatMetric(value, "money", 2)),
    },
    {
      title: "采集凭据", dataIndex: "credentialRef", width: 170,
      render: (value: string | null) =>
        value ? (
          <Tooltip title="只登记引用，真实密钥不进入本系统展示层">
            <code className="text-xs text-muted">{value}</code>
          </Tooltip>
        ) : (
          <Tag color="red">未配置</Tag>
        ),
    },
    { title: "最近同步", dataIndex: "lastSyncAt", width: 150, render: (value: string | null) => value ?? "—" },
    {
      title: "状态", dataIndex: "status", width: 90, fixed: "right",
      render: (status: MediaAccount["status"]) => <Tag color={STATUS[status].color}>{STATUS[status].label}</Tag>,
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader title="媒体账户" description="账户是采集与归属的锚点：余额影响投放，凭据决定能不能拉到数据，归属决定谁能看到。" />
      <div className="card p-3">
        <Space className="mb-3" wrap>
          <Input.Search allowClear placeholder="搜索广告主账户" className="w-64" onSearch={(value) => { setKeyword(value); setPage(1); }} />
          <Select
            mode="multiple"
            allowClear
            className="min-w-56"
            placeholder="投放平台"
            maxTagCount="responsive"
            options={options(PLATFORMS)}
            value={platforms}
            onChange={(value) => { setPlatforms(value); setPage(1); }}
          />
        </Space>
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <Table<MediaAccount>
            size="small"
            rowKey="id"
            loading={query.isFetching}
            dataSource={query.data?.items ?? []}
            columns={columns}
            pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, onChange: setPage, showTotal: (total) => `共 ${total} 个账户` }}
            scroll={{ x: 1500 }}
          />
        )}
      </div>
    </div>
  );
}
