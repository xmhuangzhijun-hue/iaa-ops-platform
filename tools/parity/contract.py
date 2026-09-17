"""按 contracts/openapi.json 校验响应结构。

对照脚本原来只比"两侧是否一致"。一旦某个接口只有 Java 实现，基准就没得比了——
这时用契约当裁判：字段齐不齐、类型对不对、有没有多出契约里没写的字段。

只做契约里实际用到的那部分 JSON Schema：$ref、anyOf、type、required、
properties、additionalProperties、items、enum、const。遇到不认识的关键字就跳过，
不假装自己是完整的校验器。
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

CONTRACT_PATH = Path(__file__).resolve().parents[2] / "contracts" / "openapi.json"

_TYPES: dict[str, type | tuple[type, ...]] = {
    "object": dict,
    "array": list,
    "string": str,
    "boolean": bool,
    "number": (int, float),
    "integer": int,
}


class Contract:
    def __init__(self, path: Path = CONTRACT_PATH) -> None:
        self.document = json.loads(path.read_text(encoding="utf-8"))

    def operation(self, method: str, path: str) -> dict[str, Any] | None:
        """按模板匹配路径：/api/v1/users/usr_1/roles 命中 /api/v1/users/{user_id}/roles。"""
        wanted = path.split("?")[0].strip("/").split("/")
        for template, methods in self.document["paths"].items():
            parts = template.strip("/").split("/")
            if len(parts) != len(wanted):
                continue
            if all(p.startswith("{") or p == w for p, w in zip(parts, wanted)):
                return methods.get(method.lower())
        return None

    def response_schema(self, method: str, path: str, status: int) -> dict[str, Any] | None:
        operation = self.operation(method, path)
        if operation is None:
            return None
        responses = operation.get("responses", {})
        response = responses.get(str(status)) or responses.get("default")
        if response is None:
            return None
        for media, media_type in response.get("content", {}).items():
            if "json" in media:
                return media_type.get("schema")
        return None

    def resolve(self, schema: dict[str, Any]) -> dict[str, Any]:
        while "$ref" in schema:
            node: Any = self.document
            for part in schema["$ref"].lstrip("#/").split("/"):
                node = node[part]
            schema = node
        return schema

    def validate(self, value: Any, schema: dict[str, Any] | None, path: str = "") -> list[str]:
        if schema is None:
            return []
        schema = self.resolve(schema)

        if "anyOf" in schema:
            branches = [self.validate(value, option, path) for option in schema["anyOf"]]
            return [] if any(not errors for errors in branches) else [
                f"{path or '<根>'}: 不符合任何一种允许的形状（{_brief(value)}）"
            ]
        if "const" in schema and value != schema["const"]:
            return [f"{path or '<根>'}: 应为 {schema['const']!r}，实际 {_brief(value)}"]
        if "enum" in schema and value not in schema["enum"]:
            return [f"{path or '<根>'}: 不在允许取值内，实际 {_brief(value)}"]

        expected = schema.get("type")
        if expected == "null":
            return [] if value is None else [f"{path or '<根>'}: 应为 null，实际 {_brief(value)}"]
        if expected in _TYPES:
            if expected == "number" and isinstance(value, bool):
                return [f"{path or '<根>'}: 应为数字，实际布尔值"]
            if not isinstance(value, _TYPES[expected]) or (expected != "boolean" and isinstance(value, bool)):
                return [f"{path or '<根>'}: 应为 {expected}，实际 {_brief(value)}"]

        errors: list[str] = []
        if expected == "object" or "properties" in schema:
            errors.extend(self._object(value, schema, path))
        if expected == "array" and isinstance(value, list):
            for index, item in enumerate(value):
                errors.extend(self.validate(item, schema.get("items"), f"{path}[{index}]"))
        return errors

    def _object(self, value: Any, schema: dict[str, Any], path: str) -> list[str]:
        if not isinstance(value, dict):
            return []
        errors: list[str] = []
        properties = schema.get("properties", {})
        for name in schema.get("required", []):
            if name not in value:
                errors.append(f"{_join(path, name)}: 契约要求该字段，响应里没有")
        extra = schema.get("additionalProperties")
        for key, item in value.items():
            if key in properties:
                errors.extend(self.validate(item, properties[key], _join(path, key)))
            elif extra is False:
                errors.append(f"{_join(path, key)}: 契约里没有这个字段")
            elif isinstance(extra, dict):
                errors.extend(self.validate(item, extra, _join(path, key)))
        return errors


def _join(path: str, name: str) -> str:
    return f"{path}.{name}" if path else name


def _brief(value: Any) -> str:
    text = repr(value)
    return text if len(text) <= 60 else text[:57] + "…"
