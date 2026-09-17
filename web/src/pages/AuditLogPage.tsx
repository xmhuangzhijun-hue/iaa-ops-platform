import { Button, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import { type AuditEvent, useAuditEvents } from "../api/adminHooks";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";

const PAGE_SIZE = 50;

const ACTION_LABELS: Record<string, string> = {
  "mapping.upsert": "账户归属修改",
  "user.create": "创建账号",
  "user.roles.update": "修改角色与范围",
  "import.create": "上传导入文件",
};

const ACTION_OPTIONS = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }));

/**
 * 审计日志：写操作留痕，只追加。
 *
 * 用游标翻页而不是页码：日志一直在写，按页码翻会把新写入的挤进已看过的页里，
 * 造成重复或漏看；游标锁住"从上一条之后继续"。
 */
export function AuditLogPage() {
  const [action, setAction] = useState("");
  const [cursor, setCursor] = useState<string>();
  const [trail, setTrail] = useState<string[]>([]);

  const query = useAuditEvents(action, cursor, PAGE_SIZE);
  const events = query.data?.items ?? [];

  const reset = (nextAction: string) => {
    setAction(nextAction);
    setCursor(undefined);
    setTrail([]);
  };

  const columns: ColumnsType<AuditEvent> = [
    {
      title: "时间", dataIndex: "occurred_at", width: 180, fixed: "left",
      render: (value: string) => new Date(value).toLocaleString("zh-CN", { hour12: false }),
    },
    { title: "操作人", dataIndex: "actor", width: 140 },
    {
      title: "动作", dataIndex: "action", width: 160,
      render: (value: string) => <Tag color="blue">{ACTION_LABELS[value] ?? value}</Tag>,
    },
    { title: "对象", dataIndex: "target_id", width: 220, render: (value: string) => <code className="text-xs">{value}</code> },
    {
      title: "前后值", dataIndex: "detail",
      render: (detail: AuditEvent["detail"]) => (
        <span className="text-xs">
          {Object.entries(detail)
            .filter(([, value]) => value !== null && value !== undefined && value !== "")
            .map(([key, value]) => `${key}=${typeof value === "object" ? JSON.stringify(value) : String(value)}`)
            .join("　")}
        </span>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="审计日志"
        description="谁在什么时候把什么改成了什么。日志只追加，不提供修改与删除——能改的记录不叫审计。"
      />
      <div className="card p-3">
        <Space className="mb-3" wrap>
          <Select allowClear className="w-48" placeholder="全部动作" value={action || undefined}
            options={ACTION_OPTIONS} onChange={(value) => reset(value ?? "")} />
          <Button onClick={() => void query.refetch()} loading={query.isFetching}>刷新</Button>
        </Space>
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <>
            <Table<AuditEvent>
              size="small" rowKey="id" loading={query.isFetching} columns={columns}
              dataSource={events} scroll={{ x: 900 }} pagination={false}
            />
            <div className="mt-3 flex items-center justify-between">
              <span className="text-muted text-xs">
                {trail.length > 0 ? `第 ${trail.length + 1} 页` : "第 1 页"}，每页 {PAGE_SIZE} 条
              </span>
              <Space>
                <Button disabled={trail.length === 0} onClick={() => {
                  const previous = [...trail];
                  previous.pop();
                  setTrail(previous);
                  setCursor(previous.at(-1));
                }}>上一页</Button>
                <Button type="primary" disabled={!query.data?.next_cursor} onClick={() => {
                  const next = query.data?.next_cursor;
                  if (!next) return;
                  setTrail((current) => [...current, next]);
                  setCursor(next);
                }}>下一页</Button>
              </Space>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
