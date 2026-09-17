import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap, type Schemas } from "./client";

export type Preferences = Schemas["Preferences"];
type PreferencesPatch = Partial<Omit<Preferences, "revision">>;

const PREFERENCES_KEY = ["preferences"] as const;

export function useMetricCatalog() {
  return useQuery({
    queryKey: ["metrics"],
    queryFn: () => unwrap(api.GET("/api/v1/metrics")),
    staleTime: Infinity,
  });
}

export function useFilterOptions(dateFrom: string, dateTo: string) {
  return useQuery({
    queryKey: ["filter-options", dateFrom, dateTo],
    queryFn: () => unwrap(api.GET("/api/v1/filter-options", { params: { query: { date_from: dateFrom, date_to: dateTo } } })),
    enabled: Boolean(dateFrom && dateTo && dateFrom <= dateTo),
    staleTime: 60_000,
  });
}

function fetchPreferences() {
  return unwrap(api.GET("/api/v1/me/preferences"));
}

export function usePreferences() {
  return useQuery({ queryKey: PREFERENCES_KEY, queryFn: fetchPreferences, staleTime: Infinity });
}

export function useSavePreferences() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (patch: PreferencesPatch) => {
      const put = (base: Preferences) =>
        api.PUT("/api/v1/me/preferences", { body: { ...base, ...patch, revision: base.revision } });
      const cached = client.getQueryData<Preferences>(PREFERENCES_KEY) ?? (await fetchPreferences());
      let result = await put(cached);
      if (result.response.status === 409) {
        // 其他设备刚改过：取最新版本，只覆盖本次修改的字段。
        result = await put(await fetchPreferences());
      }
      return unwrap(Promise.resolve(result));
    },
    onMutate: (patch) => {
      const cached = client.getQueryData<Preferences>(PREFERENCES_KEY);
      if (cached) client.setQueryData<Preferences>(PREFERENCES_KEY, { ...cached, ...patch });
    },
    onSuccess: (saved) => client.setQueryData(PREFERENCES_KEY, saved),
    onError: () => client.invalidateQueries({ queryKey: PREFERENCES_KEY }),
  });
}
