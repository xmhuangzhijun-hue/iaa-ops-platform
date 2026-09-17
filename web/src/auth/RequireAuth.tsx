import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { FullScreenLoading } from "../components/States";
import { useAuth } from "./AuthProvider";

export function RequireAuth({ children, allowPasswordChange = false }: { children: ReactNode; allowPasswordChange?: boolean }) {
  const { status, principal } = useAuth();
  const location = useLocation();

  if (status === "loading") return <FullScreenLoading />;
  if (status === "anonymous" || !principal) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (principal.must_change_password && !allowPasswordChange) return <Navigate to="/password" replace />;
  return <>{children}</>;
}
