import { App, Button, Form, InputNumber, Modal, Radio, Select, Space, Table, Tag } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { SorterResult } from "antd/es/table/interface";
import { useState } from "react";
import type { CampaignFilters, CampaignRevenueRow } from "../api/draft";
import { useBulkCallback, useBulkRules, useCampaigns, type TableQuery } from "../api/draftHooks";
import { CampaignFilterForm } from "../components/CampaignFilterForm";
import { CompareCell } from "../components/CompareCell";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { formatMetric } from "../lib/format";
import { CALLBACK_LEVELS, CALLBACK_RULES, CONVERT_RULES, options } from "../lib/vocabulary";

const money = (value: number | null) => formatMetric(value, "money", 2);
const ratio = (value: number | null) => formatMetric(value, "ratio", 4);

export function CampaignsTodayPage() {
  const { message } = App.useApp();
  const [filters, setFilters] = useState<CampaignFilters>({});
  const [table, setTable] = useState<TableQuery>({ page: 1, pageSize: 20, sortField: "cost", sortOrder: "desc" });
  const [selected, setSelected] = useState<string[]>([]);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [callbackOpen, setCallbackOpen] = useState(false);
  const [ruleForm] = Form.useForm();
  const [callbackForm] = Form.useForm();

  const query = useCampaigns<CampaignRevenueRow>("revenue", filters, table);
  const bulkRules = useBulkRules();
  const bulkCallback = useBulkCallback();

  const columns: ColumnsType<CampaignRevenueRow> = [
    { title: "ID", dataIndex: "id", fixed: "left", width: 96, sorter: true },
    { title: "PM 收益", dataIndex: "pmIncome", width: 110, align: "right", sorter: true, render: money },
    { title: "CPC 收益", dataIndex: "cpcIncome", width: 110, align: "right", sorter: true, render: money },
    {
      title: "总 ROI", dataIndex: "totalRoi", width: 110, align: "right",
      render: (_: unknown, row) => <CompareCell data={row.totalRoi} unit="ratio" precision={4} />,
    },
    { title: "消耗", dataIndex: "cost", width: 110, align: "right", sorter: true, render: money },
    {
      title: "消耗同比", dataIndex: "costYoy", width: 100, align: "right",
      render: (value: number | null) => <span className={value !== null && value < 0 ? "text-bad" : "text-good"}>{ratio(value)}</span>,
    },
    { title: "消耗差值", dataIndex: "costDiff", width: 110, align: "right", render: money },
    { title: "成本", dataIndex: "realCost", width: 110, align: "right", render: money },
    { title: "代投成本", dataIndex: "agentCost", width: 100, align: "right", render: money },
    { title: "赔付金", dataIndex: "payout", width: 90, align: "right", render: money },
    { title: "星火激励", dataIndex: "spark", width: 100, align: "right", render: money },
    { title: "余额", dataIndex: "balance", width: 110, align: "right", render: money },
    {
      title: "预算", dataIndex: "settings", width: 100, align: "right",
      render: (settings: CampaignRevenueRow["settings"]) => (settings.budget === null ? "不限" : money(settings.budget)),
    },
    { title: "应回传", dataIndex: "shouldCallback", width: 90, align: "right", sorter: true },
    { title: "回传数", dataIndex: "callbacked", width: 90, align: "right" },
    { title: "回传 ARPU", dataIndex: "callbackArpu", width: 110, align: "right", render: (value: number | null) => formatMetric(value, "per_unit", 3) },
    { title: "转化回传比", dataIndex: "convCallbackRatio", width: 110, align: "right", render: ratio },
    { title: "5 分内回传", dataIndex: "in5min", width: 110, align: "right", render: ratio },
    { title: "补回传数", dataIndex: "extraCallback", width: 100, align: "right" },
    {
      title: "转化规则", dataIndex: "settings", width: 150,
      render: (settings: CampaignRevenueRow["settings"]) => <Tag>{settings.convertRule}</Tag>,
    },
    {
      title: "回传规则", dataIndex: "settings", width: 120,
      render: (settings: CampaignRevenueRow["settings"]) => (
        <Tag color="blue">{settings.callbackRule} · {settings.callbackLevel}</Tag>
      ),
    },
    {
      title: "出价", dataIndex: "settings", width: 90, align: "right",
      render: (settings: CampaignRevenueRow["settings"]) => money(settings.bid),
    },
    {
      title: "转化目标", dataIndex: "settings", width: 100,
      render: (settings: CampaignRevenueRow["settings"]) => settings.convertTarget,
    },
    {
      title: "状态", dataIndex: "settings", fixed: "right", width: 90,
      render: (settings: CampaignRevenueRow["settings"]) =>
        settings.enabled ? <Tag color="green">投放中</Tag> : <Tag>已暂停</Tag>,
    },
  ];

  const onChange = (pagination: TablePaginationConfig, _: unknown, sorter: SorterResult<CampaignRevenueRow> | SorterResult<CampaignRevenueRow>[]) => {
    const active = Array.isArray(sorter) ? sorter[0] : sorter;
    setTable({
      page: pagination.current ?? 1,
      pageSize: pagination.pageSize ?? 20,
      sortField: active?.order ? String(active.field) : undefined,
      sortOrder: active?.order === "ascend" ? "asc" : active?.order === "descend" ? "desc" : undefined,
    });
  };

  const submitRules = async () => {
    const values = await ruleForm.validateFields();
    const patch = Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined && value !== null));
    if (!Object.keys(patch).length) {
      message.warning("至少修改一项");
      return;
    }
    const result = await bulkRules.mutateAsync({ ids: selected, ...patch });
    message.success(`已修改 ${result.updated} 条推广的规则`);
    setRuleOpen(false);
    ruleForm.resetFields();
    setSelected([]);
  };

  const submitCallback = async () => {
    const values = await callbackForm.validateFields();
    const result = await bulkCallback.mutateAsync({ ids: selected, mode: values.mode, hours: values.hours });
    message.success(`已提交回传任务 ${result.taskId}，预计补回传 ${result.estimated} 条`);
    setCallbackOpen(false);
    setSelected([]);
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title="今日推广"
        description="收益与回传视角：收益构成、成本项与回传达成；支持批量改规则与批量补回传。"
        actions={
          <Space>
            <Button disabled={!selected.length} onClick={() => setRuleOpen(true)}>
              批量修改规则{selected.length ? `（${selected.length}）` : ""}
            </Button>
            <Button type="primary" disabled={!selected.length} onClick={() => setCallbackOpen(true)}>
              推广批量回传{selected.length ? `（${selected.length}）` : ""}
            </Button>
          </Space>
        }
      />
      <CampaignFilterForm value={filters} onApply={(next) => { setFilters(next); setTable({ ...table, page: 1 }); }} loading={query.isFetching} />
      {query.isError ? (
        <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
      ) : (
        <div className="card p-3">
          <div className="mb-2 text-sm text-muted">
            共 <b className="text-text tabular-nums">{query.data?.total ?? 0}</b> 条，投放中{" "}
            <b className="text-text tabular-nums">{query.data?.summary?.enabled ?? 0}</b> 条，合计消耗{" "}
            <b className="text-text tabular-nums">{money(query.data?.summary?.cost ?? null)}</b>
          </div>
          <Table<CampaignRevenueRow>
            size="small"
            rowKey="id"
            loading={query.isFetching}
            dataSource={query.data?.items ?? []}
            columns={columns}
            onChange={onChange}
            scroll={{ x: 2600 }}
            rowSelection={{ selectedRowKeys: selected, onChange: (keys) => setSelected(keys as string[]) }}
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

      <Modal
        title={`批量修改规则（${selected.length} 条）`}
        open={ruleOpen}
        onCancel={() => setRuleOpen(false)}
        onOk={submitRules}
        confirmLoading={bulkRules.isPending}
        okText="确认修改"
      >
        <Form form={ruleForm} layout="vertical" className="pt-2">
          <Form.Item name="convertRule" label="转化规则">
            <Select allowClear placeholder="不修改" options={options(CONVERT_RULES)} />
          </Form.Item>
          <Form.Item name="callbackRule" label="回传规则">
            <Select allowClear placeholder="不修改" options={options(CALLBACK_RULES)} />
          </Form.Item>
          <Form.Item name="callbackLevel" label="回传级别">
            <Select allowClear placeholder="不修改" options={options(CALLBACK_LEVELS)} />
          </Form.Item>
          <Form.Item name="bid" label="出价">
            <InputNumber className="w-full" min={0.1} max={50} step={0.1} placeholder="不修改" />
          </Form.Item>
          <Form.Item name="enabled" label="投放状态">
            <Select
              allowClear
              placeholder="不修改"
              options={[
                { value: true, label: "开启投放" },
                { value: false, label: "暂停投放" },
              ]}
            />
          </Form.Item>
          <p className="text-xs text-muted">留空的项不修改。规则变更会记入配置版本，用于解释当天数据变化。</p>
        </Form>
      </Modal>

      <Modal
        title={`推广批量回传（${selected.length} 条）`}
        open={callbackOpen}
        onCancel={() => setCallbackOpen(false)}
        onOk={submitCallback}
        confirmLoading={bulkCallback.isPending}
        okText="提交回传"
      >
        <Form form={callbackForm} layout="vertical" initialValues={{ mode: "missing", hours: 24 }} className="pt-2">
          <Form.Item name="mode" label="回传范围">
            <Radio.Group>
              <Radio value="missing">只补未回传的转化</Radio>
              <Radio value="all">全部重新回传</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="hours" label="回溯小时数">
            <InputNumber className="w-full" min={1} max={72} />
          </Form.Item>
          <p className="text-xs text-muted">回传会实际发往媒体，无法撤回；提交后在「回传记录」查看结果。</p>
        </Form>
      </Modal>
    </div>
  );
}
