import { App, Button, Input, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { type AccountMapping, useAccountMappings, useUpsertMappings } from "../api/adminHooks";
import { problemMessage } from "../api/client";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { AGENCIES, MEDIA_OPTIONS, OWNERS, PRODUCTS, options } from "../lib/vocabulary";

type Draft = Pick<AccountMapping, "agency" | "product" | "operator">;

const PAGE_SIZE = 20;

/** 选项以字典为准，但库里已有的值一定要出现在列表里，否则现有归属会被显示成空。 */
const withExisting = (preset: readonly string[], existing: (string | null)[]) =>
  options([...new Set([...preset, ...existing.filter((value): value is string => Boolean(value))])]);

/**
 * 账户分配：把媒体账户归到代理、产品与运营名下。
 *
 * 归属不落在事实表里，改了对历史一并生效——所以这页改的是"谁的账"，不是"今天算谁的"。
 * 保存是整批提交：任一条的 revision 被别人改过，整批都不写，界面刷新后重来。
 */
export function AccountAssignmentsPage() {
  const { message } = App.useApp();
  const [media, setMedia] = useState<string>();
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});

  const query = useAccountMappings(media, keyword, page, PAGE_SIZE);
  const upsert = useUpsertMappings();

  const rows = query.data?.items ?? [];
  const rowKey = (row: AccountMapping) => `${row.media}/${row.account}`;

  const changed = useMemo(
    () => rows.filter((row) => {
      const draft = drafts[rowKey(row)];
      return draft && (draft.agency !== row.agency || draft.product !== row.product || draft.operator !== row.operator);
    }),
    [rows, drafts],
  );

  const edit = (row: AccountMapping, patch: Partial<Draft>) =>
    setDrafts((current) => {
      const base = current[rowKey(row)] ?? { agency: row.agency, product: row.product, operator: row.operator };
      return { ...current, [rowKey(row)]: { ...base, ...patch } };
    });

  const valueOf = (row: AccountMapping, field: keyof Draft) => drafts[rowKey(row)]?.[field] ?? row[field] ?? undefined;

  async function save() {
    try {
      const result = await upsert.mutateAsync(changed.map((row) => ({
        account: row.account,
        media: row.media,
        agency: valueOf(row, "agency") ?? null,
        product: valueOf(row, "product") ?? null,
        operator: valueOf(row, "operator") ?? null,
        revision: row.revision,
      })));
      setDrafts({});
      message.success(`已保存：新增 ${result.created}、修改 ${result.updated}、无变化 ${result.unchanged}`);
    } catch (error) {
      // 409 说明有人抢先改了：清掉草稿并重新取数，让用户在最新版本上重做
      setDrafts({});
      void query.refetch();
      message.error(problemMessage(error));
    }
  }

  const columns: ColumnsType<AccountMapping> = [
    { title: "账户", dataIndex: "account", width: 200, fixed: "left" },
    { title: "媒体", dataIndex: "media", width: 100, render: (value: string) => <Tag>{value}</Tag> },
    {
      title: "代理", dataIndex: "agency", width: 160,
      render: (_, row) => (
        <Select allowClear showSearch className="w-full" placeholder="未分配" options={withExisting(AGENCIES, rows.map((item) => item.agency))}
          value={valueOf(row, "agency")} onChange={(value) => edit(row, { agency: value ?? null })} />
      ),
    },
    {
      title: "产品", dataIndex: "product", width: 160,
      render: (_, row) => (
        <Select allowClear showSearch className="w-full" placeholder="未分配" options={withExisting(PRODUCTS, rows.map((item) => item.product))}
          value={valueOf(row, "product")} onChange={(value) => edit(row, { product: value ?? null })} />
      ),
    },
    {
      title: "运营", dataIndex: "operator", width: 140,
      render: (_, row) => (
        <Select allowClear showSearch className="w-full" placeholder="未分配" options={withExisting(OWNERS, rows.map((item) => item.operator))}
          value={valueOf(row, "operator")} onChange={(value) => edit(row, { operator: value ?? null })} />
      ),
    },
    {
      title: "版本", dataIndex: "revision", width: 80, align: "right",
      render: (value: number) => <span className="text-muted text-xs">v{value}</span>,
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="账户分配"
        description="账户归到哪个代理、产品与运营名下。归属改了对历史报表一并生效；保存整批提交，有人抢先改过就整批不写。"
        actions={
          <Space>
            <Button onClick={() => setDrafts({})} disabled={changed.length === 0}>放弃修改</Button>
            <Button type="primary" loading={upsert.isPending} disabled={changed.length === 0} onClick={() => void save()}>
              保存 {changed.length > 0 ? `(${changed.length})` : ""}
            </Button>
          </Space>
        }
      />
      <div className="card p-3">
        <Space className="mb-3" wrap>
          <Input.Search allowClear placeholder="搜索账户、代理、产品或运营" className="w-72"
            onSearch={(value) => { setKeyword(value); setPage(1); }} />
          <Select allowClear className="w-40" placeholder="媒体" options={MEDIA_OPTIONS}
            value={media} onChange={(value) => { setMedia(value); setPage(1); }} />
        </Space>
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <Table<AccountMapping>
            size="small"
            rowKey={rowKey}
            loading={query.isFetching}
            columns={columns}
            dataSource={rows}
            scroll={{ x: 860 }}
            rowClassName={(row) => (changed.some((item) => rowKey(item) === rowKey(row)) ? "bg-accent-soft" : "")}
            pagination={{
              current: page, pageSize: PAGE_SIZE, total: query.data?.total ?? 0,
              showSizeChanger: false, onChange: setPage,
              showTotal: (total) => `共 ${total} 个账户`,
            }}
          />
        )}
      </div>
    </div>
  );
}
