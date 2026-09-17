# 首次公开 CI 验证

2026-09-17 首次推送后的 [运行 35212444191](https://github.com/xmhuangzhijun-hue/iaa-ops-platform/actions/runs/35212444191) 在调度任何作业之前失败：工作流第 81 行在 job 级 `env` 使用了 `runner.temp`，该位置不支持 `runner` context。

预期是四个作业实际启动，parity 服务日志写入 runner 临时目录，并由 artifact 步骤上传。

修正仅把 `PARITY_LOG_DIR` 移到运行对照脚本的 step 级 `env`；artifact 的 step `with.path` 保持同一路径。依照 [GitHub context availability](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability)，这两个 step 位置均支持 `runner`。没有降低测试要求、关闭作业或改变业务代码。

修改后提交 `389f5c88c2b4a87f97b09c5626b96ddabe93231e` 的 [运行 35212699260](https://github.com/xmhuangzhijun-hue/iaa-ops-platform/actions/runs/35212699260) 已于同日实际执行完成：

| 作业 | 实际结果 |
|---|---|
| backend | Python 测试与契约导出一致性通过 |
| backend-java | Java 编译、Testcontainers 集成测试及完整 Gradle build 通过 |
| web | frozen-lockfile 安装、契约类型再生成无差异、TypeScript 与 Vite 构建通过 |
| backend-parity | 独立 PostgreSQL、Alembic 基线、种子、Java Flyway、双服务就绪与对照通过 |

`backend-parity-logs` artifact 已实际上传并下载核对（artifact ID `10493940771`，保留期 7 天）。公开仓库可以从 HTTPS 地址全新克隆；公开历史只有经审查的源码快照和后续公开变更。191 个业务源码/契约文件的 Git blob 与本机已验收版本一致。

公开版本仍是 MVP：四个 CI 作业通过不代表媒体 API、归因、结算、持久任务队列或公网部署已实现。最终预发布版本的对应提交和 CI 链接以 GitHub Release 为准。
