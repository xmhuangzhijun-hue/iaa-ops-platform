from datetime import date, datetime
from decimal import Decimal
from typing import Any

from sqlalchemy import (
    BigInteger, Boolean, CheckConstraint, Date, DateTime, ForeignKey, Identity, Index, Integer, MetaData,
    Numeric, SmallInteger, String, Text, UniqueConstraint, func, text,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from app.domain.access import ROLES

NAMING_CONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMING_CONVENTION)


def _timestamp(*, on_update: bool = False) -> Mapped[datetime]:
    return mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now() if on_update else None
    )


class Tenant(Base):
    __tablename__ = "tenants"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    name: Mapped[str] = mapped_column(String(64))
    created_at: Mapped[datetime] = _timestamp()


class User(Base):
    __tablename__ = "users"
    __table_args__ = (
        UniqueConstraint("username"),
        CheckConstraint("status in ('active', 'disabled')", name="status"),
    )

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    tenant_id: Mapped[str] = mapped_column(String(32), ForeignKey("tenants.id"))
    username: Mapped[str] = mapped_column(String(32))
    display_name: Mapped[str] = mapped_column(String(32))
    password_hash: Mapped[str] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(16), server_default="active")
    must_change_password: Mapped[bool] = mapped_column(Boolean, server_default=text("false"))
    data_scope: Mapped[dict[str, Any]] = mapped_column(JSONB, server_default=text("'{}'::jsonb"))
    revision: Mapped[int] = mapped_column(Integer, server_default=text("1"))
    created_at: Mapped[datetime] = _timestamp()
    updated_at: Mapped[datetime] = _timestamp(on_update=True)


class UserRole(Base):
    __tablename__ = "user_roles"
    __table_args__ = (CheckConstraint(f"role in ({', '.join(repr(role) for role in ROLES)})", name="role"),)

    user_id: Mapped[str] = mapped_column(String(32), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    role: Mapped[str] = mapped_column(String(32), primary_key=True)


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id: Mapped[int] = mapped_column(BigInteger, Identity(), primary_key=True)
    user_id: Mapped[str] = mapped_column(String(32), ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = _timestamp()


class UserPreference(Base):
    __tablename__ = "user_preferences"

    user_id: Mapped[str] = mapped_column(String(32), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    theme_mode: Mapped[str] = mapped_column(String(16))
    theme_preset: Mapped[str] = mapped_column(String(32))
    custom_primary: Mapped[str | None] = mapped_column(String(7))
    table_columns: Mapped[dict[str, Any]] = mapped_column(JSONB, server_default=text("'{}'::jsonb"))
    revision: Mapped[int] = mapped_column(Integer)
    updated_at: Mapped[datetime] = _timestamp(on_update=True)


class AccountMapping(Base):
    __tablename__ = "account_mappings"

    tenant_id: Mapped[str] = mapped_column(String(32), ForeignKey("tenants.id"), primary_key=True)
    media: Mapped[str] = mapped_column(String(32), primary_key=True)
    account: Mapped[str] = mapped_column(String(64), primary_key=True)
    agency: Mapped[str | None] = mapped_column(String(64))
    product: Mapped[str | None] = mapped_column(String(64))
    operator: Mapped[str | None] = mapped_column(String(64))
    revision: Mapped[int] = mapped_column(Integer, server_default=text("1"))
    updated_at: Mapped[datetime] = _timestamp(on_update=True)


class AdFact(Base):
    """投放明细事实。产品、代理、运营不落在事实表，查询时经账户映射补齐，映射修改对历史生效。"""

    __tablename__ = "ad_facts"
    __table_args__ = (
        CheckConstraint("stat_hour is null or stat_hour between 0 and 23", name="stat_hour"),
        Index("ix_ad_facts_tenant_id_stat_date", "tenant_id", "stat_date"),
        Index("ix_ad_facts_tenant_id_media_account", "tenant_id", "media", "account", "stat_date"),
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(), primary_key=True)
    tenant_id: Mapped[str] = mapped_column(String(32), ForeignKey("tenants.id"))
    stat_date: Mapped[date] = mapped_column(Date)
    stat_hour: Mapped[int | None] = mapped_column(SmallInteger)
    media: Mapped[str] = mapped_column(String(32))
    account: Mapped[str] = mapped_column(String(64))
    campaign: Mapped[str | None] = mapped_column(String(64))
    cost: Mapped[Decimal] = mapped_column(Numeric(14, 2), server_default=text("0"))
    revenue: Mapped[Decimal] = mapped_column(Numeric(14, 2), server_default=text("0"))
    impressions: Mapped[int] = mapped_column(BigInteger, server_default=text("0"))
    clicks: Mapped[int] = mapped_column(BigInteger, server_default=text("0"))
    launches: Mapped[int] = mapped_column(BigInteger, server_default=text("0"))
    callbacks: Mapped[int] = mapped_column(BigInteger, server_default=text("0"))
    conversions: Mapped[int] = mapped_column(BigInteger, server_default=text("0"))
    loaded_at: Mapped[datetime] = _timestamp()
