import { useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, unwrap, type Schemas } from "../api/client";
import { SESSION_EXPIRED, readTokens, writeTokens } from "./tokens";

export type Principal = Schemas["Principal"];
type Status = "loading" | "anonymous" | "authenticated";

type AuthValue = {
  status: Status;
  principal: Principal | null;
  login: (username: string, password: string) => Promise<Principal | null>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<{ status: Status; principal: Principal | null }>(() => ({
    status: readTokens() ? "loading" : "anonymous",
    principal: null,
  }));

  const loadPrincipal = useCallback(async () => {
    try {
      const principal = await unwrap(api.GET("/api/v1/auth/me"));
      setState({ status: "authenticated", principal });
      return principal;
    } catch {
      writeTokens(null);
      setState({ status: "anonymous", principal: null });
      return null;
    }
  }, []);

  useEffect(() => {
    if (readTokens()) void loadPrincipal();
  }, [loadPrincipal]);

  const logout = useCallback(() => {
    const tokens = readTokens();
    if (tokens) void api.POST("/api/v1/auth/logout", { body: { refresh_token: tokens.refresh } });
    writeTokens(null);
    queryClient.clear();
    setState({ status: "anonymous", principal: null });
  }, [queryClient]);

  useEffect(() => {
    const expire = () => {
      writeTokens(null);
      queryClient.clear();
      setState({ status: "anonymous", principal: null });
    };
    window.addEventListener(SESSION_EXPIRED, expire);
    return () => window.removeEventListener(SESSION_EXPIRED, expire);
  }, [queryClient]);

  const login = useCallback(
    async (username: string, password: string) => {
      const tokens = await unwrap(api.POST("/api/v1/auth/login", { body: { username, password } }));
      writeTokens({ access: tokens.access_token, refresh: tokens.refresh_token });
      queryClient.clear();
      return loadPrincipal();
    },
    [loadPrincipal, queryClient],
  );

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      const tokens = await unwrap(
        api.POST("/api/v1/auth/password", { body: { current_password: currentPassword, new_password: newPassword } }),
      );
      writeTokens({ access: tokens.access_token, refresh: tokens.refresh_token });
      await loadPrincipal();
    },
    [loadPrincipal],
  );

  const value = useMemo(
    () => ({ ...state, login, changePassword, logout }),
    [state, login, changePassword, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth 必须在 AuthProvider 内使用");
  return value;
}

export function usePrincipal(): Principal {
  const { principal } = useAuth();
  if (!principal) throw new Error("需要已登录的页面才能使用 usePrincipal");
  return principal;
}
