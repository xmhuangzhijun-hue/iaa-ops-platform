# ADR-0001 技术栈与前后端分离

- 状态：历史决策（2026-09-16，维护者确认）；组件库由 ADR-0003 更新，业务后端与迁移管理由 ADR-0004 更新
- 范围：整个仓库

以下保留初始选型与当时的理由。当前业务后端为 Java 21 / Spring Boot，表结构由 Flyway 管理，前端使用 Ant Design；Python 保留契约模型、演示种子和迁移对照服务。实际交付范围见 [README](../../README.md)，下表不是当前部署清单。

## 背景

本项目重写一个已停止开发的纯前端中台原型（Vite + React，只有模拟数据，没有后端）。交互逻辑与视觉风格参考维护者此前负责的一套生产投放看板：Streamlit 单体，运行过真实的多角色、多租户投放数据。

约束：

- 一人维护，任何 Agent 离开后维护者仍要能独立接手。
- 开发机常驻多项服务，可用内存经常不足 1 GB；演示环境预计为小规格云主机。
- 项目同时是求职作品，技术选择要经得起面试追问，不能为显得复杂而堆砌中间件。
- 维护者的开发工作台 dev-plm 读取标准 OpenAPI，并已使用 FastAPI + PostgreSQL。

## 决策

| 层 | 选择 |
|---|---|
| 前端框架 | React 18 + TypeScript + Vite（沿用原型工程） |
| 组件与样式 | Tailwind CSS + CSS 变量主题 + Radix / shadcn 无样式原语 |
| 表格 | AG Grid Community |
| 图表 | ECharts |
| 数据请求 | TanStack Query + 由 OpenAPI 生成类型的 openapi-fetch |
| 后端 | Python + FastAPI + Pydantic v2 |
| 持久化 | PostgreSQL 17 + SQLAlchemy 2 + Alembic |
| 认证授权 | JWT 访问令牌 + 刷新令牌；RBAC 权限码；数据按租户与数据范围过滤 |
| 错误格式 | RFC 9457 Problem Details |
| 工程化 | uv、pnpm、pytest、GitHub Actions、Docker Compose |

契约流程：后端代码生成 `contracts/openapi.json`，前端由它生成类型，CI 检查两者与代码一致。

## 理由

- **FastAPI 而非 Spring Boot 3**：原看板的指标口径、数据范围和权限逻辑都是 Python 写的，可以按原语义重写并用测试锁住；和 dev-plm 同栈，一人能维护；内存占用低；求职主定位偏 FDE / AI 应用，Python 更对口。
- **定制主题而非 Ant Design 5**：参考风格包括毛玻璃侧栏、浅色/深色/跟随系统三种模式、12 套主题预设和自定义主色，并要求 WCAG AA 对比度。Ant Design 的 token 能改色，但改不出这套材质与层次，覆盖样式的成本高于用无样式原语自建。
- **AG Grid**：原看板已验证宽表需要冻结列、按原始数值排序（避免按格式化字符串排序）、列设置与密度切换。
- **契约由代码生成而非手写**：手写 YAML 容易与实现漂移；由代码导出再由 CI 比对，契约始终是真的。

## 否决的备选

- **Spring Boot 3 / Java**：国内传统中台岗位更熟悉，但与现有 Python 资产和 dev-plm 形成两套栈，一人维护成本翻倍，内存占用也高。
- **Kafka / Flink / Kubernetes**：数据量与团队规模都用不到。在小规格主机上摆出来只是装饰，面试中反而减分。
- **继续 Streamlit**：前后端不分离，难以做细粒度交互、账号级偏好与移动端适配，也不符合本项目要展示的工程能力。
- **Next.js**：原型阶段试过后弃用；本项目是登录后使用的后台，不需要 SSR 与 SEO。

## 后果

- 第 1 阶段只落地契约与骨架；数据库、认证与设计系统从第 2 阶段起按阶段接入，每阶段带测试。
- 接口在实现前按契约返回 `501 NOT_IMPLEMENTED`，并在契约中标注 `x-implementation: planned`，不把未实现的接口说成可用。
- 新增依赖需在本 ADR 或后续 ADR 中说明理由。
