import { App, ConfigProvider, theme } from "antd";
import zhCN from "antd/locale/zh_CN";
import type { ReactNode } from "react";
import { useTheme } from "./ThemeProvider";

const SURFACE = {
  light: { container: "#ffffff", elevated: "#ffffff", layout: "transparent", text: "#172231", border: "#d6dfea" },
  dark: { container: "#101d2d", elevated: "#132437", layout: "transparent", text: "#eef5ff", border: "#274056" },
} as const;

/** 让 Ant Design 的令牌跟随同一套主题：主色取当前预设，明暗跟随全局模式。 */
export function AntdProvider({ children }: { children: ReactNode }) {
  const { primary, resolvedMode } = useTheme();
  const surface = SURFACE[resolvedMode];

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: resolvedMode === "dark" ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: primary,
          colorBgContainer: surface.container,
          colorBgElevated: surface.elevated,
          colorBgLayout: surface.layout,
          colorText: surface.text,
          colorBorder: surface.border,
          borderRadius: 10,
          fontFamily: "var(--font-sans)",
          fontSize: 13,
        },
        components: {
          Table: { headerBg: "transparent", rowHoverBg: "var(--primary-soft)" },
          Modal: { contentBg: surface.elevated, headerBg: surface.elevated },
          Drawer: { colorBgElevated: surface.elevated },
        },
      }}
    >
      <App>{children}</App>
    </ConfigProvider>
  );
}
