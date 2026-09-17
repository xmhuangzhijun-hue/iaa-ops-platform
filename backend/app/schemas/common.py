from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

ErrorCode = Literal[
    "AUTH_REQUIRED", "INVALID_CREDENTIALS", "PASSWORD_CHANGE_REQUIRED", "FORBIDDEN", "NOT_FOUND",
    "METHOD_NOT_ALLOWED", "CONFLICT", "PAYLOAD_TOO_LARGE", "VALIDATION_FAILED", "RESULT_TOO_LARGE",
    "INTERNAL_ERROR", "NOT_IMPLEMENTED", "HTTP_ERROR",
]
ERROR_CODE_DESCRIPTIONS: dict[str, str] = {
    "AUTH_REQUIRED": "未登录、令牌缺失或已失效，需要重新登录。",
    "INVALID_CREDENTIALS": "账号或口令错误，或登录暂被限制；不区分账号不存在、口令错误、账号停用与锁定。",
    "PASSWORD_CHANGE_REQUIRED": "正在使用临时口令，修改口令前只能访问当前账号与改密接口。",
    "FORBIDDEN": "没有该操作的权限，或请求超出账号的数据范围。",
    "NOT_FOUND": "对象或路径不存在。",
    "METHOD_NOT_ALLOWED": "路径存在，但不支持该 HTTP 方法。",
    "CONFLICT": "对象已被他人修改（revision 不一致）或唯一键冲突，刷新后重试。",
    "PAYLOAD_TOO_LARGE": "上传文件超过大小限制。",
    "VALIDATION_FAILED": "请求参数未通过校验，errors 列出具体字段。",
    "RESULT_TOO_LARGE": "分组数或导出行数超过上限，请缩小日期范围或减少分组维度。",
    "INTERNAL_ERROR": "服务端异常，已记录，可稍后重试。",
    "NOT_IMPLEMENTED": "契约已确定，接口尚未实现。",
    "HTTP_ERROR": "其他 HTTP 错误，以 status 为准。",
}


class ApiModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class FieldError(ApiModel):
    field: str
    message: str


class Error(ApiModel):
    """RFC 9457 Problem Details（application/problem+json），所有 4xx/5xx 统一使用。"""

    type: str = "about:blank"
    title: str
    status: int
    code: ErrorCode = Field(json_schema_extra={"x-code-descriptions": ERROR_CODE_DESCRIPTIONS})
    detail: str | None = None
    errors: list[FieldError] | None = None


class Health(ApiModel):
    status: Literal["ok"]
    version: str
    environment: str
