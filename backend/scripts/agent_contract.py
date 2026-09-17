"""Java-only Agent DTO schema mirror; no Python business routes are introduced.

REQ-AGENT-001..008: docs/agent/requirements.md. Keep aligned with AgentDtos.java.
"""
from datetime import datetime
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class AgentSubmit(BaseModel):
    model_config = ConfigDict(extra="forbid")
    prompt: str = Field(min_length=1, max_length=4000)
    request_id: str = Field(pattern=r"^[a-zA-Z0-9_-]{1,64}$")
    context: dict[str, Any] | None = None


class AgentDecision(BaseModel):
    model_config = ConfigDict(extra="forbid")
    approve: bool
    request_id: str = Field(pattern=r"^[a-zA-Z0-9_-]{1,64}$")
    proposal_id: str = Field(min_length=1, max_length=32)


class AgentCapabilities(BaseModel):
    available: bool
    model: str | None
    unavailable_reason: str | None
    can_execute: bool
    execution_mode: str
    max_steps: int


class AgentCampaign(BaseModel):
    id: str
    name: str
    media: str
    account: str
    campaign: str
    agency: str | None
    product: str | None
    operator: str | None
    bid: float
    daily_budget: float
    revision: int
    execution_mode: str


class AgentAmounts(BaseModel):
    bid: float
    daily_budget: float


class AgentChange(BaseModel):
    campaign_id: str
    campaign_name: str
    account: str
    revision: int
    before: AgentAmounts
    after: AgentAmounts


class AgentAppliedChange(AgentChange):
    new_revision: int


class AgentProposal(BaseModel):
    id: str
    status: str
    summary: str
    changes: list[AgentChange]
    expires_at: datetime


class AgentReceipt(BaseModel):
    id: str
    run_id: str
    proposal_id: str
    execution_mode: str
    status: str
    changes: list[AgentAppliedChange]
    created_at: datetime


class AgentEvent(BaseModel):
    id: str
    tool: str
    status: str
    summary: str
    arguments: dict[str, Any]
    result: Any
    created_at: datetime


class AgentRun(BaseModel):
    id: str
    prompt: str
    status: Literal["running", "awaiting_confirmation", "succeeded", "rejected", "failed"]
    answer: str | None
    error: str | None
    events: list[AgentEvent]
    proposal: AgentProposal | None
    receipt: AgentReceipt | None
    created_at: datetime
    finished_at: datetime | None


class AgentCampaignList(BaseModel):
    items: list[AgentCampaign]


class AgentRunList(BaseModel):
    items: list[AgentRun]


class AgentReceiptList(BaseModel):
    items: list[AgentReceipt]


def add_agent_contract(schema: dict[str, Any], root: Path) -> None:
    controller = "backend-java/src/main/java/com/iaaops/agent/web/AgentController.java"
    if not (root / controller).is_file():
        raise ValueError("Agent controller is missing; cannot publish an implemented contract")
    models = (AgentSubmit, AgentDecision, AgentCapabilities, AgentCampaignList,
              AgentRunList, AgentRun, AgentReceiptList)
    for model in models:
        part = model.model_json_schema(ref_template="#/components/schemas/{model}")
        schema["components"]["schemas"].update(part.pop("$defs", {}))
        schema["components"]["schemas"][model.__name__] = part

    operations = [
        ("get", "capabilities", "getAgentCapabilities", "查询助手可用状态", AgentCapabilities, None, "200", [1, 2]),
        ("get", "campaigns", "listAgentCampaigns", "查询授权范围内的虚构计划", AgentCampaignList, None, "200", [2, 3]),
        ("get", "runs", "listAgentRuns", "查询本人运行记录", AgentRunList, None, "200", [2, 7]),
        ("post", "runs", "createAgentRun", "提交模型任务", AgentRun, AgentSubmit, "202", [1, 2, 3, 7]),
        ("get", "runs/{run_id}", "getAgentRun", "回读本人任务和证据", AgentRun, None, "200", [2, 3, 7]),
        ("post", "runs/{run_id}/decision", "decideAgentRun", "确认或拒绝具体沙箱变更", AgentRun, AgentDecision, "200", [2, 4, 5]),
        ("get", "receipts", "listAgentReceipts", "查询本人沙箱执行回执", AgentReceiptList, None, "200", [2, 5]),
    ]
    for method, suffix, operation_id, summary, response, request, success, requirements in operations:
        operation: dict[str, Any] = {
            "operationId": operation_id, "summary": summary, "tags": ["Agent工作台"],
            "security": [{"bearerAuth": []}],
            "x-requirements": [f"REQ-AGENT-{r:03d}" for r in requirements],
            "x-screens": ["agent-workspace"],
            "x-implementation": "implemented", "x-implementation-source": controller,
            "x-reference-implementation": {"runtime": "FastAPI", "status": "not_available"},
            "responses": {success: {"description": "成功", "content": {"application/json": {
                "schema": {"$ref": f"#/components/schemas/{response.__name__}"}}}}},
        }
        for status, description in {"401": "登录失效", "403": "无权限或授权已变化", "404": "目标不可见", "409": "请求、状态、版本冲突或任务容量已满", "422": "输入校验失败", "501": "模型接口尚未配置"}.items():
            operation["responses"][status] = {"description": description, "content": {
                "application/problem+json": {"schema": {"$ref": "#/components/schemas/Error"}}}}
        if request:
            operation["requestBody"] = {"required": True, "content": {"application/json": {
                "schema": {"$ref": f"#/components/schemas/{request.__name__}"}}}}
        if "{run_id}" in suffix:
            operation["parameters"] = [{"name": "run_id", "in": "path", "required": True,
                                        "schema": {"type": "string"}}]
        schema["paths"].setdefault("/api/v1/agent/" + suffix, {})[method] = operation
