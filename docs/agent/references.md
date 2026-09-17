# FDE 交付与 Agent 工程参考

调研日期：2026-09-18。以下四个仓库通过 GitHub API 核对为 MIT、未归档。这里借鉴设计思路，没有复制其实现代码或引入整套平台。OpenFDE 明确面向 FDE 工作；其余为 Agent 工程参考应用，不将它们包装成真实 FDE 客户案例。

| 项目 / 核对提交 | 借鉴 | 本项目的取舍 |
|---|---|---|
| [OpenFDE](https://github.com/memovai/openfde/tree/fda7b0649d71a68724eeb1971719ba9e2a8df25c) · MIT | 客户目标、约束、来源登记与交付文档 | 用来源和验收组织作品；目录隔离不能替代后端多用户权限 |
| [Microsoft Multi-Agent Custom Automation Engine](https://github.com/microsoft/Multi-Agent-Custom-Automation-Engine-Solution-Accelerator/tree/e89689e475eecf23ef2b48ad9e556bde16776e8d) · MIT | 计划面板、执行状态与人工审批 | 首版一个 Agent，复用 Java 单体；不引入 Azure 全套基础设施和多 Agent 编队 |
| [Azure Search OpenAI Demo](https://github.com/Azure-Samples/azure-search-openai-demo/tree/3f4a21f03ae3d565aca37cc300e3d38b0c7b582a) · MIT | 身份贯穿检索和来源访问，评测结果可比较 | 结构化投放表继续走已有报表接口，无需向量数据库；授权始终开启 |
| [LangChain Agent Inbox](https://github.com/langchain-ai/agent-inbox/tree/cb3af21f9bd3ec04161c0a3202d6eb344371f415) · MIT | 结构化动作卡、接受/拒绝和人工反馈 | 将预算/出价渲染为前后值差异；确认绑定提案版本，密钥只留服务端 |

## 具体设计依据

- OpenFDE [来源写入接口](https://github.com/memovai/openfde/blob/fda7b0649d71a68724eeb1971719ba9e2a8df25c/packages/core/src/ledger/ingest.ts#L29) 对缺少来源的输入直接报错。IAA 的证据也必须由实际工具结果产生，不能仅让模型自行编写引用。
- Microsoft 示例的 [计划面板](https://github.com/microsoft/Multi-Agent-Custom-Automation-Engine-Solution-Accelerator/blob/e89689e475eecf23ef2b48ad9e556bde16776e8d/src/App/src/components/content/PlanPanelRight.tsx) 和 [审批处理](https://github.com/microsoft/Multi-Agent-Custom-Automation-Engine-Solution-Accelerator/blob/e89689e475eecf23ef2b48ad9e556bde16776e8d/src/backend/orchestration/plan_review_helpers.py#L405) 将等待确认作为独立状态。IAA 的待确认修改也必须区别于已执行回执。
- Azure 示例的 [身份与来源访问检查](https://github.com/Azure-Samples/azure-search-openai-demo/blob/3f4a21f03ae3d565aca37cc300e3d38b0c7b582a/app/backend/core/authentication.py#L131) 提醒我们：聊天接口有权限还不够，证据和历史访问同样要检查。其 [评测说明](https://github.com/Azure-Samples/azure-search-openai-demo/blob/3f4a21f03ae3d565aca37cc300e3d38b0c7b582a/docs/evaluation.md) 用于参考测试集与结果记录的分离。
- Agent Inbox 的 [中断/审批契约](https://github.com/langchain-ai/agent-inbox/blob/cb3af21f9bd3ec04161c0a3202d6eb344371f415/README.md#interrupts) 将动作与参数一起交给人审阅。IAA 用持久提案绑定目标、数值、版本和发起人。

## 明确不照搬的演示便利

Microsoft 示例存在 [缺失身份时回退示例用户](https://github.com/microsoft/Multi-Agent-Custom-Automation-Engine-Solution-Accelerator/blob/e89689e475eecf23ef2b48ad9e556bde16776e8d/src/backend/auth/auth_utils.py#L5) 的开发路径。Agent Inbox 的 [配置说明](https://github.com/langchain-ai/agent-inbox/blob/cb3af21f9bd3ec04161c0a3202d6eb344371f415/README.md#configuration) 使用浏览器本地存储保存连接配置/API key。这些并不是本项目的权限或凭据设计：无有效登录不能查询，模型服务凭据不能进入前端。

模型工具调用遵循 [DeepSeek 官方 Tool Calls](https://api-docs.deepseek.com/guides/tool_calls/) 的调用/回传协议。这里只实现现有 Java 应用所需的有限 HTTP 适配；模型负责语义选择，代码负责允许的能力及确定性边界。
