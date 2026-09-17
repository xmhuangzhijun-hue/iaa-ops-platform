import os

# 必须在导入应用之前设置：数据库连接与签名密钥在导入时读取。
os.environ["APP_ENV"] = "test"
TEST_DATABASE_URL = os.environ.setdefault(
    "IAA_TEST_DATABASE_URL", "postgresql+psycopg://iaa@127.0.0.1:55433/iaa_ops_test"
)
os.environ["DATABASE_URL"] = TEST_DATABASE_URL

from collections.abc import Callable, Iterator  # noqa: E402
from pathlib import Path  # noqa: E402

import pytest  # noqa: E402
from alembic import command  # noqa: E402
from alembic.config import Config  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine, text  # noqa: E402
from sqlalchemy.engine import make_url  # noqa: E402
from sqlalchemy.exc import OperationalError  # noqa: E402

from app.main import app  # noqa: E402
from tests.support import SEED_DAYS, SEED_END, TEST_PASSWORD  # noqa: E402

BACKEND = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="session")
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture(scope="session")
def database() -> Iterator[None]:
    """建一个一次性测试库：迁移到最新并写入 14 天虚构数据，测试结束删除。"""
    url = make_url(TEST_DATABASE_URL)
    admin = create_engine(url.set(database="postgres"), isolation_level="AUTOCOMMIT")
    try:
        with admin.connect() as connection:
            connection.execute(text(f'DROP DATABASE IF EXISTS "{url.database}" WITH (FORCE)'))
            connection.execute(text(f'CREATE DATABASE "{url.database}"'))
    except OperationalError as error:
        pytest.fail(f"需要 PostgreSQL（在仓库根目录 docker compose up -d postgres）：{error.orig}", pytrace=False)

    config = Config(str(BACKEND / "alembic.ini"))
    config.set_main_option("sqlalchemy.url", TEST_DATABASE_URL)
    command.upgrade(config, "head")

    from app.db.session import SessionLocal, engine
    from app.demo.seed import seed

    with SessionLocal() as session:
        seed(session, end=SEED_END, days=SEED_DAYS, password=TEST_PASSWORD)
    yield
    engine.dispose()
    with admin.connect() as connection:
        connection.execute(text(f'DROP DATABASE IF EXISTS "{url.database}" WITH (FORCE)'))
    admin.dispose()


@pytest.fixture(scope="session")
def auth_headers(client: TestClient, database: None) -> Callable[[str], dict[str, str]]:
    cache: dict[str, dict[str, str]] = {}

    def headers(username: str) -> dict[str, str]:
        if username not in cache:
            response = client.post("/api/v1/auth/login", json={"username": username, "password": TEST_PASSWORD})
            assert response.status_code == 200, response.text
            cache[username] = {"Authorization": f"Bearer {response.json()['access_token']}"}
        return cache[username]

    return headers
