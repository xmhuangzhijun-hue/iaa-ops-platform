# 模块边界（模块化单体）

一个主应用，内部按业务模块划边界；模块之间只通过应用服务接口或领域事件通信，**不得跨模块直接读写对方的表**。用 Spring Modulith 做边界校验与模块测试。先把边界做对，再按真实负载决定哪些模块值得独立部署。

## 模块清单

| 模块 | 负责 | 主要表 | 对外提供 |
|---|---|---|---|
| `iam` | 租户、账号、角色、权限码、数据范围、登录与令牌 | tenants, users, user_roles, refresh_tokens, data_scopes | 当前主体、鉴权与范围判定 |
| `catalog` | 厂商、品类、产品、跳转产品关系 | vendors, categories, products | 产品档案查询 |
| `media` | 投放平台、子平台、媒体账户、凭据引用、账户归属 | media_platforms, sub_platforms, media_accounts | 账户档案、凭据引用解析 |
| `campaign` | 推广档案、创意形态、配置版本（出价 / 预算 / 规则 / 启停） | campaigns, campaign_settings | 推广查询、配置变更、版本历史 |
| `ingestion` | 数据源、采集任务、字段映射、staging 落库与入仓 | ingest_sources, ingest_runs, field_mappings, staging_* | 触发采集、任务状态 |
| `facts` | 事实表写入与读取，唯一键幂等、归因作业 | delivery_facts, revenue_source_facts, revenue_attributed_facts, callback_facts | 事实读写接口 |
| `reporting` | **指标口径唯一实现**、聚合 / 分天 / 趋势 / 异常 / 导出 | 无自有表（读 facts） | 报表查询、指标目录、导出任务 |
| `conversion` | 转化记录、回传规则执行、回传结果与重试 | conversion_events, callback_events | 回传触发、回传结果查询 |
| `finance` | 返点、预付、代投成本、赔付、结算与对账 | rebates, prepayments, settlements | 结算数据 |
| `governance` | 审计事件、通知、操作留痕 | audit_events, notifications | 审计写入与查询 |

## 依赖方向（只允许向下）

```
iam  ←  所有模块（鉴权与数据范围）
catalog / media  ←  campaign  ←  conversion
facts  ←  reporting / conversion / finance
ingestion  →  facts（写入）
governance  ←  所有模块（写审计）
```

禁止：`reporting` 直接读 `campaign` 的表（需要推广属性时走 `campaign` 的查询接口或在 facts 侧固化必要维度键）；`ingestion` 直接算派生指标；任何模块绕过 `iam` 自行判定权限。

## 事件

用领域事件解耦写后动作，事件通过事务性发件箱投递（见 `task-reliability.md`）：

- `CampaignSettingsChanged`：`campaign` 发出，`governance` 记审计，`conversion` 刷新回传规则缓存。
- `IngestRunCompleted`：`ingestion` 发出，`facts` 触发归因作业，`reporting` 失效相关缓存。
- `CallbackAttempted`：`conversion` 发出，`governance` 记审计，`facts` 更新回传事实。

## 为什么不一开始拆微服务

拆分的成本是分布式事务、跨服务鉴权、链路排查与多套部署；收益是独立扩缩容。当前负载没有证据要求独立扩缩容，而业务边界尚在变化。先做模块化单体：边界写在代码里并由构建校验，真要拆时按模块切出去即可。

Worker 是例外——采集、导出、批量回传从第一天就独立进程部署，因为它们的失败模式与资源曲线和在线请求完全不同。

## 边界由测试守，不由口头约定守

`backend-java/src/test/java/com/iaaops/ModuleBoundaryTest.java` 用 ArchUnit 把上面的方向固化为可执行规则：共享内核不依赖任何业务模块；任何模块不跨模块引用别人的 `persistence` 与 `web` 包；控制器里不出现 Repository。规则违例即构建失败。

一个已经被这组规则拦下的例子：Spring Security 的过滤链配置最初写在 `shared/config`，注入了 `iam` 的 JWT 过滤器——共享内核反向依赖业务模块。认证与授权的装配属于 `iam`，已移入 `iam/security`。

## 与设计不一致的地方（2026-09-17）

`facts` 模块尚未拆出：`ad_facts` 的读在 `reporting`、写在 `ingestion`，两边各写各的 SQL。触发拆分的条件是「第三个模块需要动这张表」或「归因作业落地」，理由与取舍见 ADR-0004 的实施记录。
