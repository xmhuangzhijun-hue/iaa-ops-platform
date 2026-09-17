"""从 FastAPI 模型导出当前 Java 服务的 OpenAPI 契约。

Python 路由仍保留原有实现状态；本导出层单独记录已核对的 Java 操作，
不能因 Java 实现完成就把 Python 的 501 标成已实现。

    uv run python scripts/export_openapi.py            # 写入 contracts/openapi.json
    uv run python scripts/export_openapi.py --check    # 契约与代码不一致时退出码 1
    uv run python scripts/export_openapi.py --copy-to <路径>   # 另写一份（如 dev-plm 项目资料）
"""

import argparse
from copy import deepcopy
import json
import sys
from pathlib import Path
from typing import Any

BACKEND = Path(__file__).resolve().parents[1]
CONTRACT = BACKEND.parent / "contracts" / "openapi.json"
sys.path.insert(0, str(BACKEND))

from app.main import app  # noqa: E402
from scripts.agent_contract import add_agent_contract  # noqa: E402


METHODS = {"get", "post", "put", "patch", "delete"}
# 过渡期显式登记 Java 的已实现操作与控制器。新增接口必须同时核对并更新这里，
# 不把 Python 中未来新增的 planned 操作自动提升为 implemented。
JAVA_OPERATIONS = {
    "getHealth": ("get", "/api/v1/health", "system/SystemController.java"),
    "listMetrics": ("get", "/api/v1/metrics", "reporting/web/MetricCatalogController.java"),
    "login": ("post", "/api/v1/auth/login", "iam/web/AuthController.java"),
    "refreshToken": ("post", "/api/v1/auth/refresh", "iam/web/AuthController.java"),
    "logout": ("post", "/api/v1/auth/logout", "iam/web/AuthController.java"),
    "changePassword": ("post", "/api/v1/auth/password", "iam/web/AuthController.java"),
    "getCurrentPrincipal": ("get", "/api/v1/auth/me", "iam/web/AuthController.java"),
    "getPreferences": ("get", "/api/v1/me/preferences", "iam/web/AuthController.java"),
    "updatePreferences": ("put", "/api/v1/me/preferences", "iam/web/AuthController.java"),
    "getFilterOptions": ("get", "/api/v1/filter-options", "reporting/web/ReportController.java"),
    "queryAggregate": ("post", "/api/v1/reports/aggregate", "reporting/web/ReportController.java"),
    "queryDaily": ("post", "/api/v1/reports/daily", "reporting/web/ReportController.java"),
    "queryTrend": ("post", "/api/v1/reports/trend", "reporting/web/ReportController.java"),
    "queryRoiAnomalies": ("post", "/api/v1/reports/roi-anomalies", "reporting/web/ReportController.java"),
    "queryRawDetail": ("post", "/api/v1/reports/raw", "reporting/web/ReportController.java"),
    "exportReport": ("post", "/api/v1/reports/export", "reporting/web/ReportController.java"),
    "listAccountMappings": ("get", "/api/v1/mappings/accounts", "mapping/web/MappingController.java"),
    "upsertAccountMappings": ("put", "/api/v1/mappings/accounts", "mapping/web/MappingController.java"),
    "createImport": ("post", "/api/v1/imports", "ingestion/web/ImportController.java"),
    "getImport": ("get", "/api/v1/imports/{import_id}", "ingestion/web/ImportController.java"),
    "listUsers": ("get", "/api/v1/users", "iam/web/UserAdminController.java"),
    "createUser": ("post", "/api/v1/users", "iam/web/UserAdminController.java"),
    "updateUserRoles": ("put", "/api/v1/users/{user_id}/roles", "iam/web/UserAdminController.java"),
    "listAuditEvents": ("get", "/api/v1/audit-events", "governance/web/AuditController.java"),
}


def build_contract() -> dict[str, Any]:
    schema = deepcopy(app.openapi())
    # Java 发布版本与冻结的 Python 对照服务版本分别维护。
    schema["info"]["version"] = "1.0.0-rc.1"
    schema["info"]["description"] = (
        "IAA 投放运营中台 Java 业务服务契约；示例数据均为虚构。"
        "模型暂由 FastAPI 导出，x-implementation 表示 Java 状态；"
        "x-reference-implementation 保留 Python 对照服务的实际状态。"
    )
    # dev-plm 的环境导航引用这三个稳定标识；占位地址不表示环境已经部署。
    schema["servers"] = [
        {"url": "http://127.0.0.1:8080", "description": "本机 Java 业务服务",
         "x-environment": "development"},
        {"url": "http://127.0.0.1:18081", "description": "本机 Java 隔离验收示例，按需启动，非常驻入口",
         "x-environment": "testing"},
        {"url": "https://iaa-ops.invalid", "description": "公开演示规划占位，尚未部署；发布后替换为真实地址",
         "x-environment": "production"},
    ]
    schema["x-contract-source"] = {
        "schemas": "backend/app",
        "runtime": "backend-java",
        "exporter": "backend/scripts/export_openapi.py",
        "reference-runtime": "backend (FastAPI; 8 operations still return 501)",
    }
    # F-01 修复后的授权语义属于 Java；不修改旧 Python 查询的行为或伪装两者等价。
    scope = schema["components"]["schemas"]["DataScope"]
    scope["description"] = (
        "Java 服务端授权范围；维度之间取交集。省略或 null 表示该维度不限，"
        "空数组 [] 表示该维度没有任何授权值，查询不会返回事实数据。"
        "这是授权范围，不是报表请求中用于清除筛选条件的空筛选数组。"
    )
    for key, label in {"agencies": "代理", "products": "产品", "operators": "运营"}.items():
        scope["properties"][key]["description"] = f"可见{label}；null 或省略表示不限；[] 表示无授权值"
    seen: set[str] = set()
    for path, path_item in schema["paths"].items():
        for method, operation in path_item.items():
            if method not in METHODS:
                continue
            operation_id = operation["operationId"]
            if operation_id not in JAVA_OPERATIONS:
                raise ValueError(f"Java 实现状态未核对：{operation_id}")
            expected_method, expected_path, controller = JAVA_OPERATIONS[operation_id]
            if (method, path) != (expected_method, expected_path) or operation_id in seen:
                raise ValueError(f"Java 操作映射已漂移：{operation_id}")
            seen.add(operation_id)
            source = "backend-java/src/main/java/com/iaaops/" + controller
            if not (BACKEND.parent / source).is_file():
                raise ValueError(f"Java 控制器不存在：{source}")
            operation["x-reference-implementation"] = {
                "runtime": "FastAPI", "status": operation["x-implementation"],
            }
            operation["x-implementation"] = "implemented"
            operation["x-implementation-source"] = source
            # 501 仍由 Python 原始 schema 描述，不属于已实现的 Java 接口响应。
            operation["responses"].pop("501", None)
    if seen != JAVA_OPERATIONS.keys():
        raise ValueError(f"Java 操作缺少契约：{sorted(JAVA_OPERATIONS.keys() - seen)}")
    add_agent_contract(schema, BACKEND.parent)
    return schema


def render() -> str:
    return json.dumps(build_contract(), ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="只检查，不写文件")
    parser.add_argument("--copy-to", action="append", default=[], type=Path, help="额外写入的位置，可重复")
    args = parser.parse_args()

    text = render()
    if args.check:
        current = CONTRACT.read_text(encoding="utf-8") if CONTRACT.exists() else ""
        if current != text:
            print(f"{CONTRACT} 与代码不一致，请运行 uv run python scripts/export_openapi.py", file=sys.stderr)
            return 1
        print("契约与代码一致")
        return 0

    for target in [CONTRACT, *args.copy_to]:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")
        print(f"已写入 {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
