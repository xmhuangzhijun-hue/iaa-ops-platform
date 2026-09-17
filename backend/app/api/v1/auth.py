from typing import Annotated

from fastapi import APIRouter, Body, Response

from app.api import examples as ex
from app.api.contract import contract, named_example, ok, problems
from app.api.deps import AnyUser, CurrentUserDep, SessionDep
from app.schemas.auth import (
    LoginRequest, PasswordChange, Preferences, PreferencesUpdate, Principal, RefreshRequest, TokenPair,
)
from app.services import auth as auth_service
from app.services import preferences as preference_service

router = APIRouter(tags=["认证与偏好"])


@router.post(
    "/auth/login", operation_id="login", summary="账号密码登录",
    description="校验账号口令，签发短期访问令牌与刷新令牌。账号不存在、口令错误、账号停用及锁定统一返回 INVALID_CREDENTIALS。Java 业务服务按账号及来源 IP 限制失败登录，锁定期内正确口令也拒绝；成功登录或改密提交后清零账号与当前 IP 的失败计数。",
    response_model=TokenPair, response_description="登录成功",
    responses={**ok(ex.TOKEN_PAIR, "登录成功"), **problems(401)},
    openapi_extra=contract(reqs=["IAA-REQ-001"], screens=["login"], fields=["username"], status="implemented", public=True),
)
def login(
    body: Annotated[LoginRequest, Body(openapi_examples=named_example(ex.LOGIN_REQUEST, "演示账号登录"))],
    session: SessionDep,
) -> TokenPair:
    return auth_service.login(session, body.username, body.password)


@router.post(
    "/auth/refresh", operation_id="refreshToken", summary="刷新访问令牌",
    description="用刷新令牌换取新的令牌对，旧刷新令牌随即失效；已失效的刷新令牌再次出现时吊销该账号全部刷新令牌。",
    response_model=TokenPair, response_description="已换发",
    responses={**ok(ex.TOKEN_PAIR, "换发成功"), **problems(401)},
    openapi_extra=contract(reqs=["IAA-REQ-001"], status="implemented", public=True),
)
def refresh_token(
    body: Annotated[RefreshRequest, Body(openapi_examples=named_example(ex.REFRESH_REQUEST, "换发令牌"))],
    session: SessionDep,
) -> TokenPair:
    return auth_service.refresh(session, body.refresh_token)


@router.post(
    "/auth/logout", operation_id="logout", summary="退出登录",
    description="吊销提交的刷新令牌。访问令牌无状态，最长 15 分钟后自然失效，前端退出时应同时丢弃。",
    status_code=204, response_class=Response, response_description="已退出",
    openapi_extra=contract(reqs=["IAA-REQ-001"], screens=["app-shell"], status="implemented", public=True),
)
def logout(
    body: Annotated[RefreshRequest, Body(openapi_examples=named_example(ex.REFRESH_REQUEST, "退出当前会话"))],
    session: SessionDep,
) -> Response:
    auth_service.logout(session, body.refresh_token)
    return Response(status_code=204)


@router.post(
    "/auth/password", operation_id="changePassword", summary="修改口令",
    description="临时口令账号也可调用。成功后吊销该账号全部刷新令牌并签发新的令牌对。",
    response_model=TokenPair, response_description="已修改",
    responses={**ok(ex.TOKEN_PAIR, "修改成功"), **problems(401)},
    openapi_extra=contract(reqs=["IAA-REQ-001"], screens=["login"], status="implemented"),
)
def change_password(
    body: Annotated[PasswordChange, Body(openapi_examples=named_example(ex.PASSWORD_CHANGE, "首次登录改密"))],
    session: SessionDep,
    user: AnyUser,
) -> TokenPair:
    return auth_service.change_password(session, user, body.current_password, body.new_password)


@router.get(
    "/auth/me", operation_id="getCurrentPrincipal", summary="当前账号、权限与导航",
    description="返回角色、权限、数据范围以及按权限裁剪后的导航分组。临时口令账号也可调用。",
    response_model=Principal, response_description="当前账号",
    responses={**ok(ex.PRINCIPAL, "运营账号"), **problems(401)},
    openapi_extra=contract(reqs=["IAA-REQ-001"], screens=["app-shell"],
                           fields=["username", "roles", "permissions", "data_scope"], status="implemented"),
)
def get_current_principal(user: AnyUser) -> Principal:
    return user.principal()


@router.get(
    "/me/preferences", operation_id="getPreferences", summary="读取账号界面偏好",
    description="主题模式、主题预设与各页面列设置按账号保存，换设备后保持一致。从未保存时返回默认值且 revision 为 0。",
    response_model=Preferences, response_description="当前偏好",
    responses={**ok(ex.PREFERENCES, "深色极光蓝"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-004"], screens=["app-shell"],
                           fields=["theme_mode", "theme_preset", "table_columns", "revision"], status="implemented"),
)
def get_preferences(session: SessionDep, user: CurrentUserDep) -> Preferences:
    return preference_service.get_preferences(session, user)


@router.put(
    "/me/preferences", operation_id="updatePreferences", summary="保存账号界面偏好",
    description="带上最后读到的 revision；期间被其他设备修改则返回 409。",
    response_model=Preferences, response_description="已保存",
    responses={**ok(ex.PREFERENCES, "保存后版本 +1"), **problems(401, 403, 409)},
    openapi_extra=contract(reqs=["IAA-REQ-004"], screens=["app-shell"],
                           fields=["theme_mode", "theme_preset", "table_columns", "revision"], status="implemented"),
)
def update_preferences(
    body: Annotated[PreferencesUpdate, Body(openapi_examples=named_example(ex.PREFERENCES_UPDATE, "保存列设置"))],
    session: SessionDep,
    user: CurrentUserDep,
) -> Preferences:
    return preference_service.update_preferences(session, user, body)
