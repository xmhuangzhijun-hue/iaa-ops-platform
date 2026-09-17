import { App, Badge, Button, Segmented, Space, Switch, Table, Tag, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import type { IngestRun, IngestSource } from "../api/draft";
import { useIngestRuns, useIngestSources, useToggleSource, useTriggerIngest } from "../api/draftHooks";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";

const KIND_LABELS: Record<IngestSource["kind"], string> = { delivery: "投放", revenue: "变现", callback: "回传" };
const STATE_BADGE: Record<string, { status: "success" | "warning" | "error" | "processing" | "default"; text: string }> = {
  succeeded: { status: "success", text: "成功" },
  partial: { status: "warning", text: "部分失败" },
  failed: { status: "error", text: "失败" },
  running: { status: "processing", text: "进行中" },
  never: { status: "default", text: "未运行" },
};

export function IngestPage() {
  const { message } = App.useApp();
  const [stateFilter, setStateFilter] = useState<string>("全部");
  const sources = useIngestSources();
  const runs = useIngestRuns(stateFilter === "全部" ? undefined : stateFilter, { page: 1, pageSize: 20 });
  const toggle = useToggleSource();
  const trigger = useTriggerIngest();

  const sourceColumns: ColumnsType<IngestSource> = [
    { title: "平台", dataIndex: "platform", width: 120 },
    { title: "数据类型", dataIndex: "kind", width: 100, render: (kind: IngestSource["kind"]) => <Tag>{KIND_LABELS[kind]}</Tag> },
    { title: "调度", dataIndex: "cron", width: 120 },
    {
      title: "授权状态", dataIndex: "authStatus", width: 140,
      render: (status: IngestSource["authStatus"], row) =>
        status === "ok" ? (
          <Tooltip title={`到期 ${row.authExpireAt}`}>
            <Tag color="green">正常</Tag>
          </Tooltip>
        ) : status === "expiring" ? (
          <Tooltip title={`到期 ${row.authExpireAt}`}>
            <Tag color="orange">即将过期</Tag>
          </Tooltip>
        ) : (
          <Tag color="red">已失效</Tag>
        ),
    },
    { title: "凭据", dataIndex: "credentialRef", width: 190, render: (value: string) => <code className="text-xs text-muted">{value}</code> },
    { title: "最近一次", dataIndex: "lastRunAt", width: 150 },
    {
      title: "结果", dataIndex: "lastRunState", width: 110,
      render: (state: IngestSource["lastRunState"]) => <Badge {...STATE_BADGE[state]} />,
    },
    { title: "已覆盖到", dataIndex: "coveredUntil", width: 150 },
    {
      title: "启用", dataIndex: "enabled", width: 80,
      render: (enabled: boolean, row) => (
        <Switch
          size="small"
          checked={enabled}
          loading={toggle.isPending}
          onChange={(next) => toggle.mutate({ id: row.id, enabled: next })}
        />
      ),
    },
    {
      title: "操作", dataIndex: "id", fixed: "right", width: 100,
      render: (id: string) => (
        <Button
          type="link"
          size="small"
          loading={trigger.isPending}
          onClick={async () => {
            await trigger.mutateAsync({ sourceId: id, window: "手动补采最近 1 小时" });
            message.success("已提交补采任务");
          }}
        >
          手动补采
        </Button>
      ),
    },
  ];

  const runColumns: ColumnsType<IngestRun> = [
    { title: "任务", dataIndex: "id", width: 150 },
    { title: "平台", dataIndex: "platform", width: 120 },
    { title: "类型", dataIndex: "kind", width: 80, render: (kind: IngestRun["kind"]) => KIND_LABELS[kind] },
    { title: "数据窗口", dataIndex: "window", width: 200 },
    { title: "状态", dataIndex: "state", width: 110, render: (state: IngestRun["state"]) => <Badge {...STATE_BADGE[state]} /> },
    { title: "入库行数", dataIndex: "rows", width: 110, align: "right", render: (value: number) => value.toLocaleString("zh-CN") },
    { title: "错误", dataIndex: "errors", width: 80, align: "right", render: (value: number) => (value ? <span className="text-bad">{value}</span> : 0) },
    { title: "说明", dataIndex: "message", ellipsis: true, render: (value: string | null) => value ?? "—" },
    { title: "开始时间", dataIndex: "startedAt", width: 150 },
    {
      title: "耗时", dataIndex: "durationMs", width: 100, align: "right",
      render: (value: number | null) => (value === null ? "—" : `${(value / 1000).toFixed(1)}s`),
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="数据源与采集"
        description="每个平台按投放、变现、回传三类数据分别采集；这里管调度、授权与失败排查，不是上传页面。"
      />
      {sources.isError ? (
        <ErrorBlock error={sources.error} onRetry={() => void sources.refetch()} />
      ) : (
        <div className="card p-3">
          <h3 className="mb-2 font-medium">数据源</h3>
          <Table<IngestSource>
            size="small"
            rowKey="id"
            loading={sources.isLoading}
            dataSource={sources.data?.items ?? []}
            columns={sourceColumns}
            pagination={false}
            scroll={{ x: 1400 }}
          />
        </div>
      )}
      <div className="card p-3">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-medium">采集任务</h3>
          <Space>
            <Segmented
              size="small"
              value={stateFilter}
              onChange={(value) => setStateFilter(String(value))}
              options={["全部", "succeeded", "partial", "failed"].map((value) => ({
                value,
                label: value === "全部" ? "全部" : STATE_BADGE[value].text,
              }))}
            />
            <Button size="small" onClick={() => void runs.refetch()}>
              刷新
            </Button>
          </Space>
        </div>
        <Table<IngestRun>
          size="small"
          rowKey="id"
          loading={runs.isFetching}
          dataSource={runs.data?.items ?? []}
          columns={runColumns}
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 次` }}
          scroll={{ x: 1300 }}
        />
      </div>
    </div>
  );
}
