from typing import Annotated

from fastapi import APIRouter, Body, File, Form, Path, Query, UploadFile

from app.api import examples as ex
from app.api.contract import contract, named_example, ok, problems
from app.core.errors import not_implemented
from app.schemas.admin import (
    AccountMappingPage, AccountMappingUpsert, AuditEventPage, ImportTask, UpsertResult, UserCreate,
    UserCreated, UserPage, UserRolesUpdate, UserStatus, UserSummary,
)

router = APIRouter()

MAPPING_FIELDS = ["account", "media", "agency", "product", "operator", "revision"]
USER_FIELDS = ["username", "roles", "data_scope", "revision"]
Page = Annotated[int, Query(ge=1)]
PageSize = Annotated[int, Query(ge=1, le=200)]


@router.get(
    "/mappings/accounts", tags=["映射管理"], operation_id="listAccountMappings", summary="账户归属映射列表",
    response_model=AccountMappingPage, response_description="映射分页",
    responses={**ok(ex.MAPPING_PAGE, "两条映射"), **problems(401, 403, 501)},
    openapi_extra=contract(reqs=["IAA-REQ-003"], screens=["mapping-manage"], fields=MAPPING_FIELDS),
)
def list_account_mappings(
    media: Annotated[str | None, Query(max_length=32)] = None,
    keyword: Annotated[str | None, Query(max_length=100)] = None,
    page: Page = 1,
    page_size: PageSize = 50,
) -> AccountMappingPage:
    not_implemented(3)


@router.put(
    "/mappings/accounts", tags=["映射管理"], operation_id="upsertAccountMappings", summary="批量新增或修改映射",
    description="逐条比对 revision；任一条版本已变化则整批不写入并返回 409。写入记审计日志。",
    response_model=UpsertResult, response_description="写入结果",
    responses={**ok(ex.UPSERT_RESULT, "新增 1 条，修改 1 条"), **problems(401, 403, 409, 501)},
    openapi_extra=contract(reqs=["IAA-REQ-003"], screens=["mapping-manage"], fields=MAPPING_FIELDS),
)
def upsert_account_mappings(
    body: Annotated[AccountMappingUpsert, Body(openapi_examples=named_example(ex.MAPPING_UPSERT, "改运营并新增账户"))],
) -> UpsertResult:
    not_implemented(3)


@router.post(
    "/imports", tags=["数据导入"], operation_id="createImport", summary="上传媒体导出表",
    description="解析后按文件覆盖的日期整天替换入库，重复上传同一天不会累加。异步处理，返回任务。",
    status_code=202, response_model=ImportTask, response_description="已受理",
    responses={**ok(ex.IMPORT_TASK, "导入完成的任务", status=202), **problems(401, 403, 413, 501)},
    openapi_extra={
        **contract(reqs=["IAA-REQ-003"], screens=["data-import"], fields=["media", "stat_date", "import_status"]),
        "requestBody": {"content": {"multipart/form-data": {"examples": named_example(ex.IMPORT_FORM, "上传 vivo 明细")}}},
    },
)
def create_import(
    file: Annotated[UploadFile, File(description="媒体后台导出的 .xlsx 文件")],
    media: Annotated[str, Form(max_length=32)],
) -> ImportTask:
    not_implemented(3)


@router.get(
    "/imports/{import_id}", tags=["数据导入"], operation_id="getImport", summary="查询导入任务",
    response_model=ImportTask, response_description="任务状态",
    responses={**ok(ex.IMPORT_TASK, "已完成"), **problems(401, 403, 404, 501)},
    openapi_extra=contract(reqs=["IAA-REQ-003"], screens=["data-import"], fields=["import_status", "stat_date"]),
)
def get_import(import_id: Annotated[str, Path(max_length=64)]) -> ImportTask:
    not_implemented(3)


@router.get(
    "/users", tags=["用户与权限"], operation_id="listUsers", summary="账号列表",
    description="公司管理员看到本租户账号；代理管理员只看到本代理下的账号。",
    response_model=UserPage, response_description="账号分页",
    responses={**ok(ex.USER_PAGE, "一个运营账号"), **problems(401, 403, 501)},
    openapi_extra=contract(reqs=["IAA-REQ-001", "IAA-REQ-003"], screens=["user-manage"], fields=USER_FIELDS),
)
def list_users(
    keyword: Annotated[str | None, Query(max_length=64)] = None,
    status: Annotated[UserStatus | None, Query()] = None,
    page: Page = 1,
    page_size: PageSize = 50,
) -> UserPage:
    not_implemented(3)


@router.post(
    "/users", tags=["用户与权限"], operation_id="createUser", summary="创建账号",
    description="服务端生成一次性临时口令，只在本响应返回；不能授予超出操作者自身范围的角色或数据。",
    status_code=201, response_model=UserCreated, response_description="已创建",
    responses={**ok(ex.USER_CREATED, "创建代理管理员", status=201), **problems(401, 403, 409, 501)},
    openapi_extra=contract(reqs=["IAA-REQ-001", "IAA-REQ-003"], screens=["user-manage"], fields=USER_FIELDS),
)
def create_user(body: Annotated[UserCreate, Body(openapi_examples=named_example(ex.USER_CREATE, "代理管理员"))]) -> UserCreated:
    not_implemented(3)


@router.put(
    "/users/{user_id}/roles", tags=["用户与权限"], operation_id="updateUserRoles", summary="修改角色与数据范围",
    description="带 revision 防止覆盖他人修改；真实口径与对外口径权限不能同时授予。",
    response_model=UserSummary, response_description="已修改",
    responses={**ok(ex.USER_SUMMARY, "改为运营"), **problems(401, 403, 404, 409, 501)},
    openapi_extra=contract(reqs=["IAA-REQ-001", "IAA-REQ-003"], screens=["user-manage"], fields=USER_FIELDS),
)
def update_user_roles(
    user_id: Annotated[str, Path(max_length=64)],
    body: Annotated[UserRolesUpdate, Body(openapi_examples=named_example(ex.ROLES_UPDATE, "限定产品范围"))],
) -> UserSummary:
    not_implemented(3)


@router.get(
    "/audit-events", tags=["审计"], operation_id="listAuditEvents", summary="审计日志",
    description="按时间倒序游标分页，记录映射、导入、账号与权限等写操作的前后值。",
    response_model=AuditEventPage, response_description="审计分页",
    responses={**ok(ex.AUDIT_PAGE, "映射修改记录"), **problems(401, 403, 501)},
    openapi_extra=contract(reqs=["IAA-REQ-003"], screens=["audit-log"], fields=["audit_action"]),
)
def list_audit_events(
    action: Annotated[str | None, Query(max_length=64)] = None,
    cursor: Annotated[str | None, Query(max_length=128)] = None,
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
) -> AuditEventPage:
    not_implemented(3)
