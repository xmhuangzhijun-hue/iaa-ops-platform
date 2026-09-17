# 贡献指南

这是仍在迭代的作品集 MVP。先阅读 [README](README.md)、[产品范围](docs/product-scope.md) 和 [AGENTS.md](AGENTS.md)；新增业务模块或改变数据模型前，请用 issue 说明场景和验收标准。

## 修改约定

- 业务实现放在 Java；数据库变更新增 Flyway 迁移，不修改已发布的迁移文件。Python 保留契约模型、种子和迁移对照用途。
- 接口的 `x-requirements` 沿用现有 `REQ-*` 映射；新增需求在 issue、PR 或仓库文档记录编号、目标和验收标准，不依赖维护者私有工具。
- 指标、租户隔离、角色和数据范围由服务端强制。修改权限或导入行为时，覆盖拒绝路径及邻近功能。
- 只提交虚构数据。不要上传真实账户、业务数据、截图、凭据或本机配置；保留无关并发改动。

## 本地验证

按 [README](README.md#从空库启动本地演示) 准备依赖并启动 Docker/PostgreSQL，然后在对应目录执行：

| 目录 | 命令 |
|---|---|
| `backend-java/` | `./gradlew build`；Windows 使用 `.\gradlew.bat build` |
| `backend/` | `uv sync --locked`、`uv run pytest -q`、`uv run python scripts/export_openapi.py --check` |
| `web/` | `pnpm install --frozen-lockfile`、`pnpm gen:api`、`pnpm build` |

Java 集成测试用 Testcontainers 创建临时数据库。Python 测试会删除、重建并在结束时删除 `IAA_TEST_DATABASE_URL` 指定的数据库，默认是独立的 `iaa_ops_test`；该变量只能指向可丢弃的测试库。

修改接口时先在 `backend/` 执行 `uv run python scripts/export_openapi.py`，再生成前端类型，将模型、导出器、`contracts/openapi.json` 与 `web/src/api/schema.d.ts` 一并提交。未修改接口时，生成后这些文件应无差异。双后端对照及完整验收命令见 [发布清单](docs/release-checklist.md)。

界面变更还需按 [演示指南](docs/demo-guide.md) 实际操作相关页面；构建通过不能代替交互验收。

## 提交 PR

说明解决的问题、用户可见变化、实际执行的验证及已知限制，关联 issue 和需求编号。保持提交范围清晰，并使用自己的贡献者身份；使用 Agent 辅助时说明其参与范围。无需访问任何维护者本机系统。

安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。
