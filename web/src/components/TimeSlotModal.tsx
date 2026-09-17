import { Modal, Select, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import type { TimelineRow } from "../api/draft";
import { useTimeline } from "../api/draftHooks";
import { CompareCell } from "./CompareCell";

type MetricKey = "cost" | "quality" | "ctr" | "cvr" | "cpm";
const METRICS: { key: MetricKey; label: string; unit: "money" | "ratio"; precision: number }[] = [
  { key: "cost", label: "消耗", unit: "money", precision: 2 },
  { key: "quality", label: "质量分", unit: "money", precision: 2 },
  { key: "ctr", label: "点击率", unit: "ratio", precision: 4 },
  { key: "cvr", label: "CVR", unit: "ratio", precision: 4 },
  { key: "cpm", label: "CPM", unit: "money", precision: 2 },
];

/** 原型里的「时段」弹窗：按小时拆分，每个指标带今日与昨日对比。 */
export function TimeSlotModal({ campaignId, onClose }: { campaignId: string | null; onClose: () => void }) {
  const [selected, setSelected] = useState<MetricKey[]>(["cost", "quality", "ctr", "cvr", "cpm"]);
  const timeline = useTimeline(campaignId);

  const columns: ColumnsType<TimelineRow> = [
    { title: "时段", dataIndex: "bucket", fixed: "left", width: 150 },
    ...METRICS.filter((metric) => selected.includes(metric.key)).map((metric) => ({
      title: metric.label,
      dataIndex: metric.key,
      align: "right" as const,
      width: 120,
      render: (_: unknown, row: TimelineRow) => (
        <CompareCell data={row[metric.key]} unit={metric.unit} precision={metric.precision} />
      ),
    })),
  ];

  return (
    <Modal
      open={Boolean(campaignId)}
      onCancel={onClose}
      footer={null}
      width={860}
      title={`推广 ${campaignId ?? ""} · 时段分析`}
    >
      <div className="mb-3 flex items-center gap-2">
        <span className="text-xs text-muted">指标</span>
        <Select
          mode="multiple"
          className="min-w-64"
          value={selected}
          onChange={(value) => setSelected(value.length ? value : ["cost"])}
          options={METRICS.map((metric) => ({ value: metric.key, label: metric.label }))}
          maxTagCount="responsive"
        />
      </div>
      <Table<TimelineRow>
        size="small"
        rowKey="bucket"
        loading={timeline.isLoading}
        dataSource={timeline.data?.items ?? []}
        columns={columns}
        pagination={false}
        scroll={{ x: 700, y: 420 }}
      />
      <p className="mt-2 text-xs text-muted">每格上方为当日值，下方为与昨日同时段的环比。</p>
    </Modal>
  );
}
