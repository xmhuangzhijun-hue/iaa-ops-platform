import { useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { Schemas } from "../api/client";
import { lastDays } from "../lib/dates";
import { readJson, writeJson } from "../lib/storage";

export type Filters = {
  dateFrom: string;
  dateTo: string;
  media: string[];
  products: string[];
  agencies: string[];
  accounts: string[];
  operators: string[];
  keyword: string;
};

type FilterValue = {
  filters: Filters;
  apply: (next: Filters) => void;
  refresh: () => void;
  refreshToken: number;
};

const STORAGE_KEY = "iaa.filters";
const FilterContext = createContext<FilterValue | null>(null);

export function defaultFilters(): Filters {
  return { ...lastDays(7), media: [], products: [], agencies: [], accounts: [], operators: [], keyword: "" };
}

/** 各看盘页共用的已生效筛选；输入过程中的草稿留在筛选条内，点「查询」才生效。 */
export function FilterProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<Filters>(() => readJson<Filters>("session", STORAGE_KEY) ?? defaultFilters());
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => writeJson("session", STORAGE_KEY, filters), [filters]);

  const refresh = useCallback(() => {
    setRefreshToken((value) => value + 1);
    void queryClient.invalidateQueries({ queryKey: ["filter-options"] });
  }, [queryClient]);

  const value = useMemo(() => ({ filters, apply: setFilters, refresh, refreshToken }), [filters, refresh, refreshToken]);
  return <FilterContext.Provider value={value}>{children}</FilterContext.Provider>;
}

export function useFilters(): FilterValue {
  const value = useContext(FilterContext);
  if (!value) throw new Error("useFilters 必须在 FilterProvider 内使用");
  return value;
}

export function queryBase(filters: Filters): Pick<Schemas["ReportQuery"], "date_from" | "date_to" | "filters"> {
  return {
    date_from: filters.dateFrom,
    date_to: filters.dateTo,
    filters: {
      media: filters.media,
      products: filters.products,
      agencies: filters.agencies,
      accounts: filters.accounts,
      operators: filters.operators,
    },
  };
}
