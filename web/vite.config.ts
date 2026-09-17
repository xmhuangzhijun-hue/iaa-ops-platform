import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// 迁移期两个后端都能跑：默认连 Java（8080），要回既有 FastAPI 时
// 设 VITE_API_TARGET=http://127.0.0.1:8000 即可，不改代码。
const apiTarget = process.env.VITE_API_TARGET ?? "http://127.0.0.1:8080";

export default defineConfig({
  // 将预构建缓存放在项目内，避免本地依赖链接切换时复用其他工作区的缓存。
  cacheDir: ".local/vite-cache",
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { "/api": apiTarget },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          grid: ["ag-grid-community", "ag-grid-react"],
          charts: ["echarts"],
        },
      },
    },
  },
});
