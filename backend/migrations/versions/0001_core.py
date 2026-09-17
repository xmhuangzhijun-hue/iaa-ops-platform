"""核心表：租户、账号、角色、刷新令牌、偏好、账户映射、投放事实

Revision ID: 0001_core
Revises:
Create Date: 2026-09-16
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0001_core"
down_revision = None
branch_labels = None
depends_on = None

ROLE_CHECK = "role in ('super_admin', 'company_admin', 'operator', 'agency_admin', 'customer', 'readonly')"


def _now() -> sa.TextClause:
    return sa.text("now()")


def upgrade() -> None:
    op.create_table(
        "tenants",
        sa.Column("id", sa.String(32), nullable=False),
        sa.Column("name", sa.String(64), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.PrimaryKeyConstraint("id", name="pk_tenants"),
    )
    op.create_table(
        "users",
        sa.Column("id", sa.String(32), nullable=False),
        sa.Column("tenant_id", sa.String(32), nullable=False),
        sa.Column("username", sa.String(32), nullable=False),
        sa.Column("display_name", sa.String(32), nullable=False),
        sa.Column("password_hash", sa.Text(), nullable=False),
        sa.Column("status", sa.String(16), server_default="active", nullable=False),
        sa.Column("must_change_password", sa.Boolean(), server_default=sa.text("false"), nullable=False),
        sa.Column("data_scope", postgresql.JSONB(), server_default=sa.text("'{}'::jsonb"), nullable=False),
        sa.Column("revision", sa.Integer(), server_default=sa.text("1"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.PrimaryKeyConstraint("id", name="pk_users"),
        sa.ForeignKeyConstraint(["tenant_id"], ["tenants.id"], name="fk_users_tenant_id_tenants"),
        sa.UniqueConstraint("username", name="uq_users_username"),
        sa.CheckConstraint("status in ('active', 'disabled')", name="ck_users_status"),
    )
    op.create_table(
        "user_roles",
        sa.Column("user_id", sa.String(32), nullable=False),
        sa.Column("role", sa.String(32), nullable=False),
        sa.PrimaryKeyConstraint("user_id", "role", name="pk_user_roles"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name="fk_user_roles_user_id_users", ondelete="CASCADE"),
        sa.CheckConstraint(ROLE_CHECK, name="ck_user_roles_role"),
    )
    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.BigInteger(), sa.Identity(), nullable=False),
        sa.Column("user_id", sa.String(32), nullable=False),
        sa.Column("token_hash", sa.String(64), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.PrimaryKeyConstraint("id", name="pk_refresh_tokens"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name="fk_refresh_tokens_user_id_users", ondelete="CASCADE"),
        sa.UniqueConstraint("token_hash", name="uq_refresh_tokens_token_hash"),
    )
    op.create_index("ix_refresh_tokens_user_id", "refresh_tokens", ["user_id"])
    op.create_table(
        "user_preferences",
        sa.Column("user_id", sa.String(32), nullable=False),
        sa.Column("theme_mode", sa.String(16), nullable=False),
        sa.Column("theme_preset", sa.String(32), nullable=False),
        sa.Column("custom_primary", sa.String(7), nullable=True),
        sa.Column("table_columns", postgresql.JSONB(), server_default=sa.text("'{}'::jsonb"), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.PrimaryKeyConstraint("user_id", name="pk_user_preferences"),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"], name="fk_user_preferences_user_id_users", ondelete="CASCADE"
        ),
    )
    op.create_table(
        "account_mappings",
        sa.Column("tenant_id", sa.String(32), nullable=False),
        sa.Column("media", sa.String(32), nullable=False),
        sa.Column("account", sa.String(64), nullable=False),
        sa.Column("agency", sa.String(64), nullable=True),
        sa.Column("product", sa.String(64), nullable=True),
        sa.Column("operator", sa.String(64), nullable=True),
        sa.Column("revision", sa.Integer(), server_default=sa.text("1"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.PrimaryKeyConstraint("tenant_id", "media", "account", name="pk_account_mappings"),
        sa.ForeignKeyConstraint(["tenant_id"], ["tenants.id"], name="fk_account_mappings_tenant_id_tenants"),
    )
    op.create_table(
        "ad_facts",
        sa.Column("id", sa.BigInteger(), sa.Identity(), nullable=False),
        sa.Column("tenant_id", sa.String(32), nullable=False),
        sa.Column("stat_date", sa.Date(), nullable=False),
        sa.Column("stat_hour", sa.SmallInteger(), nullable=True),
        sa.Column("media", sa.String(32), nullable=False),
        sa.Column("account", sa.String(64), nullable=False),
        sa.Column("campaign", sa.String(64), nullable=True),
        sa.Column("cost", sa.Numeric(14, 2), server_default=sa.text("0"), nullable=False),
        sa.Column("revenue", sa.Numeric(14, 2), server_default=sa.text("0"), nullable=False),
        sa.Column("impressions", sa.BigInteger(), server_default=sa.text("0"), nullable=False),
        sa.Column("clicks", sa.BigInteger(), server_default=sa.text("0"), nullable=False),
        sa.Column("launches", sa.BigInteger(), server_default=sa.text("0"), nullable=False),
        sa.Column("callbacks", sa.BigInteger(), server_default=sa.text("0"), nullable=False),
        sa.Column("conversions", sa.BigInteger(), server_default=sa.text("0"), nullable=False),
        sa.Column("loaded_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.PrimaryKeyConstraint("id", name="pk_ad_facts"),
        sa.ForeignKeyConstraint(["tenant_id"], ["tenants.id"], name="fk_ad_facts_tenant_id_tenants"),
        sa.CheckConstraint("stat_hour is null or stat_hour between 0 and 23", name="ck_ad_facts_stat_hour"),
    )
    op.create_index("ix_ad_facts_tenant_id_stat_date", "ad_facts", ["tenant_id", "stat_date"])
    op.create_index("ix_ad_facts_tenant_id_media_account", "ad_facts", ["tenant_id", "media", "account", "stat_date"])


def downgrade() -> None:
    op.drop_table("ad_facts")
    op.drop_table("account_mappings")
    op.drop_table("user_preferences")
    op.drop_table("refresh_tokens")
    op.drop_table("user_roles")
    op.drop_table("users")
    op.drop_table("tenants")
