from collections.abc import Callable
from typing import Annotated

import jwt
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.core.security import decode_access_token
from app.db.session import get_session
from app.domain.access import has_permission
from app.services.auth import CurrentUser, load_current_user

SessionDep = Annotated[Session, Depends(get_session)]
_bearer = HTTPBearer(auto_error=False, scheme_name="bearerAuth", bearerFormat="JWT")


def get_user_allowing_password_change(
    session: SessionDep,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
) -> CurrentUser:
    if credentials is None:
        raise ApiError(401, "AUTH_REQUIRED", "需要登录")
    try:
        claims = decode_access_token(credentials.credentials)
    except jwt.PyJWTError:
        raise ApiError(401, "AUTH_REQUIRED", "令牌无效或已过期") from None
    user = load_current_user(session, str(claims["sub"]))
    if user is None:
        raise ApiError(401, "AUTH_REQUIRED", "账号不存在或已停用")
    return user


def get_current_user(user: Annotated[CurrentUser, Depends(get_user_allowing_password_change)]) -> CurrentUser:
    if user.must_change_password:
        raise ApiError(403, "PASSWORD_CHANGE_REQUIRED", "请先修改临时口令")
    return user


AnyUser = Annotated[CurrentUser, Depends(get_user_allowing_password_change)]
CurrentUserDep = Annotated[CurrentUser, Depends(get_current_user)]


def require(permission: str) -> Callable[[CurrentUser], CurrentUser]:
    def dependency(user: CurrentUserDep) -> CurrentUser:
        if not has_permission(user.permissions, permission):
            raise ApiError(403, "FORBIDDEN", "没有该操作的权限")
        return user

    return dependency
