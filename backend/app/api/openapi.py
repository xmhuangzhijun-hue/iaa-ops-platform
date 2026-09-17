from typing import Any

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

PROBLEM_REF = "#/components/schemas/Error"
PROBLEM_MEDIA_TYPE = "application/problem+json"

SERVERS = [
    {"url": "http://127.0.0.1:8000", "description": "本机开发", "x-environment": "development"},
    {"url": "http://127.0.0.1:8001", "description": "本机测试（验收时临时启动，非常驻）", "x-environment": "testing"},
    {"url": "https://iaa-ops.invalid", "description": "公开演示环境，尚未部署；上线后替换为真实地址",
     "x-environment": "production"},
]

TAGS = [
    {"name": "系统", "description": "健康检查等运维接口。"},
    {"name": "认证与偏好", "description": "登录、令牌、当前账号与账号级界面偏好。"},
    {"name": "指标", "description": "指标口径目录，与后端计算共用同一份定义。"},
    {"name": "看盘分析", "description": "聚合、分天、趋势、ROI 异常、原始明细与导出，均受账号数据范围约束。"},
    {"name": "映射管理", "description": "账户与代理、产品、运营的归属映射。"},
    {"name": "数据导入", "description": "媒体后台导出表的上传与按天幂等入库。"},
    {"name": "用户与权限", "description": "账号、角色与数据范围。"},
    {"name": "审计", "description": "关键写操作的审计记录。"},
]


def _problem_media_types(schema: dict[str, Any]) -> None:
    """错误响应统一为 application/problem+json，并替换 FastAPI 默认的 422 模型。"""
    for path_item in schema.get("paths", {}).values():
        for operation in path_item.values():
            responses = operation.get("responses", {})
            if "422" in responses:
                responses["422"] = {
                    "description": "请求参数校验失败",
                    "content": {PROBLEM_MEDIA_TYPE: {"schema": {"$ref": PROBLEM_REF}}},
                }
            for code, response in responses.items():
                if code[0] not in "45":
                    continue
                content = response.get("content", {})
                for media_type in list(content):
                    if media_type != PROBLEM_MEDIA_TYPE and content[media_type].get("schema", {}).get("$ref") == PROBLEM_REF:
                        content[PROBLEM_MEDIA_TYPE] = content.pop(media_type)
    schemas = schema.get("components", {}).get("schemas", {})
    schemas.pop("HTTPValidationError", None)
    schemas.pop("ValidationError", None)


def build_openapi(app: FastAPI) -> dict[str, Any]:
    if app.openapi_schema:
        return app.openapi_schema
    schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
        tags=app.openapi_tags,
        separate_input_output_schemas=False,
    )
    schema["servers"] = SERVERS
    schema.setdefault("components", {}).setdefault("securitySchemes", {})["bearerAuth"] = {
        "type": "http", "scheme": "bearer", "bearerFormat": "JWT",
    }
    schema["security"] = [{"bearerAuth": []}]
    _problem_media_types(schema)
    app.openapi_schema = schema
    return schema
