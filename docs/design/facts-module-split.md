# facts 模块拆分前审计

日期：2026-09-17。审计基线：`77cbb61`，核对代码、Flyway、既有测试与对照工具，并在 Testcontainers 隔离库复现 F-01；没有移动或修复业务实现。后续登录保护与 CI 的变更不应改变这里审计的事实读写语义。

后续状态：F-01 已在独立修复中关闭；授权空集与账号管理三维范围的修复、修改前失败和修改后回归见 [修复记录](../../reports/f01-permission-regression.md)。以下复现是历史基线证据，未来拆分应采用修复后的语义。

## 结论

**当前先不拆 `facts`。** Java 业务代码仍只有 `reporting` 读、`ingestion` 写 `ad_facts`，没有第三个业务模块直接访问，也没有收益归因作业实现。符合 [ADR-0004](../adr/0004-backend-stack.md) 的等待条件：第三个模块需要动事实表，或归因作业准备落地时，再实施拆分。

现在值得交付的是明确读写契约与补测清单。包名迁移不能顺带把现有单表换成设计中的四张事实表，也不能趁机改历史映射、去重或权限语义。发现的权限差异应先独立复现和修复，再冻结拆分前基线。

## 1. 实际数据模型

真值为 `backend-java/src/main/resources/db/migration/V1__core.sql`。`AdFactEntity` 仅参与启动时实体校验；实际 SQL 读写使用 `JdbcTemplate`，集成测试通过 Flyway 建表。

| 列 | 存储与空值 | 当前含义 |
|---|---|---|
| `id` | bigint identity，主键 | 一条物理明细的标识；原始明细排序的末位稳定键 |
| `tenant_id` | varchar(32)，非空，租户外键 | 所有查询与删除的强制隔离条件 |
| `stat_date` | date，非空 | 原始业务日期；查询首尾均包含 |
| `stat_hour` | smallint，可空；非空必须 0–23 | 空代表未提供小时，不等于 0 点 |
| `media` | varchar(32)，非空 | 上传表单提供；当前接口只校验非空和长度，没有媒体枚举约束 |
| `account` | varchar(64)，非空 | 账户字符串；导入必需且去首尾空白 |
| `campaign` | varchar(64)，可空 | 推广计划字符串；缺列或空单元格导入为 null |
| `cost`、`revenue` | numeric(14,2)，非空，默认 0 | 基础金额；Excel 缺失金额值变为 0，解析使用 HALF_EVEN 保留两位 |
| `impressions`、`clicks`、`launches`、`callbacks`、`conversions` | bigint，非空，默认 0 | 基础计数；Excel 缺失值变为 0，非整数拒绝该行 |
| `loaded_at` | timestamptz，非空，默认 `now()` | 数据库事务时钟的入库标记；不是媒体数据的发生时间或处理完成时刻 |

只有 `id` 是唯一键。不存在 `(租户, 媒体, 账户, 计划, 日期, 小时)` 业务唯一约束；同一组合的多行会分别保留并求和。小时可空，当前表可以混入日粒度和小时粒度行，代码不会检测两者是否重叠。金额、计数没有非负数据库约束，解析器也没有负数拒绝逻辑；机械迁移不能擅自改变这些接受条件。

索引是 `(tenant_id, stat_date)` 与 `(tenant_id, media, account, stat_date)`。没有 `source_run_id`、归因方法、收益类型、归因版本或配置快照。`product`、`agency`、`operator` 不在事实行里。

[fact-grain.md](fact-grain.md) 中的 `delivery_facts`、`revenue_source_facts`、`revenue_attributed_facts`、`callback_facts` 是目标设计，尚未实现。尤其不能把当前 `revenue` 解释成已经完成推广归因的收益。当前 ROI 只是现有单表中 `sum(revenue) / sum(cost)` 的口径。

## 2. 读路径与不可丢失的条件

入口链路是 `ReportController → ReportService → reporting/persistence/FactQueryRepository`。筛选项、聚合、分天、趋势、ROI 异常、原始明细和 CSV 共用这条查询路径。

### 映射 join

```sql
from ad_facts f
left join account_mappings m
  on m.tenant_id = f.tenant_id
 and m.media = f.media
 and m.account = f.account
```

`account_mappings` 主键也是上述三列，保证一次 join 至多补齐一行，不能省去 tenant 或 media，否则可能串租户、串媒体或重复放大金额。映射修改立即影响历史报表，没有历史快照。没有映射或映射字段为空时，不限范围查询仍保留事实行；对应映射维度受限时，SQL `IN` 会排除 null。

聚合和原始明细响应保留维度 null；趋势线和异常说明使用 `未映射` 标签。不能在 facts 层统一把 null 替换成文案。筛选可选值排除 null，按值排序。

### 租户、范围与筛选

- 每次查询首先加 `f.tenant_id = 当前主体租户` 与日期闭区间。
- 请求筛选支持媒体、账户、产品、代理、运营；同维度 `IN` 是或，不同维度之间是且。请求筛选的空列表表示不限。
- 服务端范围另加代理、产品、运营条件，与请求条件取交集，不能被请求中的更宽范围覆盖。当前没有独立的账户级 `DataScope` 字段。
- 聚合关键词只搜索实际分组维度；原始明细关键词搜索账户、计划、产品。大小写不敏感，`/`、`%`、`_` 转义后匹配。搜索 `未映射` 文案不会自动匹配 null。
- `Dimension` 枚举同时绑定 API key、显示名与 SQL 列白名单；原始排序还允许本次可见的基础指标。所有值绑定参数，不能将外部字段名直接拼入 SQL。

**现有权限差异，需要独立处理：** Java 的 `FactQueryRepository.addIn` 对 `null` 与 `[]` 都不加条件；Python 的 `reports._conditions` 对 `DataScope` 只把 null 视为不限，`[]` 得到空集。契约 `DataScope` 的说明也是「null 表示不限」。`UserAdminService.checkScopeWithin` 按集合子集判断，受限主体可以授出 `[]`，随后 Java 报表会将该维度按不限处理。该链路已在 Testcontainers 隔离库复现（见 F-01 回执）；不能用当前 parity 的零失败证明其正确。应补用例并独立修复，不作为拆包时允许保留的业务行为。

### 汇总、分页与新鲜度

- SQL 只求基础指标和，空集合合计基础值为 0。`Summaries` 与 `shared/metrics/MetricRegistry` 再计算派生指标；分母 0 返回 null，除法 28 位有效数字、HALF_EVEN，展示按指标精度舍入。指标口径继续归共享内核，不能搬成 facts 内的第二份公式。
- 聚合先取最多 20,001 组，超过 20,000 组报错；应用层排序后分页，合计是全部匹配分组的合计。原始明细在数据库分页，合计不受分页影响，排序 `nulls last` 后追加 `f.id`。
- 聚合 SQL 没有完整 `ORDER BY`；并列项稳定排序承接数据库返回顺序。因此现有并列顺序不是跨查询计划的稳定保证，新增索引、改查询形状或分批读取都可能影响逐字段对照。需要明确并列验收策略，不能用忽略整段 rows 顺序掩盖变化。
- 分天固定加入日期维度；小时趋势排除 `stat_hour is null`，日趋势仍包含这些行；缺失时间桶不补零。拆线按消耗选前 8 个，其余合并成「其他」。
- `data_as_of = max(loaded_at)`，无匹配行为 null。聚合及原始明细使用不含关键词的基础条件；小时趋势使用已经排除空小时的条件。范围筛选与租户条件始终存在。
- CSV 复用页面查询与范围，金额和比率按同一指标精度输出，带 UTF-8 BOM。导出原始明细额外要求真实指标权限。

## 3. 写路径与替换语义

入口链路是 `ImportController → ImportService → afterCommit → ImportProcessor → ingestion/persistence/FactWriteRepository`。上传接口先检查 `imports.manage`，租户取当前主体，后台从已持久化任务取得租户和媒体，不接受 Excel 内容覆盖这两个字段。

1. 解析首个工作表，接受有效行，错误行跳过并保留前 20 条错误。没有有效行则整份失败，尚不触及事实表。
2. 覆盖日期来自**解析成功的行**，排序去重。某个日期若只有坏行，就不会进入删除范围。
3. 删除条件是 `tenant_id + media + stat_date IN (成功日期)`，覆盖该媒体这些日期的**全部账户与计划**，不是仅覆盖文件里的账户。
4. 按 500 行批次插入全部有效行。保留文件内重复组合，不行级去重。`id` 与 `loaded_at` 重新生成；重复上传在基础数据行数与金额上不累加，不保证主键和新鲜度不变。
5. `rows_replaced` 是删除的物理行数，`rows_total` 是插入的有效行数；带坏行但仍有有效行时任务为 `succeeded` 并携带 errors。

当前删除、插入和任务处理在 `ImportProcessor.process` 的 `REQUIRES_NEW` 事务中。拆出 facts 写服务不能把删除和插入拆成独立提交。现有测试已覆盖解析阶段失败不动旧数据，但未覆盖「删除后数据库插入失败」；方法内捕获 `RuntimeException` 并尝试写 failed，也不等于已证明数据库事务出错后终态可持久化。需要在隔离库进行失败注入，分别验证旧数据保留和终态可见，不能只断言异常或 HTTP 202。

同租户、媒体、日期的并发导入没有显式串行锁，也无业务唯一键，顺序重复上传的测试不能证明并发不混写。当前审计只登记该测试缺口，不在拆包时顺带改变并发策略。

## 4. 真要拆时需改哪些文件

目标先保留 `ad_facts`、HTTP 契约及全部业务行为，只收拢归属。以下是文件级边界，不是本次已完成改动。

| 范围 | 需要的改动 |
|---|---|
| `reporting/persistence/FactQueryRepository.java`、`AdFactEntity.java` | 迁到 `facts/persistence`，仅 facts 内部使用 |
| `ingestion/persistence/FactWriteRepository.java` | 迁到 `facts/persistence`；对外提供一个完整日期替换操作，避免调用方自行拼 delete / insert |
| 新增 `facts/FactReadService`、`FactWriteService` 与 API 值对象 | 暴露基础数据、维度、分组、计数、新鲜度与替换结果；不暴露 JPA 实体、JdbcTemplate、任意 SQL 或 Repository |
| `reporting/ReportService.java` | 依赖 facts 应用接口；移除 `Conditions`、`f.stat_hour is not null` 和 SQL orderBy 字符串，改用受限查询参数；保留指标选择、派生指标、分页结果组装、趋势、异常、CSV |
| `reporting/domain/Dimension.java` | 分离报表标签与 facts 的维度字段白名单；facts 不能反向引用 reporting，否则现有 ArchUnit 规则会拒绝 |
| `ingestion/ParsedRow.java`、`WorkbookParser.java`、`ImportProcessor.java` | 解析对象由 ingestion 持有，在边界转换为 facts 写入值对象；facts 不依赖 Excel 或 ingestion 包；任务、解析错误与审计留在 ingestion |
| `ModuleBoundaryTest.java` | 注册 facts 模块，守住私有 persistence、facts 不依赖 reporting / ingestion、共享内核不反向依赖 facts；保留原边界规则 |
| `ReportingApiTest.java`、`ImportApiTest.java`、新增 facts 集成测试 | 使用同一 Flyway schema 验证以下行为矩阵；不要只测试新服务能被注入 |
| `docs/design/module-boundaries.md`、ADR-0004、STATUS | 记录触发原因、数据/SQL 归属与验收证据；不把旧设计当成已实施能力 |

还有一个不能用改包名掩盖的边界：查询目前直接 join `mapping` 所属的 `account_mappings`。ArchUnit 检查 Java 类依赖，不检查 SQL 表访问，现状已经偏离「不跨模块读对方表」的目标。落地拆分前必须在 ADR 中确定映射读模型的归属与接口；若第一步仍保留 join，应明确它是保留历史动态映射行为的过渡例外，并单列后续触发条件。不能同时改成事实维度快照，那会改变历史报表与权限范围。

纯包与接口迁移不需要新的 Flyway 版本，不改已发布的 V1/V2，不修改 Python 契约导出、前端生成类型或演示种子。未来四事实表演进要另起数据迁移设计，尤其先解决收益层级、归因与来源追踪。

## 5. 拆分前后用同一把尺验收

已有 `ReportingApiTest` 覆盖汇总后算比率、零分母、代理范围、字段可见性、分天、小时趋势、未映射线、异常阈值、分页合计、筛选项与 CSV；`ImportApiTest` 覆盖按天替换、顺序重复不累加、跨日/媒体保留、坏行、解析失败、权限与审计。`tools/parity/check.py` 逐字段对照现有报表和导出，但导入只查询不存在的任务，不会验证写入行为。本轮交接的常规 Java 构建为 78 项通过；本审计另运行 1 项专用隔离复现，用例通过表示 F-01 缺陷被复现，不代表权限正确。

| 必须补齐/明确的输入 | 应核对的用户可见行为与数据回执 |
|---|---|
| 两个租户使用同媒体同账户；同租户两个媒体使用同账户 | join 不放大、不串范围；所有报表、筛选项、新鲜度和替换只影响目标租户及媒体 |
| 范围 null、空数组、多值；请求范围与授权范围交叉 | 按契约固定空范围语义；受限账号不能通过授空集或请求扩大结果；列表与 CSV 一致 |
| 无映射、部分映射为空、修改映射后查历史 | null 与「未映射」展示位置保持；历史数据随映射改变；受限主体不见不匹配事实 |
| 小时 null 与 0；计划 null；同业务组合重复行 | 日/小时趋势差别、原始行数、求和、排序和导出一致，不私自补小时或去重 |
| 空集合、零分母、舍入临界值 | 合计基础值为 0、派生指标 null、HALF_EVEN 精度不变 |
| 关键词包含 `%`、`_`、`/`；跨页并列值；关键词排除了最新行 | 转义、不越界分页、排序策略、新鲜度条件一致 |
| 一天仅有坏行；同一天另账户旧行；重复上传 | 覆盖日期只含有效行日期；按天全账户替换；金额不累加；行数、错误表和新鲜度符合既有约定 |
| 删除后插入失败、500 行批次中途失败、同日并发导入 | 分别核对旧数据/部分数据、任务终态与最终报表；先建立期望和现状，不由拆包偷偷决定错误恢复策略 |

实施时先在隔离 Postgres 上保存上述固定数据的 HTTP JSON / CSV 及数据库回执，再应用拆分，对同一批输入逐项比较。重跑 Java 集成与边界测试、双后端 parity、契约校验；最后用浏览器走一次虚构文件上传到终态，再查报表与导出验证结果。测试应使用隔离数据，不能为做验收覆盖现有演示日期。

## 6. 下一次触发时的顺序

先收敛权限空数组差异和故障/并发的实际证据；第三个访问方或归因需求确定后，明确 facts API 与映射读模型，补齐能手算的回归数据，再机械迁移现有单表读写。机械迁移验收通过后，才单独启动四事实表或归因模型变更。现有数据规模与仅有两个调用方，不足以支持现在增加这一轮迁移风险。

## 独立问题 F-01：空授权数组被扩大为不限范围

建议优先级：P1。审计时已在 Testcontainers 隔离库通过真实鉴权接口复现；现已独立修复，未与 facts 拆包合并。修复回执见本文顶部链接。

基线 `77cbb61` 的精确证据：

| 文件与行号 | 证据 |
|---|---|
| `backend-java/src/main/java/com/iaaops/reporting/persistence/FactQueryRepository.java:73–76` | 授权 `DataScope` 与普通筛选调用同一 `addIn` |
| 同文件 `:80–84` | `values == null || values.isEmpty()` 直接返回，不增加 SQL 限制 |
| `backend-java/src/main/java/com/iaaops/iam/UserAdminService.java:128–143` | 范围授予按包含关系判断；空数组没有越界元素，能通过校验 |
| 同文件 `:85–102` | 更新角色和范围前按原范围校验目标可见性；主体自己的原账号可见，随后可保存空数组 |
| `backend/app/services/reports.py:67–70` | 基准只在 `allowed is not None` 时添加 `IN`；空数组被保留成空集条件 |
| `backend/app/schemas/auth.py:38–40` | 三个授权维度明确 null 表示不限 |
| `contracts/openapi.json:3439,3454,3469` | 导出契约同样明确 null 表示不限 |
| `backend-java/src/test/java/com/iaaops/reporting/ReportingApiTest.java:35–48` | 现成隔离夹具：代理原范围只含星河代理，acc-a 消耗 400；另一代理与未映射行合计 250 |

最小复现建议放在现有 `ReportingApiTest`，使用继承的 `PostgresTestBase` Testcontainers 数据库。**不要在现有演示服务或数据库运行以下更新。**

1. 正常运行该测试类的 `seed()`。用 `t.agency` 调 `/reports/aggregate`、按账户分组，确认原结果只有 `acc-a`，消耗合计 400。
2. 以同一 `t.agency` 身份调用 `PUT /api/v1/users/usr_agency/roles`，请求体 `{"roles":["agency_admin"],"data_scope":{"agencies":[]},"revision":0}`（现有测试夹具的初始 revision 为 0，实际复现以列表接口读取值为准），记录状态码与返回范围。角色权限未变，原目标账号属于本人可见范围。
3. 重新取得该账号令牌后，按同样日期与请求查询聚合、筛选可选值以及聚合导出。契约和 Python 的预期是空授权范围没有数据；本轮真实接口结果出现 `acc-a`、`acc-b`、`acc-c`，消耗合计 650，并暴露另一代理及未映射数据。收益字段仍受指标权限限制，本问题不等于已获得真实收益权限。
4. 增加同维度请求筛选为 `[]` 的对照，确认请求筛选仍表示「不增加条件」，但授权 `DataScope=[]` 必须独立处理；再覆盖 products / operators 的同类输入。

只验证查询侧的更小变体是在测试夹具内以绑定参数执行 `update users set data_scope = cast(? as jsonb) where id = ?`，参数为 `{"agencies":[]}` 与 `usr_agency`，再查询；这能定位 SQL 条件缺失，但不能替代步骤 2 的真实授予路径验证。

### 本轮隔离复现回执

2026-09-17，专用 `EmptyScopeAuditTest` 继承 `PostgresTestBase`，通过额外测试源码目录注入后单独运行，没有进入默认测试集，也没有连接既有演示库。受限账号保持 `agency_admin` 角色，先从列表读取 revision，再调用自己的 roles 更新接口。

| 检查 | 实际结果 |
|---|---|
| 更新前账户 / 合计消耗 | 仅 acc-a / 400.00 |
| 自身范围更新为 agencies=[] | HTTP 200；数据库保存 [] |
| 重新登录后账户 / 合计消耗 | acc-a、acc-b、acc-c / 650.00 |
| 筛选项 / 聚合 CSV | 同样包含另一代理与未映射账户 |
| 点名索取收益字段 | 仍为 403 FORBIDDEN，未越过字段权限 |
| 专项测试结果 | 1 项通过；含义是漏洞成功复现 |

只复现了 agencies 这一维度；products / operators 同用条件拼接，仍应由修复时的测试分别覆盖。此次没有将“断言漏洞存在”的测试混入常规 CI；修复应新增“空授权集不得返回数据”的回归测试。

推荐修复方向是把请求筛选与授权范围的空值语义分开：授权 null 保持不限，授权空数组产生恒假条件；同时核对 IAM 的列表可见性与授予逻辑，明确各维度的 null/[] 契约。修复应先保存上述失败证据，再同尺复测。未来 facts 拆分以修复后的权限行为为基线，不能为了「行为等价」机械保留该缺陷。

## 审计来源

- 决策：`docs/adr/0004-backend-stack.md`、`docs/design/module-boundaries.md`、`fact-grain.md`、`permission-matrix.md`、`task-reliability.md`。
- 表与 SQL：`backend-java/src/main/resources/db/migration/V1__core.sql`；`reporting/persistence/FactQueryRepository.java`；`ingestion/persistence/FactWriteRepository.java`。
- 行为：`reporting/ReportService.java`、`reporting/domain/Dimension.java`、`Summaries.java`、`shared/metrics/MetricRegistry.java`；`ingestion/ImportService.java`、`ImportProcessor.java`、`WorkbookParser.java`。
- 权限差异：`iam/UserAdminService.java`、`iam/domain/DataScope.java`、`backend/app/services/reports.py`、`backend/app/schemas/auth.py`、`contracts/openapi.json` 的 `DataScope`。
- 现有验收范围：`backend-java/src/test/java/com/iaaops/reporting/ReportingApiTest.java`、`ingestion/ImportApiTest.java`、`ModuleBoundaryTest.java`；`tools/parity/check.py`。
