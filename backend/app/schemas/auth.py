from typing import Literal, Self

from pydantic import Field, model_validator

from app.schemas.common import ApiModel
from app.schemas.reports import MetricKey

ThemeMode = Literal["light", "dark", "system"]
ThemePreset = Literal[
    "aurora-blue", "arc-purple", "quantum-cyan", "pulse-green", "corona-gold", "molten-orange",
    "rose-wave", "glacier-blue", "nebula-purple", "deep-space-gray", "neon-cyan", "custom",
]


class LoginRequest(ApiModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class RefreshRequest(ApiModel):
    refresh_token: str = Field(min_length=1)


class PasswordChange(ApiModel):
    current_password: str = Field(min_length=1, max_length=128)
    new_password: str = Field(min_length=10, max_length=128, description="至少 10 位，不能与当前口令相同")


class TokenPair(ApiModel):
    access_token: str
    refresh_token: str
    token_type: Literal["bearer"] = "bearer"
    expires_in: int = Field(description="访问令牌有效秒数")
    must_change_password: bool


class DataScope(ApiModel):
    agencies: list[str] | None = Field(default=None, description="可见代理；null 表示不限")
    products: list[str] | None = Field(default=None, description="可见产品；null 表示不限")
    operators: list[str] | None = Field(default=None, description="可见运营；null 表示不限")


class NavPage(ApiModel):
    id: str
    title: str
    route: str


class NavGroup(ApiModel):
    title: str
    pages: list[NavPage]


class Principal(ApiModel):
    user_id: str
    username: str
    display_name: str
    tenant_id: str
    roles: list[str]
    permissions: list[str]
    data_scope: DataScope
    must_change_password: bool
    visible_metrics: list[MetricKey] = Field(description="当前账号可查看的指标；前端据此裁剪列选择，接口仍独立校验")
    navigation: list[NavGroup] = Field(description="按权限裁剪后的导航；仅用于展示，接口仍独立鉴权")


class PreferencesUpdate(ApiModel):
    theme_mode: ThemeMode
    theme_preset: ThemePreset
    custom_primary: str | None = Field(
        default=None, pattern=r"^#[0-9A-Fa-f]{6}$", description="theme_preset 为 custom 时的主色"
    )
    table_columns: dict[str, list[str]] = Field(
        default_factory=dict, description="按页面保存的列顺序与显隐，键为页面 id"
    )
    revision: int = Field(ge=0, description="客户端最后读到的版本号，用于并发冲突检测")

    @model_validator(mode="after")
    def check_custom_color(self) -> Self:
        if self.theme_preset == "custom" and self.custom_primary is None:
            raise ValueError("theme_preset 为 custom 时必须提供 custom_primary")
        return self


class Preferences(PreferencesUpdate):
    """账号级偏好；revision 为服务端当前版本号。"""
