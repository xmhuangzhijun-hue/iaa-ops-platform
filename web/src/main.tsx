import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { ApiProblem } from "./api/client";
import { router } from "./app/router";
import { AuthProvider } from "./auth/AuthProvider";
import "./styles/index.css";
import { AntdProvider } from "./theme/AntdProvider";
import { ThemeProvider } from "./theme/ThemeProvider";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 4xx 是确定性结果，重试没有意义；只重试网络与服务端错误。
      retry: (count, error) => !(error instanceof ApiProblem && error.status < 500) && count < 2,
      refetchOnWindowFocus: false,
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AntdProvider>
          <AuthProvider>
            <RouterProvider router={router} />
          </AuthProvider>
        </AntdProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
