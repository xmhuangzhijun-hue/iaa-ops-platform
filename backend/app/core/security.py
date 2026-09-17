import hashlib
import secrets
from datetime import UTC, datetime, timedelta
from typing import Any

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

from app.core.config import settings

ALGORITHM = "HS256"
_hasher = PasswordHasher()
_TIMING_HASH = _hasher.hash("equalize-login-timing")


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password_hash: str | None, password: str) -> bool:
    """账号不存在时也校验一次，避免响应时间暴露账号是否存在。"""
    try:
        matched = _hasher.verify(password_hash or _TIMING_HASH, password)
    except (VerificationError, InvalidHashError):
        return False
    return matched and password_hash is not None


def create_access_token(user_id: str, tenant_id: str, now: datetime | None = None) -> tuple[str, int]:
    issued_at = now or datetime.now(UTC)
    lifetime = timedelta(minutes=settings.access_token_minutes)
    claims = {"sub": user_id, "tid": tenant_id, "typ": "access", "iat": issued_at, "exp": issued_at + lifetime}
    return jwt.encode(claims, settings.jwt_signing_key, algorithm=ALGORITHM), int(lifetime.total_seconds())


def decode_access_token(token: str) -> dict[str, Any]:
    claims = jwt.decode(
        token, settings.jwt_signing_key, algorithms=[ALGORITHM], options={"require": ["sub", "exp", "iat"]}
    )
    if claims.get("typ") != "access":
        raise jwt.InvalidTokenError("not an access token")
    return claims


def hash_token(raw: str) -> str:
    return hashlib.sha256(raw.encode()).hexdigest()


def new_refresh_token() -> tuple[str, str]:
    """返回 (明文, 摘要)；库里只存摘要。"""
    raw = secrets.token_urlsafe(32)
    return raw, hash_token(raw)
