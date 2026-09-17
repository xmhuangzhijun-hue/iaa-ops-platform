import { App, Alert, Button, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import {
  type Role, type UserCreated, type UserSummary,
  useCreateUser, useUpdateUserRoles, useUsers,
} from "../api/adminHooks";
import { problemMessage } from "../api/client";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { AGENCIES, OWNERS, PRODUCTS, options } from "../lib/vocabulary";

const PAGE_SIZE = 20;

const ROLE_LABELS: Record<Role, string> = {
  super_admin: "超级管理员",
  company_admin: "公司管理员",
  operator: "运营",
  agency_admin: "代理管理员",
  customer: "客户",
  readonly: "只读",
};

const ROLE_OPTIONS = Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }));

type ScopeForm = { agencies?: string[]; products?: string[]; operators?: string[] };
type CreateForm = { username: string; display_name: string; roles: Role[] } & ScopeForm;
type RolesForm = { roles: Role[] } & ScopeForm;

/** 空数组与"不填"含义不同：不填表示不限，空数组表示一个都看不到，这里统一按不限处理。 */
const scopeOf = (form: ScopeForm) => ({
  agencies: form.agencies?.length ? form.agencies : null,
  products: form.products?.length ? form.products : null,
  operators: form.operators?.length ? form.operators : null,
});

export function UsersPage() {
  const { message } = App.useApp();
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<"active" | "disabled">();
  const [page, setPage] = useState(1);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<UserSummary>();
  const [created, setCreated] = useState<UserCreated>();

  const query = useUsers(keyword, status, page, PAGE_SIZE);
  const create = useCreateUser();
  const updateRoles = useUpdateUserRoles();
  const [createForm] = Form.useForm<CreateForm>();
  const [rolesForm] = Form.useForm<RolesForm>();

  async function submitCreate() {
    const values = await createForm.validateFields();
    try {
      const result = await create.mutateAsync({
        username: values.username,
        display_name: values.display_name,
        roles: values.roles,
        data_scope: scopeOf(values),
      });
      setCreating(false);
      createForm.resetFields();
      // 临时口令只在这一次响应里出现，关掉就再也拿不到
      setCreated(result);
    } catch (error) {
      message.error(problemMessage(error));
    }
  }

  async function submitRoles() {
    if (!editing) return;
    const values = await rolesForm.validateFields();
    try {
      await updateRoles.mutateAsync({
        userId: editing.id,
        roles: values.roles,
        data_scope: scopeOf(values),
        revision: editing.revision,
      });
      setEditing(undefined);
      message.success("已修改");
    } catch (error) {
      message.error(problemMessage(error));
    }
  }

  const columns: ColumnsType<UserSummary> = [
    { title: "账号", dataIndex: "username", width: 160, fixed: "left" },
    { title: "姓名", dataIndex: "display_name", width: 120 },
    {
      title: "角色", dataIndex: "roles", width: 200,
      render: (roles: Role[]) => <Space size={4} wrap>{roles.map((role) => <Tag key={role}>{ROLE_LABELS[role]}</Tag>)}</Space>,
    },
    {
      title: "数据范围", dataIndex: "data_scope", width: 300,
      render: (scope: UserSummary["data_scope"]) => {
        const parts = [
          scope.agencies?.length ? `代理：${scope.agencies.join("、")}` : null,
          scope.products?.length ? `产品：${scope.products.join("、")}` : null,
          scope.operators?.length ? `运营：${scope.operators.join("、")}` : null,
        ].filter(Boolean);
        return parts.length ? <span className="text-xs">{parts.join("；")}</span> : <span className="text-muted text-xs">不限</span>;
      },
    },
    {
      title: "状态", dataIndex: "status", width: 90,
      render: (value: UserSummary["status"]) =>
        value === "active" ? <Tag color="green">正常</Tag> : <Tag>停用</Tag>,
    },
    {
      title: "操作", width: 110, fixed: "right",
      render: (_, row) => (
        <Button size="small" onClick={() => {
          setEditing(row);
          rolesForm.setFieldsValue({
            roles: row.roles,
            agencies: row.data_scope.agencies ?? undefined,
            products: row.data_scope.products ?? undefined,
            operators: row.data_scope.operators ?? undefined,
          });
        }}>改角色</Button>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="用户与权限"
        description="角色决定能做什么，数据范围决定能看到谁的数据。谁都不能授出自己没有的权限，也不能把范围放得比自己宽。"
        actions={<Button type="primary" onClick={() => setCreating(true)}>新建账号</Button>}
      />
      <div className="card p-3">
        <Space className="mb-3" wrap>
          <Input.Search allowClear placeholder="搜索账号或姓名" className="w-64"
            onSearch={(value) => { setKeyword(value); setPage(1); }} />
          <Select allowClear className="w-32" placeholder="状态" value={status}
            options={[{ value: "active", label: "正常" }, { value: "disabled", label: "停用" }]}
            onChange={(value) => { setStatus(value); setPage(1); }} />
        </Space>
        {query.isError ? (
          <ErrorBlock error={query.error} onRetry={() => void query.refetch()} />
        ) : (
          <Table<UserSummary>
            size="small" rowKey="id" loading={query.isFetching} columns={columns}
            dataSource={query.data?.items ?? []} scroll={{ x: 1000 }}
            pagination={{
              current: page, pageSize: PAGE_SIZE, total: query.data?.total ?? 0,
              showSizeChanger: false, onChange: setPage, showTotal: (total) => `共 ${total} 个账号`,
            }}
          />
        )}
      </div>

      <Modal title="新建账号" open={creating} onCancel={() => setCreating(false)}
        onOk={() => void submitCreate()} confirmLoading={create.isPending} okText="创建" destroyOnHidden>
        <Form form={createForm} layout="vertical" className="pt-2">
          <Form.Item name="username" label="账号" rules={[
            { required: true, message: "请填账号" },
            { pattern: /^[a-z0-9_.-]{3,32}$/, message: "3-32 位小写字母、数字、下划线、点或连字符" },
          ]}>
            <Input placeholder="demo.operator" />
          </Form.Item>
          <Form.Item name="display_name" label="姓名" rules={[{ required: true, message: "请填姓名" }]}>
            <Input placeholder="张三" />
          </Form.Item>
          <Form.Item name="roles" label="角色" rules={[{ required: true, message: "至少选一个角色" }]}>
            <Select mode="multiple" options={ROLE_OPTIONS} placeholder="真实口径与对外口径不能同时给" />
          </Form.Item>
          <ScopeFields />
        </Form>
      </Modal>

      <Modal title={`改角色 · ${editing?.username ?? ""}`} open={Boolean(editing)} onCancel={() => setEditing(undefined)}
        onOk={() => void submitRoles()} confirmLoading={updateRoles.isPending} okText="保存" destroyOnHidden>
        <Form form={rolesForm} layout="vertical" className="pt-2">
          <Form.Item name="roles" label="角色" rules={[{ required: true, message: "至少选一个角色" }]}>
            <Select mode="multiple" options={ROLE_OPTIONS} />
          </Form.Item>
          <ScopeFields />
          <p className="text-muted text-xs">
            基于版本 v{editing?.revision} 修改；期间被别人改过会提示冲突，需要刷新后重做。
          </p>
        </Form>
      </Modal>

      <Modal title="账号已创建" open={Boolean(created)} onCancel={() => setCreated(undefined)}
        footer={<Button type="primary" onClick={() => setCreated(undefined)}>我已保存</Button>} destroyOnHidden>
        <Alert type="warning" showIcon className="mb-3"
          message="临时口令只显示这一次" description="关掉这个窗口就再也拿不到，请现在转交给本人；对方首次登录必须改密。" />
        <Typography.Paragraph copyable={{ text: created?.one_time_password }} className="text-lg">
          <code>{created?.one_time_password}</code>
        </Typography.Paragraph>
        <p className="text-muted text-sm">账号：{created?.user.username}</p>
      </Modal>
    </div>
  );
}

function ScopeFields() {
  return (
    <>
      <Form.Item name="agencies" label="可见代理" help="留空表示不限">
        <Select mode="multiple" allowClear options={options(AGENCIES)} placeholder="不限" />
      </Form.Item>
      <Form.Item name="products" label="可见产品" help="留空表示不限">
        <Select mode="multiple" allowClear options={options(PRODUCTS)} placeholder="不限" />
      </Form.Item>
      <Form.Item name="operators" label="可见运营" help="留空表示不限">
        <Select mode="multiple" allowClear options={options(OWNERS)} placeholder="不限" />
      </Form.Item>
    </>
  );
}
