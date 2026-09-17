# F-01 授权范围修复记录

日期：2026-09-17。验证环境：独立 Testcontainers PostgreSQL，未修改本机演示账号的授权。

## 症状与期望

代理账号通过真实 roles 接口将自身 agencies 保存为 [] 后，原本仅可见 acc-a / 消耗 400，扩大到 acc-a、acc-b、acc-c / 消耗 650。报表、筛选与 CSV 均受到影响；收益字段权限仍生效。历史复现详见 `docs/design/facts-module-split.md`。

缺省或 null 表示某维度不限；授权空数组表示该维度没有可见事实；非空数组按各维度交集限制。普通查询筛选的空数组仍然表示不额外筛选。

## 修改前证据

新增永久 `DataScopeApiTest`，在修复前运行 11 项：7 项通过、4 项按预期失败。失败分别为自身 roles 更新后的访问，以及 agencies/products/operators 三个空授权集；null、不传范围、普通空筛选、非空交集和租户隔离对照通过。

## 修复与同尺验证

`FactQueryRepository` 将授权限制与普通筛选拆成不同入口。授权 [] 增加 SQL `false`，因此所有复用条件的报表、筛选、导出和时间戳查询都受约束。没有修改数据，也没有禁止用户合法收窄授权。

同一组 11 项测试修复后全部通过，覆盖已签发和重新登录令牌、聚合、分天、趋势、ROI 异常、原始明细、筛选可选值及三种 CSV；空集无数据且无 data_as_of，正常请求筛选行为不变。

## 邻近权限检查

另发现账号管理目标可见性仅校验 agencies，漏了 products/operators。新增 `UserScopeVisibilityApiTest` 修改前运行 9 项，5 项失败：产品/运营范围的列表与更新越界，以及多维交集。两个未限制主体对照与原代理维度对照通过。

`UserAdminService.visibleTo` 现对三个维度分别检查并取交集。受限管理者只能管理各受限维度上有明确非空归属、且属于自身范围的账号；目标不限、空归属、超范围和范围外均不可见，即使提交的新范围收窄也不能修改。空归属账号仍由不限范围的上级管理者处理。拒绝时不改范围、revision、角色，也不写成功审计。

最终 `gradle build`：98 项、0 失败、0 错误；包含全部原有 78 项与本轮 20 项。IAM 同一组 9 项全部通过，并补验了同范围/非空子范围目标可以成功更新，不会因修复阻断正常管理。

## 防回归

- 永久用例：`backend-java/src/test/java/com/iaaops/reporting/DataScopeApiTest.java`。
- 邻近回归：`backend-java/src/test/java/com/iaaops/iam/UserScopeVisibilityApiTest.java`。
- 每次 `gradle build` 与 GitHub Java 作业均执行真实数据库鉴权与查询链路。
- 后续 facts 拆分必须保持本记录的 null/[]/非空交集语义；不要复用请求筛选的空值判断。

本记录只证明列出的行为；不将未执行的公开部署和远程 CI 记为通过。
