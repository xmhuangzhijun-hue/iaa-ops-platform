import { Button, Col, Form, Input, InputNumber, Row, Select, Space } from "antd";
import { useState } from "react";
import type { CampaignFilters } from "../api/draft";
import {
  AGENCIES, CALLBACK_RULES, CONVERT_RULES, CONVERT_TARGETS, COST_TYPES, CREATIVE_STYLES, CREATIVE_TYPES,
  FRESHNESS, LANDINGS, PLATFORMS, PRODUCTS, SLOTS, STRATEGIES, SUB_PLATFORMS, TEAMS, VENDORS, YES_NO, options,
} from "../lib/vocabulary";

type Props = { value: CampaignFilters; onApply: (filters: CampaignFilters) => void; loading?: boolean };

const PRIMARY: { name: keyof CampaignFilters; label: string; values: readonly string[] }[] = [
  { name: "products", label: "投放产品", values: PRODUCTS },
  { name: "vendors", label: "厂商", values: VENDORS },
  { name: "platforms", label: "投放平台", values: PLATFORMS },
  { name: "subPlatforms", label: "子平台", values: SUB_PLATFORMS },
  { name: "landings", label: "落地页", values: LANDINGS },
  { name: "agencies", label: "代理", values: AGENCIES },
];

const ADVANCED: { name: keyof CampaignFilters; label: string; values: readonly string[] }[] = [
  { name: "teams", label: "投放组", values: TEAMS },
  { name: "convertRules", label: "转化规则", values: CONVERT_RULES },
  { name: "callbackRules", label: "回传规则", values: CALLBACK_RULES },
  { name: "convertTargets", label: "转化目标", values: CONVERT_TARGETS },
  { name: "freshness", label: "账号鲜度", values: FRESHNESS },
  { name: "costTypes", label: "消耗类型", values: COST_TYPES },
  { name: "strategies", label: "投放策略", values: STRATEGIES },
  { name: "creativeTypes", label: "素材分类", values: CREATIVE_TYPES },
  { name: "creativeStyles", label: "素材样式", values: CREATIVE_STYLES },
  { name: "slots", label: "版位", values: SLOTS },
  { name: "playable", label: "试玩", values: YES_NO },
  { name: "interaction", label: "轻互动", values: YES_NO },
];

export function CampaignFilterForm({ value, onApply, loading }: Props) {
  const [form] = Form.useForm<CampaignFilters>();
  const [expanded, setExpanded] = useState(false);

  const submit = (values: CampaignFilters) => onApply({ ...value, ...values });
  const reset = () => {
    form.resetFields();
    onApply({});
  };

  return (
    <Form form={form} layout="vertical" size="small" initialValues={value} onFinish={submit} className="card p-3">
      <Row gutter={[12, 0]}>
        {PRIMARY.map((field) => (
          <Col key={field.name} xs={12} sm={8} lg={4}>
            <Form.Item name={field.name} label={field.label} className="mb-2">
              <Select mode="multiple" allowClear maxTagCount="responsive" options={options(field.values)} placeholder="全部" />
            </Form.Item>
          </Col>
        ))}
        {expanded &&
          ADVANCED.map((field) => (
            <Col key={field.name} xs={12} sm={8} lg={4}>
              <Form.Item name={field.name} label={field.label} className="mb-2">
                <Select mode="multiple" allowClear maxTagCount="responsive" options={options(field.values)} placeholder="全部" />
              </Form.Item>
            </Col>
          ))}
        <Col xs={24} sm={16} lg={6}>
          <Form.Item name="keyword" label="推广 ID / 渠道 / 广告主" className="mb-2">
            <Input allowClear placeholder="输入关键词" />
          </Form.Item>
        </Col>
        <Col xs={12} sm={8} lg={3}>
          <Form.Item name="costMin" label="消耗 ≥" className="mb-2">
            <InputNumber className="w-full" min={0} placeholder="不限" />
          </Form.Item>
        </Col>
        <Col xs={12} sm={8} lg={3}>
          <Form.Item name="costMax" label="消耗 <" className="mb-2">
            <InputNumber className="w-full" min={0} placeholder="不限" />
          </Form.Item>
        </Col>
        <Col xs={12} sm={8} lg={3}>
          <Form.Item name="enabled" label="投放状态" className="mb-2">
            <Select
              allowClear
              placeholder="全部"
              options={[
                { value: true, label: "投放中" },
                { value: false, label: "已暂停" },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>
      <Space>
        <Button type="primary" htmlType="submit" loading={loading}>
          统计
        </Button>
        <Button onClick={reset}>重置</Button>
        <Button type="link" onClick={() => setExpanded(!expanded)}>
          {expanded ? "收起筛选" : `展开更多筛选（${ADVANCED.length}）`}
        </Button>
      </Space>
    </Form>
  );
}
