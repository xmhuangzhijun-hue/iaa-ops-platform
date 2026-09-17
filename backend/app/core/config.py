import os
import secrets
from dataclasses import dataclass

VERSION = "0.2.0"
LOCAL_ENVIRONMENTS = {"development", "test"}
LOCAL_DATABASE_URL = "postgresql+psycopg://iaa@127.0.0.1:55433/iaa_ops"


@dataclass(frozen=True)
class Settings:
    environment: str
    database_url: str
    jwt_signing_key: str
    access_token_minutes: int = 15
    refresh_token_days: int = 7
    version: str = VERSION


def load_settings() -> Settings:
    environment = os.getenv("APP_ENV", "development")
    local = environment in LOCAL_ENVIRONMENTS

    signing_key = os.getenv("JWT_SIGNING_KEY", "")
    if not signing_key:
        if not local:
            raise RuntimeError("JWT_SIGNING_KEY 未配置：非本机环境必须由密钥管理注入")
        # 本机开发每次启动随机生成，重启后需重新登录；从不写入文件。
        signing_key = secrets.token_urlsafe(48)

    database_url = os.getenv("DATABASE_URL", LOCAL_DATABASE_URL if local else "")
    if not database_url:
        raise RuntimeError("DATABASE_URL 未配置")

    return Settings(environment=environment, database_url=database_url, jwt_signing_key=signing_key)


settings = load_settings()
