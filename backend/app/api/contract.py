"""把追溯字段与示例挂到每个接口上，供 dev-plm 读取契约。"""

from typing import Any, Literal

from app.schemas.common import Error

Implementation = Literal["implemented", "planned"]
Responses = dict[int | str, dict[str, Any]]

PROBLEM_DESCRIPTIONS = {
    401: "未登录或令牌已失效",
    403: "无权限，或超出账号的数据范围",
    404: "对象不存在",
    409: "版本已变化，请刷新后重试",
    413: "上传文件超过大小限制",
    501: "契约已确定，接口尚未实现",
}


def contract(
    *,
    reqs: list[str],
    screens: list[str] | None = None,
    fields: list[str] | None = None,
    status: Implementation = "planned",
    public: bool = False,
) -> dict[str, Any]:
    extra: dict[str, Any] = {
        "x-requirements": reqs,
        "x-screens": screens or [],
        "x-fields": fields or [],
        "x-implementation": status,
    }
    if public:
        extra["security"] = []
    return extra


def named_example(value: Any, summary: str) -> dict[str, dict[str, Any]]:
    return {"demo": {"summary": summary, "value": value}}


def ok(value: Any, summary: str, *, status: int = 200, media_type: str = "application/json") -> Responses:
    return {status: {"content": {media_type: {"examples": named_example(value, summary)}}}}


def problems(*codes: int) -> Responses:
    return {code: {"model": Error, "description": PROBLEM_DESCRIPTIONS[code]} for code in codes}
