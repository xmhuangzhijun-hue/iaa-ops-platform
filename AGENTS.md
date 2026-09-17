# AGENTS.md

开工先读：`README.md` → `docs/STATUS.md` → `docs/adr/` → `docs/product-scope.md`。

1. 接口以代码为源。契约目前仍由 `backend/scripts/export_openapi.py` 从 FastAPI 导出（迁移未完，见 `docs/STATUS.md`）；改接口后同时跑它与 `web` 的 `pnpm gen:api`，把契约和生成类型一起提交。
2. 业务实现在 `backend-java/`，`backend/` 只剩契约导出与演示数据种子，不再承接新功能。
3. 指标口径只有一份实现：Java 侧 `shared/metrics`（Python 侧 `app/domain/metrics.py` 是迁移前的原件，两边改动必须同步，且以 Java 为准）。不要在服务、前端或 SQL 里另写公式。
4. 表结构由 `backend-java/src/main/resources/db/migration/` 的 Flyway 迁移管理；Python 侧 Alembic 已冻结，不要新增版本。
5. 每个接口必须带 `x-requirements`。沿用契约中的现有 `REQ-*` 映射；新需求先在公开 issue、PR 或仓库文档中说明目标、验收标准与编号，再更新接口映射。贡献者不需要访问维护者的私有工作台。
6. 仓库只放虚构数据。不写入真实公司、客户、账户、人员姓名、域名或凭据。
7. 权限与数据范围在服务端强制；前端导航裁剪不能代替接口鉴权。
8. 在 PR 中说明变更行为、验证结果和已知限制；必要时同步 `docs/STATUS.md` 与相关设计文档。结果只写实际执行过的验证；维护者可以另行同步个人工作台，但这不是贡献前提。
9. 提交保留实际贡献者身份，建议使用 GitHub 提供的 noreply 邮箱；一次提交聚焦一个变更。使用 Agent 辅助时在 PR 中说明参与范围，不提交私人会话、日志或凭据。
