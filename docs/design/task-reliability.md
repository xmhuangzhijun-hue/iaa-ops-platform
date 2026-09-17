# 任务可靠性

采集、补数、批量回传、导出、通知一律「先持久化任务，再异步执行」，浏览器不等待长作业。

## 1. 任务生命周期

任务表 `jobs`：`id, tenant_id, type, payload, priority, state, requested_by, scope_snapshot, attempt, last_error, created_at, started_at, finished_at`。

状态：`queued → running → succeeded | partial | failed | cancelled`。
**部分失败是独立终态**，不能当成成功：采集 37 个账户成功、3 个失败，必须能看出是哪 3 个、为什么。

## 2. 事务性发件箱

「数据库写成功但消息没发出去」是这类系统最常见的静默故障。

同一事务内写业务表与 `outbox`（`id, aggregate, event_type, payload, created_at, published_at`），独立投递进程读未投递记录发往 RabbitMQ，投递成功后标记。消息可能重复，**消费者必须幂等**。

## 3. 幂等键

| 场景 | 幂等键 | 重复到达时的行为 |
|---|---|---|
| 采集入库 | `(tenant, platform, account, campaign, stat_date, stat_hour)` | 按键覆盖，不累加 |
| 采集任务 | `(source_id, window_start, window_end)` | 已有成功记录则跳过，除非显式标记强制重采 |
| 回传 | `(campaign_id, external_conversion_id)` | 已回传则不再发送 |
| 导出 | `(user_id, query_hash, date_range)` | 短时间内复用已有产物 |

## 4. 回传的特殊规则：超时不等于对方没收到

外部回传有副作用，不能无脑重试。

- 请求带幂等标识（外部转化 ID），让平台侧能去重。
- 超时归入 `unknown` 状态，**不直接重试**：先用平台的查询接口核验是否已收到；无查询接口时进入人工对账队列。
- 只有明确的「快速失败」（连接拒绝、4xx 参数错误已修正、5xx 且平台声明未处理）才重试，退避重试且次数封顶。
- 每次尝试写 `callback_events`，包含请求摘要、响应摘要、状态与耗时；回传是花钱的动作，必须可追溯。

## 5. 优先级与租户配额

队列按类型与优先级分开，避免单租户的大规模历史补采堵死其他租户的实时采集与回传：

| 队列 | 优先级 | 说明 |
|---|---|---|
| `ingest.realtime` | 高 | 当日增量采集 |
| `callback` | 高 | 有时效性，迟了就无效 |
| `ingest.backfill` | 低 | 历史补采，单租户并发上限 1–2 |
| `export` | 中 | 单用户并发上限，产物有有效期 |
| `notify` | 中 | 失败可丢弃，但要记录 |

每个租户设并发上限与每小时任务配额；超限排队而不是拒绝，并在界面显示排队位置。

## 6. 失败可见

- 采集任务页显示：最近一次结果、已覆盖到几点、失败账户与原因、可手动补采。
- 连续失败达到阈值 → 告警（通知模块），并把数据源标记为「需要人工处理」，不静默重试到天荒地老。
- 授权即将过期在失效前提醒，不等到采集失败才发现。

## 7. 与 Python Worker 的边界

Python Worker 只做：从平台 API 取原始数据 → 字段映射 → 写 staging 表 → 发 `IngestRunCompleted` 事件。
入仓（staging → 事实表）、归因、派生指标、权限判定全部在 Java 侧。Worker 不对外提供业务查询接口，不计算任何派生指标。
