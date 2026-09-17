# CI 验证边界

`.github/workflows/ci.yml` 保留 Python 测试/契约检查、Java `./gradlew build`（包含 Testcontainers）、前端类型一致性/构建，并新增独立的 `backend-parity` 作业。

对照作业使用专属 PostgreSQL 17 service 数据库：先运行已冻结的 Alembic 初始迁移，再灌入 60 天虚构种子数据；Java 启动时在**同一数据库**上运行 Flyway 增量。Python 使用 SQLAlchemy URL，Java 使用 JDBC URL，主机、端口、库名和用户保持一致。两套服务分别登录并缓存令牌，不需要共享签名密钥，不关闭或放宽登录保护。

`tools/parity/run-ci.sh` 启动 FastAPI 18000 与 Java 18080，等待健康端点后运行全部对照和 Java 响应契约校验。健康检查只是就绪条件，不能代替对照。每个服务最多等待 180 秒，整轮对照有 180 秒上限；`pipefail` 保留对照失败的退出码。退出时只清理自身启动的两个进程，日志由 `always()` artifact 步骤保存 7 天，便于定位启动/迁移/契约失败。

## 本地复现

Linux 上用当前锁定依赖和 Java 21 构建后，可对**新建的临时数据库**设置：

```bash
export APP_ENV=test TZ=UTC DATABASE_USER=iaa
export PARITY_PYTHON_DATABASE_URL=postgresql+psycopg://iaa@127.0.0.1:5432/iaa_ops_parity
export PARITY_JAVA_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/iaa_ops_parity
export DATABASE_URL="$PARITY_PYTHON_DATABASE_URL"
cd backend
uv sync --locked
uv run alembic upgrade head
uv run python -m app.demo.seed --days 60
cd ../backend-java
./gradlew bootJar
cd ..
bash tools/parity/run-ci.sh
```

不要把种子命令指向已有演示库：种子脚本会重写演示租户的数据。信任认证只适用于隔离本机/CI 数据库，正式部署使用其独立的凭据配置。

## 本轮证据（2026-09-17）

- Java 21、Gradle 9.7.1 本机构建通过：78 项测试，0 失败、0 跳过。
- workflow YAML 解析和作业结构检查、Git Bash `bash -n tools/parity/run-ci.sh` 通过。
- 本机没有 `act` 或 `actionlint`，未安装新工具。**GitHub Actions workflow 未实际执行**；仓库没有推送。静态校验不证明 action 下载、Linux 进程清理、service 容器网络和 artifact 上传已通过。
- Windows 等价流程已实际通过：新建专属临时数据库 → Alembic 初始迁移 → 60 天种子 → FastAPI 18000 / Java 18080 同库启动（Flyway 增量）→ 57 项对照、0 失败、0 契约错误、17 处有意差异。使用最终登录保护构建和默认阈值。两套临时进程已停止，测试库已删除，清理无错误。
- 上述 Windows 流程使用已有解释器与本机进程管理，没有执行 Linux `run-ci.sh` 的进程清理分支。首次授权推送后，须查看四个 job 的实际结论及 `backend-parity-logs` artifact，才能关闭远端 CI 验收项。
