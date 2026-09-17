import { Button, Table, Tag } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { SorterResult } from "antd/es/table/interface";
import { useState } from "react";
import type { CampaignDeliveryRow, CampaignFilters } from "../api/draft";
import { useCampaigns, type TableQuery } from "../api/draftHooks";
import { CampaignFilterForm } from "../components/CampaignFilterForm";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { TimeSlotModal } from "../components/TimeSlotModal";
import { formatMetric } from "../lib/format";

const money = (value: number | null) => formatMetric(value, "money", 2);
const ratio = (value: number | null) => formatMetric(value, "ratio", 4);

export function CampaignsPage() {
  const [filters, setFilters] = useState<CampaignFilters>({});
  const [table, setTable] = useState<TableQuery>({ page: 1, pageSize: 20, sortField: "cost", sortOrder: "desc" });
  const [timeSlotId, setTimeSlotId] = useState<string | null>(null);
  const query = useCampaigns<CampaignDeliveryRow>("delivery", filters, table);

  const columns: ColumnsType<CampaignDeliveryRow> = [
    { title: "ID", dataIndex: "id", fixed: "left", width: 96, sorter: true },
    { title: "渠道名称", dataIndex: "channelName", width: 180, ellipsis: true },
    { title: "投放产品", dataIndex: "product", width: 120 },
    { title: "厂商", dataIndex: "vendor", width: 80 },
    { title: "投放平台", dataIndex: "platform", width: 110 },
    { title: "子平台", dataIndex: "subPlatform", width: 90 },
    { title: "广告主", dataIndex: "advertiser", width: 150, ellipsis: true },
    { title: "落地页", dataIndex: "landing", width: 80, render: (value: string) => <Tag>{value}</Tag> },
    { title: "投放备注", dataIndex: "remark", width: 120, ellipsis: true },
    { title: "推广天数", dataIndex: "onlineDays", width: 90, align: "right", sorter: true },
    { title: "消耗", dataIndex: "cost", width: 110, align: "right", sorter: true, render: money },
    {
      title: "消耗同比", dataIndex: "costYoy", width: 100, align: "right", sorter: true,
      render: (value: number | null) => (
        <span className={value !== null && value < 0 ? "text-bad" : "text-good"}>{ratio(value)}</span>
      ),
    },
    { title: "余额", dataIndex: "balance", width: 110, align: "right", sorter: true, render: money },
    {
      title: "预算", dataIndex: "settings", width: 100, align: "right",
      render: (settings: CampaignDeliveryRow["settings"]) => (settings.budget === null ? "不限" : money(settings.budget)),
    },
    { title: "质量分", dataIndex: "quality", width: 90, align: "right", sorter: true, render: money },
    { title: "投放数", dataIndex: "delivered", width: 90, align: "right", sorter: true },
    { title: "投放成本", dataIndex: "deliverCost", width: 100, align: "right", render: money },
    {
      title: "出价", dataIndex: "settings", width: 90, align: "right",
      render: (settings: CampaignDeliveryRow["settings"]) => money(settings.bid),
    },
    { title: "计费比", dataIndex: "costFrame", width: 90, align: "right", render: ratio },
    { title: "曝光量", dataIndex: "exposure", width: 110, align: "right", sorter: true },
    { title: "点击量", dataIndex: "clicks", width: 100, align: "right", sorter: true },
    { title: "CVR", dataIndex: "cvr", width: 90, align: "right", render: ratio },
    { title: "点击率", dataIndex: "ctr", width: 90, align: "right", render: ratio },
    { title: "CPM", dataIndex: "cpm", width: 90, align: "right", render: money },
    { title: "CPC", dataIndex: "cpc", width: 90, align: "right", render: (value: number | null) => formatMetric(value, "per_unit", 4) },
    {
      title: "操作", dataIndex: "id", fixed: "right", width: 84,
      render: (id: string) => (
        <Button type="link" size="small" onClick={() => setTimeSlotId(id)}>
          时段
        </Button>
      ),
    },
  ];

  const onChange = (pagination: TablePaginationConfig, _: unknown, sorter: SorterResult<CampaignDeliveryRow> | SorterResult<CampaignDeliveryRow>[]) => {
    const active = Array.isArray(sorter) ? sorter[0] : sorter;
    setTable({
      page: pagination.current ?? 1,
      pageSize: pagination.pageSize ?? 20,
      sortField: active?.order ? String(active.field) : undefined,
      sortOrder: active?.order === "ascend" ? "asc" : active?.order === "descend" ? "desc" : undefined,
    });
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title="推广列表"
        description="投放视角的推广宽表：档案、当日消耗与转化效率；点「时段」查看按小时拆分。"
      />
      <CampaignFilterForm value={filters} onApply={(next) => { setFilters(next); setTable({ ...table, page: 1 }); }} loading={query.isFetching} />
      {query.isError ? (
        <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
      ) : (
        <div className="card p-3">
          <div className="mb-2 text-sm text-muted">
            共 <b className="text-text tabular-nums">{query.data?.total ?? 0}</b> 条推广，合计消耗{" "}
            <b className="text-text tabular-nums">{money(query.data?.summary?.cost ?? null)}</b>
          </div>
          <Table<CampaignDeliveryRow>
            size="small"
            rowKey="id"
            loading={query.isFetching}
            dataSource={query.data?.items ?? []}
            columns={columns}
            onChange={onChange}
            scroll={{ x: 2400 }}
            pagination={{
              current: table.page,
              pageSize: table.pageSize,
              total: query.data?.total ?? 0,
              showSizeChanger: true,
              pageSizeOptions: [20, 50, 100],
              showTotal: (total) => `共 ${total} 条`,
            }}
          />
        </div>
      )}
      <TimeSlotModal campaignId={timeSlotId} onClose={() => setTimeSlotId(null)} />
    </div>
  );
}
