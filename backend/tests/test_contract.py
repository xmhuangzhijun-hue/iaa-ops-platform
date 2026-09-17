import json
import re
from pathlib import Path
from typing import Any, get_args

import pytest

from app.domain.access import ROLES
from app.domain.dimensions import DIMENSIONS
from app.domain.metrics import METRIC_KEYS
from app.main import app
from app.schemas.admin import Role
from app.schemas.reports import Dimension, MetricKey
from scripts.export_openapi import build_contract

CONTRACT = Path(__file__).resolve().parents[2] / "contracts" / "openapi.json"
METHODS = {"get", "post", "put", "patch", "delete"}
REQUIREMENT_ID = re.compile(r"^IAA-REQ-\d{3}$")


def operations() -> list[tuple[str, str, dict[str, Any]]]:
    return [
        (path, method, operation)
        for path, item in app.openapi()["paths"].items()
        for method, operation in item.items()
        if method in METHODS
    ]


PLANNED = [entry for entry in operations() if entry[2]["x-implementation"] == "planned"]


def test_committed_contract_matches_code():
    committed = json.loads(CONTRACT.read_text(encoding="utf-8"))
    assert committed == json.loads(json.dumps(build_contract())), "契约已漂移：运行 uv run python scripts/export_openapi.py"


def test_java_contract_preserves_reference_runtime_status():
    java = build_contract()
    python = app.openapi()
    assert len(operations()) == 24
    assert len(PLANNED) == 8
    for path, method, reference in operations():
        operation = java["paths"][path][method]
        assert operation["x-implementation"] == "implemented"
        assert operation["x-reference-implementation"] == {
            "runtime": "FastAPI", "status": reference["x-implementation"],
        }
        assert "501" not in operation["responses"]
        assert ("501" in python["paths"][path][method]["responses"]) == (
            reference["x-implementation"] == "planned"
        )


def test_schema_literals_follow_domain_registries():
    assert get_args(MetricKey) == METRIC_KEYS
    assert get_args(Dimension) == tuple(DIMENSIONS)
    assert get_args(Role) == ROLES


def test_error_codes_form_a_closed_described_set(client):
    code = app.openapi()["components"]["schemas"]["Error"]["properties"]["code"]
    assert set(code["enum"]) == set(code["x-code-descriptions"])
    response = client.get("/api/v1/no-such-path")
    assert response.status_code == 404
    assert response.json()["code"] == "NOT_FOUND"


def test_every_operation_is_traceable_and_has_examples():
    seen: set[str] = set()
    for path, method, operation in operations():
        label = f"{method.upper()} {path}"
        assert operation["operationId"] not in seen, label
        seen.add(operation["operationId"])
        assert operation.get("summary") and operation.get("tags"), label
        assert operation["x-requirements"], label
        assert all(REQUIREMENT_ID.match(req) for req in operation["x-requirements"]), label
        assert operation["x-implementation"] in {"implemented", "planned"}, label
        for mime, media in operation.get("requestBody", {}).get("content", {}).items():
            assert media.get("examples"), f"{label} 请求体 {mime} 缺少示例"
        for code, response in operation["responses"].items():
            if code.startswith("2"):
                for mime, media in response.get("content", {}).items():
                    assert media.get("examples"), f"{label} {code} {mime} 缺少示例"
            if code[0] in "45":
                assert set(response["content"]) == {"application/problem+json"}, f"{label} {code}"


def _sample(schema: dict[str, Any]) -> str:
    if schema.get("format") == "date":
        return "2026-09-15"
    if "enum" in schema:
        return schema["enum"][0]
    return "demo"


@pytest.mark.parametrize("path,method,operation", PLANNED, ids=[entry[2]["operationId"] for entry in PLANNED])
def test_planned_operation_accepts_its_example_and_answers_501(client, path, method, operation):
    url, params = path, {}
    for parameter in operation.get("parameters", []):
        if parameter["in"] == "path":
            url = url.replace("{" + parameter["name"] + "}", _sample(parameter["schema"]))
        elif parameter.get("required"):
            params[parameter["name"]] = _sample(parameter["schema"])
    kwargs: dict[str, Any] = {"params": params}
    content = operation.get("requestBody", {}).get("content", {})
    if "application/json" in content:
        kwargs["json"] = next(iter(content["application/json"]["examples"].values()))["value"]
    elif "multipart/form-data" in content:
        kwargs["data"] = {"media": "vivo"}
        kwargs["files"] = {"file": ("demo.xlsx", b"PK\x03\x04", "application/octet-stream")}

    response = client.request(method.upper(), url, **kwargs)

    assert response.status_code == 501, response.text
    assert response.headers["content-type"].startswith("application/problem+json")
    assert response.json()["code"] == "NOT_IMPLEMENTED"


def test_health(client):
    assert client.get("/api/v1/health").json()["status"] == "ok"


def test_metric_catalog_is_the_domain_registry(client):
    items = client.get("/api/v1/metrics").json()["items"]
    assert [item["key"] for item in items] == list(METRIC_KEYS)
