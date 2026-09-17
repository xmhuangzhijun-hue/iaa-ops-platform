import { App, Alert, Button, Descriptions, Select, Space, Table, Tag, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadFile } from "antd/es/upload/interface";
import { useState } from "react";
import { type ImportTask, useCreateImport, useImportTask } from "../api/adminHooks";
import { problemMessage } from "../api/client";
import { PageHeader } from "../components/PageHeader";
import { ErrorBlock } from "../components/States";
import { MEDIA_OPTIONS } from "../lib/vocabulary";

type RowError = ImportTask["errors"][number];

const STATUS: Record<string, { color: string; label: string }> = {
  pending: { color: "default", label: "已受理" },
  processing: { color: "processing", label: "处理中" },
  succeeded: { color: "success", label: "已完成" },
  failed: { color: "error", label: "失败" },
};

/**
 * 数据导入：v1 使用媒体后台导出表入库。
 *
 * 媒体 API 自动采集尚未实现；这个页面直接使用已实现的 Excel 导入接口。
 * 导入按天整体替换——文件覆盖哪几天就替换哪几天，所以界面上必须把覆盖的日期显示出来，
 * 让人看清自己刚刚动了哪几天的数据。
 */
export function ImportsPage() {
  const { message } = App.useApp();
  const [media, setMedia] = useState<string>();
  const [file, setFile] = useState<UploadFile>();
  const [taskId, setTaskId] = useState<string>();

  const create = useCreateImport();
  const task = useImportTask(taskId);
  const current = task.data;
  const running = Boolean(current && ["pending", "processing"].includes(current.status));

  const submit = () => {
    const raw = file?.originFileObj;
    if (!media || !raw) {
      message.warning("请先选择媒体并选好文件");
      return;
    }
    create.mutate({ media, file: raw }, {
      onSuccess: (accepted) => {
        setTaskId(accepted.id);
        setFile(undefined);
        message.success("已受理，正在后台处理");
      },
      onError: (error) => message.error(problemMessage(error)),
    });
  };

  const columns: ColumnsType<RowError> = [
    { title: "行号", dataIndex: "row", width: 90, render: (row: number) => (row > 0 ? row : "—") },
    { title: "列", dataIndex: "column", width: 140, render: (column: string | null) => column ?? "—" },
    { title: "问题", dataIndex: "message" },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="数据导入"
        description="上传媒体后台导出的 .xlsx。按天整体替换：文件覆盖哪几天，就替换这几天该媒体的全部数据，重复导入同一天不会累加。"
      />

      <div className="card space-y-3 p-4">
        <Space wrap>
          <Select
            className="w-40" placeholder="选择媒体" value={media} onChange={setMedia}
            options={MEDIA_OPTIONS}
          />
          <Upload
            accept=".xlsx" maxCount={1} beforeUpload={() => false}
            fileList={file ? [file] : []}
            onChange={({ fileList }) => setFile(fileList[0])}
            onRemove={() => setFile(undefined)}
          >
            <Button>选择文件</Button>
          </Upload>
          <Button type="primary" loading={create.isPending} onClick={submit} disabled={running}>
            上传并导入
          </Button>
        </Space>
        <div className="text-muted text-xs">
          必需列：日期、账户、消耗；可选列：小时、推广计划、预估收益、曝光、点击、启动数、回传数、转化数。
          列名认别名（消耗 / 花费 / 成本都认），单文件不超过 10MB。
        </div>
      </div>

      {taskId && (
        <div className="card p-4">
          {task.isError ? (
            <ErrorBlock error={task.error} onRetry={() => void task.refetch()} />
          ) : current ? (
            <>
              <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }} items={[
                {
                  key: "status", label: "状态",
                  children: <Tag color={STATUS[current.status]?.color}>{STATUS[current.status]?.label ?? current.status}</Tag>,
                },
                { key: "file", label: "文件", children: current.file_name },
                {
                  key: "dates", label: "覆盖日期",
                  children: current.stat_dates.length > 0 ? current.stat_dates.join("、") : "—",
                },
                {
                  key: "rows", label: "写入 / 替换",
                  children: `${current.rows_total} 行 / 替换 ${current.rows_replaced} 行`,
                },
              ]} />
              {current.status === "succeeded" && current.errors.length > 0 && (
                <Alert
                  className="mt-3" type="warning" showIcon
                  message={`有 ${current.errors.length} 行没有导入，其余已写入`}
                  description="坏行只跳过坏行，不影响同一份文件里的其他数据。"
                />
              )}
              {current.errors.length > 0 && (
                <Table<RowError>
                  className="mt-3" size="small" rowKey={(row) => `${row.row}-${row.column ?? ""}-${row.message}`}
                  columns={columns} dataSource={current.errors} pagination={false}
                />
              )}
            </>
          ) : (
            <div className="text-muted text-sm">正在读取任务状态……</div>
          )}
        </div>
      )}
    </div>
  );
}
