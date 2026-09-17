import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "./client";

export type AccountMapping = Schemas["AccountMapping"];
export type AccountMappingInput = Schemas["AccountMappingInput"];
export type UpsertResult = Schemas["UpsertResult"];
export type UserSummary = Schemas["UserSummary"];
export type UserCreate = Schemas["UserCreate"];
export type UserCreated = Schemas["UserCreated"];
export type UserRolesUpdate = Schemas["UserRolesUpdate"];
export type AuditEvent = Schemas["AuditEvent"];
export type ImportTask = Schemas["ImportTask"];
export type Role = UserSummary["roles"][number];

const MAPPINGS_KEY = "account-mappings";
const USERS_KEY = "users";
const AUDIT_KEY = "audit-events";
const IMPORT_KEY = "import-task";

export function useAccountMappings(media: string | undefined, keyword: string, page: number, pageSize: number) {
  return useQuery({
    queryKey: [MAPPINGS_KEY, media ?? "", keyword, page, pageSize],
    queryFn: () =>
      unwrap(api.GET("/api/v1/mappings/accounts", {
        params: { query: { media: media || undefined, keyword: keyword || undefined, page, page_size: pageSize } },
      })),
    placeholderData: (previous) => previous,
  });
}

export function useUpsertMappings() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (items: AccountMappingInput[]) =>
      unwrap(api.PUT("/api/v1/mappings/accounts", { body: { items } })),
    // 写入是整批成败，成功后重新取一遍：revision 变了，界面必须拿到新的才能再改
    onSuccess: () => client.invalidateQueries({ queryKey: [MAPPINGS_KEY] }),
  });
}

export function useUsers(keyword: string, status: "active" | "disabled" | undefined, page: number, pageSize: number) {
  return useQuery({
    queryKey: [USERS_KEY, keyword, status ?? "", page, pageSize],
    queryFn: () =>
      unwrap(api.GET("/api/v1/users", {
        params: { query: { keyword: keyword || undefined, status, page, page_size: pageSize } },
      })),
    placeholderData: (previous) => previous,
  });
}

export function useCreateUser() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: UserCreate) => unwrap(api.POST("/api/v1/users", { body })),
    onSuccess: () => client.invalidateQueries({ queryKey: [USERS_KEY] }),
  });
}

export function useUpdateUserRoles() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, ...body }: UserRolesUpdate & { userId: string }) =>
      unwrap(api.PUT("/api/v1/users/{user_id}/roles", { params: { path: { user_id: userId } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: [USERS_KEY] }),
  });
}

/** 审计日志按游标翻页：cursor 为空表示第一页。 */
export function useAuditEvents(action: string, cursor: string | undefined, limit: number) {
  return useQuery({
    queryKey: [AUDIT_KEY, action, cursor ?? "", limit],
    queryFn: () =>
      unwrap(api.GET("/api/v1/audit-events", {
        params: { query: { action: action || undefined, cursor, limit } },
      })),
    placeholderData: (previous) => previous,
  });
}

/** 上传导出表：multipart 请求体自己拼，openapi-fetch 默认按 JSON 序列化。 */
export function useCreateImport() {
  return useMutation({
    mutationFn: ({ media, file }: { media: string; file: File }) =>
      unwrap(api.POST("/api/v1/imports", {
        body: { media, file: file as unknown as string },
        bodySerializer: (body) => {
          const form = new FormData();
          form.append("media", (body as { media: string }).media);
          form.append("file", file);
          return form;
        },
      })),
  });
}

/**
 * 轮询导入任务，直到它结束。
 *
 * 处理是异步的：上传只拿到一个 pending 的任务，状态要自己追。
 * 结束后停止轮询，不然一个开着的页面会一直打后端。
 */
export function useImportTask(taskId: string | undefined) {
  return useQuery({
    queryKey: [IMPORT_KEY, taskId ?? ""],
    enabled: Boolean(taskId),
    queryFn: () =>
      unwrap(api.GET("/api/v1/imports/{import_id}", { params: { path: { import_id: taskId as string } } })),
    refetchInterval: (query) =>
      query.state.data && ["succeeded", "failed"].includes(query.state.data.status) ? false : 1000,
  });
}
