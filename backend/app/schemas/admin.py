from datetime import date, datetime
from typing import Literal

from pydantic import Field, JsonValue

from app.schemas.auth import DataScope
from app.schemas.common import ApiModel

Role = Literal["super_admin", "company_admin", "operator", "agency_admin", "customer", "readonly"]
UserStatus = Literal["active", "disabled"]
ImportStatus = Literal["pending", "processing", "succeeded", "failed"]


class MetricDefinition(ApiModel):
    key: str
    label: str
    kind: Literal["base", "derived"]
    unit: Literal["money", "count", "ratio", "per_unit"]
    formula: str
    description: str
    precision: int


class MetricCatalog(ApiModel):
    items: list[MetricDefinition]


class AccountMapping(ApiModel):
    account: str
    media: str
    agency: str | None
    product: str | None
    operator: str | None
    revision: int


class AccountMappingPage(ApiModel):
    items: list[AccountMapping]
    total: int


class AccountMappingInput(ApiModel):
    account: str = Field(min_length=1, max_length=64)
    media: str = Field(min_length=1, max_length=32)
    agency: str | None = None
    product: str | None = None
    operator: str | None = None
    revision: int | None = Field(default=None, description="null 表示新建；更新时须带最后读到的版本号")


class AccountMappingUpsert(ApiModel):
    items: list[AccountMappingInput] = Field(min_length=1, max_length=500)


class UpsertResult(ApiModel):
    created: int
    updated: int
    unchanged: int


class ImportRowError(ApiModel):
    row: int
    column: str | None
    message: str


class ImportTask(ApiModel):
    id: str
    media: str
    file_name: str
    status: ImportStatus
    stat_dates: list[date] = Field(description="文件覆盖的日期；入库按日期整天替换，重复导入不会累加")
    rows_total: int
    rows_replaced: int
    errors: list[ImportRowError]
    created_at: datetime
    finished_at: datetime | None


class UserSummary(ApiModel):
    id: str
    username: str
    display_name: str
    roles: list[Role]
    status: UserStatus
    data_scope: DataScope
    revision: int


class UserPage(ApiModel):
    items: list[UserSummary]
    total: int


class UserCreate(ApiModel):
    username: str = Field(pattern=r"^[a-z0-9_.-]{3,32}$")
    display_name: str = Field(min_length=1, max_length=32)
    roles: list[Role] = Field(min_length=1)
    data_scope: DataScope = Field(default_factory=DataScope)


class UserCreated(ApiModel):
    user: UserSummary
    one_time_password: str = Field(description="仅在本响应返回一次；首次登录必须修改")
    must_change_password: Literal[True] = True


class UserRolesUpdate(ApiModel):
    roles: list[Role] = Field(min_length=1)
    data_scope: DataScope
    revision: int = Field(ge=0)


class AuditEvent(ApiModel):
    id: str
    occurred_at: datetime
    actor: str
    action: str
    target_type: str
    target_id: str
    detail: dict[str, JsonValue] = Field(
        description="随动作而定的前后值；键名由动作决定，值可以是嵌套结构（如角色列表、数据范围）"
    )


class AuditEventPage(ApiModel):
    items: list[AuditEvent]
    next_cursor: str | None
