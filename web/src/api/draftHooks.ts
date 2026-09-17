import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  BulkCallback, BulkRuleUpdate, CampaignDeliveryRow, CampaignFilters, CampaignRevenueRow, CampaignView,
  Category, FieldMapping, IngestRun, IngestSource, MediaAccount, Page, Product, TimelineRow,
} from "./draft";
import { draftRequest, type QueryValue } from "./client";

export type TableQuery = { page: number; pageSize: number; sortField?: string; sortOrder?: "asc" | "desc" };

function campaignQuery(view: CampaignView, filters: CampaignFilters, table: TableQuery): Record<string, QueryValue> {
  return { view, ...filters, ...table } as Record<string, QueryValue>;
}

export function useCampaigns<T extends CampaignDeliveryRow | CampaignRevenueRow>(
  view: CampaignView,
  filters: CampaignFilters,
  table: TableQuery,
) {
  return useQuery({
    queryKey: ["campaigns", view, filters, table],
    queryFn: () => draftRequest<Page<T>>("/api/v1/campaigns", { query: campaignQuery(view, filters, table) }),
  });
}

export function useTimeline(campaignId: string | null) {
  return useQuery({
    queryKey: ["campaign-timeline", campaignId],
    queryFn: () => draftRequest<{ items: TimelineRow[] }>(`/api/v1/campaigns/${campaignId}/timeline`),
    enabled: Boolean(campaignId),
  });
}

export function useBulkRules() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: BulkRuleUpdate) =>
      draftRequest<{ updated: number }>("/api/v1/campaigns/bulk-rules", { method: "POST", body: JSON.stringify(body) }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["campaigns"] }),
  });
}

export function useBulkCallback() {
  return useMutation({
    mutationFn: (body: BulkCallback) =>
      draftRequest<{ accepted: number; estimated: number; taskId: string }>("/api/v1/campaigns/bulk-callback", {
        method: "POST",
        body: JSON.stringify(body),
      }),
  });
}

export function useProducts(keyword: string, table: TableQuery) {
  return useQuery({
    queryKey: ["products", keyword, table],
    queryFn: () => draftRequest<Page<Product>>("/api/v1/products", { query: { keyword, ...table } }),
  });
}

export function useCategories() {
  return useQuery({ queryKey: ["categories"], queryFn: () => draftRequest<Page<Category>>("/api/v1/categories") });
}

export function useMediaAccounts(keyword: string, platforms: string[], table: TableQuery) {
  return useQuery({
    queryKey: ["media-accounts", keyword, platforms, table],
    queryFn: () => draftRequest<Page<MediaAccount>>("/api/v1/media-accounts", { query: { keyword, platforms, ...table } }),
  });
}

export function useIngestSources() {
  return useQuery({
    queryKey: ["ingest-sources"],
    queryFn: () => draftRequest<Page<IngestSource>>("/api/v1/ingest/sources"),
  });
}

export function useIngestRuns(state: string | undefined, table: TableQuery) {
  return useQuery({
    queryKey: ["ingest-runs", state, table],
    queryFn: () => draftRequest<Page<IngestRun>>("/api/v1/ingest/runs", { query: { state, ...table } }),
    refetchInterval: 15_000,
  });
}

export function useToggleSource() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      draftRequest<IngestSource>(`/api/v1/ingest/sources/${id}`, { method: "PATCH", body: JSON.stringify({ enabled }) }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["ingest-sources"] }),
  });
}

export function useTriggerIngest() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { sourceId: string; window?: string }) =>
      draftRequest<IngestRun>("/api/v1/ingest/runs", { method: "POST", body: JSON.stringify(body) }),
    onSuccess: () => client.invalidateQueries({ queryKey: ["ingest-runs"] }),
  });
}

export function useFieldMappings(platforms: string[], status: string | undefined, table: TableQuery) {
  return useQuery({
    queryKey: ["field-mappings", platforms, status, table],
    queryFn: () => draftRequest<Page<FieldMapping>>("/api/v1/field-mappings", { query: { platforms, status, ...table } }),
  });
}
