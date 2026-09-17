from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import ApiError
from app.core.security import create_access_token, hash_password, hash_token, new_refresh_token, verify_password
from app.db import models as m
from app.domain.access import navigation_for, permissions_for, visible_metric_keys
from app.schemas.auth import Principal, TokenPair

SCOPE_KEYS = ("agencies", "products", "operators")


@dataclass(frozen=True)
class CurrentUser:
    id: str
    tenant_id: str
    username: str
    display_name: str
    roles: tuple[str, ...]
    permissions: frozenset[str]
    data_scope: dict[str, list[str] | None]
    must_change_password: bool

    def principal(self) -> Principal:
        return Principal.model_validate({
            "user_id": self.id, "username": self.username, "display_name": self.display_name,
            "tenant_id": self.tenant_id, "roles": list(self.roles), "permissions": sorted(self.permissions),
            "data_scope": self.data_scope, "must_change_password": self.must_change_password,
            "visible_metrics": list(visible_metric_keys(self.permissions)),
            "navigation": navigation_for(self.permissions),
        })


def normalize_scope(raw: dict[str, Any] | None) -> dict[str, list[str] | None]:
    raw = raw or {}
    return {key: None if raw.get(key) is None else list(raw[key]) for key in SCOPE_KEYS}


def load_current_user(session: Session, user_id: str) -> CurrentUser | None:
    user = session.get(m.User, user_id)
    if user is None or user.status != "active":
        return None
    roles = tuple(sorted(session.scalars(select(m.UserRole.role).where(m.UserRole.user_id == user.id))))
    return CurrentUser(
        id=user.id, tenant_id=user.tenant_id, username=user.username, display_name=user.display_name,
        roles=roles, permissions=permissions_for(roles), data_scope=normalize_scope(user.data_scope),
        must_change_password=user.must_change_password,
    )


def issue_tokens(session: Session, user: m.User, now: datetime | None = None) -> TokenPair:
    now = now or datetime.now(UTC)
    access_token, expires_in = create_access_token(user.id, user.tenant_id, now)
    raw, digest = new_refresh_token()
    session.add(m.RefreshToken(
        user_id=user.id, token_hash=digest, expires_at=now + timedelta(days=settings.refresh_token_days)
    ))
    session.commit()
    return TokenPair(
        access_token=access_token, refresh_token=raw, expires_in=expires_in,
        must_change_password=user.must_change_password,
    )


def login(session: Session, username: str, password: str) -> TokenPair:
    user = session.scalar(select(m.User).where(m.User.username == username))
    matched = verify_password(user.password_hash if user else None, password)
    if user is None or not matched or user.status != "active":
        raise ApiError(401, "INVALID_CREDENTIALS", "账号或口令错误")
    return issue_tokens(session, user)


def _revoke_all(session: Session, user_id: str, now: datetime) -> None:
    session.execute(
        update(m.RefreshToken)
        .where(m.RefreshToken.user_id == user_id, m.RefreshToken.revoked_at.is_(None))
        .values(revoked_at=now)
    )


def refresh(session: Session, raw: str) -> TokenPair:
    now = datetime.now(UTC)
    token = session.scalar(
        select(m.RefreshToken).where(m.RefreshToken.token_hash == hash_token(raw)).with_for_update()
    )
    if token is None:
        raise ApiError(401, "AUTH_REQUIRED", "刷新令牌无效")
    if token.revoked_at is not None:
        # 已轮换的令牌被再次使用，按泄露处理：吊销该账号全部刷新令牌。
        _revoke_all(session, token.user_id, now)
        session.commit()
        raise ApiError(401, "AUTH_REQUIRED", "刷新令牌已失效，请重新登录")
    if token.expires_at <= now:
        raise ApiError(401, "AUTH_REQUIRED", "刷新令牌已过期，请重新登录")
    user = session.get(m.User, token.user_id)
    if user is None or user.status != "active":
        raise ApiError(401, "AUTH_REQUIRED", "账号不存在或已停用")
    token.revoked_at = now
    return issue_tokens(session, user, now)


def logout(session: Session, raw: str) -> None:
    """吊销这一个刷新令牌；未知或已吊销的令牌静默忽略，不触发重放检测。"""
    token = session.scalar(select(m.RefreshToken).where(m.RefreshToken.token_hash == hash_token(raw)))
    if token is not None and token.revoked_at is None:
        token.revoked_at = datetime.now(UTC)
        session.commit()


def change_password(session: Session, current: CurrentUser, current_password: str, new_password: str) -> TokenPair:
    user = session.get(m.User, current.id, with_for_update=True)
    if user is None or not verify_password(user.password_hash, current_password):
        raise ApiError(401, "INVALID_CREDENTIALS", "当前口令错误")
    if current_password == new_password:
        raise ApiError(422, "VALIDATION_FAILED", "新口令不能与当前口令相同")
    now = datetime.now(UTC)
    user.password_hash = hash_password(new_password)
    user.must_change_password = False
    user.revision += 1
    _revoke_all(session, user.id, now)
    return issue_tokens(session, user, now)
