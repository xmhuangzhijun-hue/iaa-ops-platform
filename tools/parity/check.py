#!/usr/bin/env python3
"""两个后端实现的逐字段对照。

迁移期同一个接口先后由 FastAPI 与 Java 提供，这个脚本把同一批请求打到两边、逐字段比响应，
用既有实现当验收基准。只依赖标准库，两个服务都起着就能跑：

    python tools/parity/check.py

差异分两类：
- 有意差异写在 KNOWN_DIFFS 里，带原因，只提示不失败；
- 其余差异一律失败退出，退出码非 0。

注意：两边必须连同一个数据库，否则比的是数据不是实现。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from typing import Any

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent))
from contract import Contract  # noqa: E402

DEMO_PASSWORD = "iaa-demo-2026"  # 仅对应仓库内的虚构演示数据
ROLES = ("demo.admin", "demo.company", "demo.operator", "demo.agency", "demo.readonly")

# 每次请求都会变的字段：只比是否存在与类型，不比值。
VOLATILE = {"access_token", "refresh_token", "expires_in"}
# 时间戳字段：解析成时刻再比，不比字符串写法。
INSTANT_FIELDS = {"data_as_of"}


@dataclass(frozen=True)
class Case:
    """一次对照请求。role 为 None 表示不带令牌。"""

    name: str
    method: str
    path: str
    role: str | None = "demo.admin"
    body: dict[str, Any] | None = None
    params: dict[str, Any] | None = None
    headers: dict[str, str] = field(default_factory=dict)
    # Java 已实现、既有实现仍是 501 的接口：基准在这里已经落后，不比两侧，改由契约当裁判
    beyond: bool = False


@dataclass(frozen=True)
class KnownDiff:
    """有意差异：case 名 + 字段路径（"status" 表示状态码），带原因。"""

    case: str
    path: str  # "http_status" 指 HTTP 状态码，其余为响应体字段路径
    reason: str


KNOWN_DIFFS = (
    KnownDiff("未认证访问不存在的路径", "http_status",
              "Java 先鉴权再路由，未认证一律 401，不泄露路径是否存在；带令牌时两侧同为 404"),
    KnownDiff("未认证访问不存在的路径", "status", "同上"),
    KnownDiff("未认证访问不存在的路径", "code", "同上"),
    KnownDiff("未认证访问不存在的路径", "title", "同上"),
    KnownDiff("未认证访问不存在的路径", "detail", "同上"),
    KnownDiff("已认证访问不存在的路径", "title",
              "title / detail 是给人看的文本，契约只约束 status 与 code；Java 侧统一中文标题并回显请求路径"),
    KnownDiff("已认证访问不存在的路径", "detail", "同上"),
    KnownDiff("方法不支持", "title", "同上"),
    KnownDiff("方法不支持", "detail", "同上"),
    KnownDiff("健康检查", "version", "两个服务各自的版本号，本来就不同"),
    KnownDiff("令牌无效", "detail", "同上"),
    *[KnownDiff(case, "errors[].message",
                "校验消息由各自框架生成（pydantic 会加 'Value error, ' 前缀），契约只约束 code 与出错字段")
      for case in ("登录缺字段", "聚合跨度超限", "聚合日期倒置")],
)

# 日期相对今天算，重新灌种子数据后不用改脚本；演示数据默认覆盖最近 60 天。
_TODAY = date.today()


def _day(offset: int) -> str:
    return (_TODAY - timedelta(days=offset)).isoformat()


TODAY_RANGE = {"date_from": _day(28), "date_to": _day(1)}
FILTERS = {"media": [], "products": [], "agencies": [], "accounts": [], "operators": []}


def _query(extra: dict[str, Any] | None = None) -> dict[str, Any]:
    body: dict[str, Any] = {**TODAY_RANGE, "filters": FILTERS}
    body.update(extra or {})
    return body


CASES: tuple[Case, ...] = (
    # —— 公开接口 ——
    Case("健康检查", "GET", "/api/v1/health", role=None),
    Case("指标口径目录", "GET", "/api/v1/metrics", role=None),
    # —— 认证与偏好 ——
    *[Case(f"当前账号 {role}", "GET", "/api/v1/auth/me", role=role) for role in ROLES],
    *[Case(f"界面偏好 {role}", "GET", "/api/v1/me/preferences", role=role) for role in ROLES],
    # —— 错误路径 ——
    Case("口令错误", "POST", "/api/v1/auth/login", role=None,
         body={"username": "demo.admin", "password": "wrong-password"}),
    Case("未带令牌访问当前账号", "GET", "/api/v1/auth/me", role=None),
    Case("令牌无效", "GET", "/api/v1/auth/me", role=None, headers={"Authorization": "Bearer not-a-token"}),
    Case("登录缺字段", "POST", "/api/v1/auth/login", role=None, body={"username": "demo.admin"}),
    Case("刷新令牌无效", "POST", "/api/v1/auth/refresh", role=None, body={"refresh_token": "nope"}),
    Case("未认证访问不存在的路径", "GET", "/api/v1/no-such-path", role=None),
    Case("已认证访问不存在的路径", "GET", "/api/v1/no-such-path"),
    Case("方法不支持", "GET", "/api/v1/auth/login", role=None),
    # —— 筛选可选值 ——
    *[Case(f"筛选可选值 {role}", "GET", "/api/v1/filter-options", role=role, params=TODAY_RANGE)
      for role in ("demo.admin", "demo.operator", "demo.agency")],
    Case("筛选可选值日期倒置", "GET", "/api/v1/filter-options",
         params={"date_from": _day(1), "date_to": _day(28)}),
    # —— 聚合 ——
    Case("聚合默认列", "POST", "/api/v1/reports/aggregate", body=_query()),
    Case("聚合多维度", "POST", "/api/v1/reports/aggregate",
         body=_query({"group_by": ["media", "product"], "page_size": 20})),
    Case("聚合指定指标与排序", "POST", "/api/v1/reports/aggregate",
         body=_query({"group_by": ["account"], "metrics": ["cost", "clicks", "cpc", "ctr"],
                      "sort": [{"field": "clicks", "direction": "asc"}], "page": 2, "page_size": 10})),
    Case("聚合关键词过滤", "POST", "/api/v1/reports/aggregate",
         body=_query({"group_by": ["product"], "keyword": "记账"})),
    Case("聚合按代理范围", "POST", "/api/v1/reports/aggregate", role="demo.agency",
         body=_query({"group_by": ["product"]})),
    Case("聚合运营范围", "POST", "/api/v1/reports/aggregate", role="demo.operator",
         body=_query({"group_by": ["account"], "page_size": 5})),
    Case("对外口径请求收益指标被拒", "POST", "/api/v1/reports/aggregate", role="demo.agency",
         body=_query({"group_by": ["product"], "metrics": ["cost", "revenue"]})),
    Case("聚合排序字段不在列中", "POST", "/api/v1/reports/aggregate",
         body=_query({"group_by": ["product"], "sort": [{"field": "launches"}]})),
    Case("聚合跨度超限", "POST", "/api/v1/reports/aggregate",
         body=_query({"date_from": _day(200), "date_to": _day(1)})),
    Case("聚合日期倒置", "POST", "/api/v1/reports/aggregate",
         body=_query({"date_from": _day(1), "date_to": _day(28)})),
    # —— 分天 ——
    Case("分天默认", "POST", "/api/v1/reports/daily",
         body=_query({"date_from": _day(3), "date_to": _day(1), "group_by": ["product"]})),
    Case("分天按媒体", "POST", "/api/v1/reports/daily",
         body=_query({"date_from": _day(2), "date_to": _day(1), "group_by": ["media"],
                      "metrics": ["cost", "revenue", "roi"]})),
    # —— 趋势 ——
    Case("趋势按天", "POST", "/api/v1/reports/trend", body=_query({"metrics": ["cost", "roi"]})),
    Case("趋势按天拆维度", "POST", "/api/v1/reports/trend",
         body=_query({"metrics": ["cost"], "split_by": "product"})),
    Case("趋势按小时", "POST", "/api/v1/reports/trend",
         body=_query({"date_from": _day(3), "date_to": _day(1),
                      "granularity": "hour", "metrics": ["cost", "clicks"]})),
    Case("趋势按小时超 7 天", "POST", "/api/v1/reports/trend",
         body=_query({"granularity": "hour", "metrics": ["cost"]})),
    Case("趋势对外口径无权", "POST", "/api/v1/reports/trend", role="demo.agency", body=_query()),
    # —— ROI 异常 ——
    Case("ROI 异常默认", "POST", "/api/v1/reports/roi-anomalies", body=_query()),
    Case("ROI 异常自定阈值", "POST", "/api/v1/reports/roi-anomalies",
         body=_query({"group_by": ["product", "media"], "roi_below": 1.05, "min_cost": 500})),
    Case("ROI 异常只读账号无权", "POST", "/api/v1/reports/roi-anomalies", role="demo.readonly", body=_query()),
    # —— 原始明细 ——
    Case("原始明细首页", "POST", "/api/v1/reports/raw",
         body=_query({"date_from": _day(1), "date_to": _day(1), "page_size": 20})),
    Case("原始明细排序与翻页", "POST", "/api/v1/reports/raw",
         body=_query({"date_from": _day(1), "date_to": _day(1), "page": 3, "page_size": 15,
                      "sort": [{"field": "cost", "direction": "asc"}]})),
    Case("原始明细关键词", "POST", "/api/v1/reports/raw",
         body=_query({"date_from": _day(2), "date_to": _day(1), "keyword": "vivo", "page_size": 10})),
    Case("原始明细对外口径无权", "POST", "/api/v1/reports/raw", role="demo.agency",
         body=_query({"date_from": _day(1), "date_to": _day(1)})),
    # —— 只有 Java 实现的系统管理接口：既有实现仍答 501，改由契约校验响应结构 ——
    Case("映射列表", "GET", "/api/v1/mappings/accounts", beyond=True),
    Case("映射列表按媒体筛选", "GET", "/api/v1/mappings/accounts", params={"media": "vivo"}, beyond=True),
    Case("账号列表", "GET", "/api/v1/users", beyond=True),
    Case("审计日志", "GET", "/api/v1/audit-events", beyond=True),
    Case("导入任务查询", "GET", "/api/v1/imports/imp_not_exists", beyond=True),
    # —— 导出 ——
    Case("导出聚合", "POST", "/api/v1/reports/export", params={"view": "aggregate"},
         body=_query({"group_by": ["product"]})),
    Case("导出分天", "POST", "/api/v1/reports/export", params={"view": "daily"},
         body=_query({"date_from": _day(2), "date_to": _day(1), "group_by": ["media"]})),
    Case("导出原始明细", "POST", "/api/v1/reports/export", params={"view": "raw"},
         body=_query({"date_from": _day(1), "date_to": _day(1)})),
    Case("对外口径导出原始明细被拒", "POST", "/api/v1/reports/export", role="demo.agency",
         params={"view": "raw"}, body=_query({"date_from": _day(1), "date_to": _day(1)})),
)


class Client:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")
        self._tokens: dict[str, str] = {}

    def token(self, role: str) -> str:
        if role not in self._tokens:
            status, _, body = self.request("POST", "/api/v1/auth/login",
                                           body={"username": role, "password": DEMO_PASSWORD})
            if status != 200:
                raise SystemExit(f"{self.base} 上账号 {role} 登录失败：{status} {body}")
            self._tokens[role] = body["access_token"]
        return self._tokens[role]

    def request(self, method: str, path: str, *, body: Any = None, params: dict[str, Any] | None = None,
                headers: dict[str, str] | None = None) -> tuple[int, dict[str, str], Any]:
        url = self.base + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(url, data=data, method=method)
        if data is not None:
            request.add_header("Content-Type", "application/json")
        for name, value in (headers or {}).items():
            request.add_header(name, value)
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, _headers(response.headers), _decode(response.headers, response.read())
        except urllib.error.HTTPError as error:
            return error.code, _headers(error.headers), _decode(error.headers, error.read())

    def run(self, case: Case) -> tuple[int, dict[str, str], Any]:
        headers = dict(case.headers)
        if case.role:
            headers.setdefault("Authorization", f"Bearer {self.token(case.role)}")
        return self.request(case.method, case.path, body=case.body, params=case.params, headers=headers)


def _headers(headers: Any) -> dict[str, str]:
    """统一成小写名、去掉参数分隔符后的空格，只比语义不比写法。"""
    return {name.lower(): value.replace("; ", ";") for name, value in headers.items()}


def _decode(headers: Any, raw: bytes) -> Any:
    if not raw:
        return None
    if "json" in headers.get("Content-Type", ""):
        return json.loads(raw)
    return raw.decode("utf-8")


def _instant(value: Any) -> Any:
    """把时间戳统一成 UTC 时刻，避免比的是写法而不是时间。"""
    if not isinstance(value, str):
        return value
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except ValueError:
        return value


def diff(left: Any, right: Any, path: str = "") -> list[tuple[str, Any, Any]]:
    """返回 (字段路径, 左值, 右值) 列表；相等则为空。"""
    name = path.rsplit(".", 1)[-1].split("[")[0]
    if name in VOLATILE:
        same_shape = (left is None) == (right is None) and type(left) is type(right)
        return [] if same_shape else [(path, type(left).__name__, type(right).__name__)]
    if name in INSTANT_FIELDS:
        return [] if _instant(left) == _instant(right) else [(path, left, right)]
    if isinstance(left, dict) and isinstance(right, dict):
        rows: list[tuple[str, Any, Any]] = []
        for key in dict.fromkeys([*left, *right]):
            if key not in left or key not in right:
                rows.append((f"{path}.{key}".lstrip("."), left.get(key, "<缺失>"), right.get(key, "<缺失>")))
            else:
                rows.extend(diff(left[key], right[key], f"{path}.{key}".lstrip(".")))
        return rows
    if isinstance(left, list) and isinstance(right, list):
        if len(left) != len(right):
            return [(f"{path}.length", len(left), len(right))]
        rows = []
        for index, (a, b) in enumerate(zip(left, right, strict=True)):
            rows.extend(diff(a, b, f"{path}[{index}]"))
        return rows
    if isinstance(left, (int, float)) and isinstance(right, (int, float)) and not isinstance(left, bool):
        return [] if float(left) == float(right) else [(path, left, right)]
    return [] if left == right else [(path, left, right)]


def known(case: str, path: str) -> KnownDiff | None:
    root = path.split(".")[0].split("[")[0] or path
    generic = re.sub(r"\[\d+\]", "[]", path)
    for item in KNOWN_DIFFS:
        if item.case == case and item.path in (path, generic, root):
            return item
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="两个后端实现的逐字段对照")
    parser.add_argument("--python", default="http://127.0.0.1:8000", help="既有 FastAPI 服务地址")
    parser.add_argument("--java", default="http://127.0.0.1:8080", help="Java 服务地址")
    parser.add_argument("--only", help="只跑名字包含该子串的用例")
    parser.add_argument("--verbose", action="store_true", help="逐项打印，包括一致的用例")
    args = parser.parse_args()

    baseline, candidate = Client(args.python), Client(args.java)
    contract = Contract()
    cases = [case for case in CASES if not args.only or args.only in case.name]

    failures: list[str] = []
    intentional: list[str] = []
    contract_errors: list[str] = []
    for case in cases:
        left_status, left_headers, left_body = baseline.run(case)
        right_status, right_headers, right_body = candidate.run(case)

        # 契约校验：Java 的每个 JSON 响应都按契约里的响应模型核一遍，超出基准的接口尤其靠它把关
        broken: list[str] = []
        if isinstance(right_body, (dict, list)):
            broken = list(contract.validate(
                right_body, contract.response_schema(case.method, case.path, right_status)))
            contract_errors.extend(f"{case.name} · {message}" for message in broken)

        if case.beyond:
            # 判据是"既有实现还没做、Java 已经做了"，不是"Java 一定 2xx"——
            # 查一个不存在的对象返回 404，同样说明这个接口已经实现。
            if left_status == 501 and right_status != 501 and not broken:
                intentional.append(f"{case.name}：Java 已实现（{right_status}），既有实现仍是 501，按契约校验通过")
            else:
                failures.append(case.name)
                print(f"FAIL {case.name}（超出基准的接口）")
                print(f"       python={left_status} java={right_status}"
                      + (f"，契约校验 {len(broken)} 处不通过" if broken else ""))
            continue

        rows: list[tuple[str, Any, Any]] = []
        if left_status != right_status:
            rows.append(("http_status", left_status, right_status))
        rows.extend(diff(left_body, right_body))
        if case.path.endswith("/export") and left_status == right_status == 200:
            for header in ("content-type", "content-disposition"):
                if left_headers.get(header) != right_headers.get(header):
                    rows.append((f"header.{header}", left_headers.get(header), right_headers.get(header)))

        unexpected = [row for row in rows if known(case.name, row[0]) is None]
        expected = [row for row in rows if known(case.name, row[0]) is not None]
        for path, left, right in expected:
            intentional.append(f"{case.name} · {path}：{left!r} ≠ {right!r}（{known(case.name, path).reason}）")
        if unexpected:
            failures.append(case.name)
            print(f"FAIL {case.name}")
            for path, left, right in unexpected[:12]:
                print(f"       {path or '<根>'}: python={left!r} java={right!r}")
            if len(unexpected) > 12:
                print(f"       …… 另有 {len(unexpected) - 12} 处差异")
        elif args.verbose:
            print(f"PASS {case.name}")

    print(f"\n用例 {len(cases)} 项，失败 {len(failures)} 项，"
          f"契约校验不通过 {len(contract_errors)} 处，有意差异 {len(intentional)} 处")
    for line in intentional:
        print(f"  · {line}")
    if contract_errors:
        print("\n契约校验不通过（Java 的响应与 contracts/openapi.json 对不上）：")
        for line in contract_errors:
            print(f"  · {line}")
    if failures:
        print("\n失败用例：" + "、".join(failures))
    return 1 if failures or contract_errors else 0


if __name__ == "__main__":
    sys.exit(main())
