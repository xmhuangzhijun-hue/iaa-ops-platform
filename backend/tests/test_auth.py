from collections.abc import Iterator

import pytest
from sqlalchemy import delete

from app.core.security import hash_password
from app.db import models as m
from app.db.session import SessionLocal
from app.demo.seed import TENANT_ID
from tests.support import TEST_PASSWORD

LOGIN = "/api/v1/auth/login"
REFRESH = "/api/v1/auth/refresh"


def login(client, username, password=TEST_PASSWORD):
    return client.post(LOGIN, json={"username": username, "password": password})


def test_me_returns_scope_and_trimmed_navigation(client, auth_headers):
    me = client.get("/api/v1/auth/me", headers=auth_headers("demo.agency")).json()
    assert me["roles"] == ["agency_admin"]
    assert me["data_scope"]["agencies"] == ["星河代理"]
    assert me["data_scope"]["products"] is None
    assert [group["title"] for group in me["navigation"]] == ["看盘分析", "系统管理"]
    assert [page["id"] for page in me["navigation"][0]["pages"]] == ["report-aggregate", "report-daily"]
    assert "cost" in me["visible_metrics"]
    assert not {"revenue", "roi", "click_arpu", "revenue_gap"} & set(me["visible_metrics"])


def test_logout_revokes_the_refresh_token(client, database):
    tokens = login(client, "demo.readonly").json()
    assert client.post("/api/v1/auth/logout", json={"refresh_token": tokens["refresh_token"]}).status_code == 204
    assert client.post(REFRESH, json={"refresh_token": tokens["refresh_token"]}).status_code == 401
    assert client.post("/api/v1/auth/logout", json={"refresh_token": "unknown"}).status_code == 204


def test_wrong_password_and_unknown_user_look_the_same(client, database):
    for username in ("demo.admin", "nobody.here"):
        response = login(client, username, "wrong-password")
        assert response.status_code == 401
        assert response.json()["code"] == "INVALID_CREDENTIALS"


def test_protected_endpoint_rejects_missing_or_bad_token(client, database):
    assert client.get("/api/v1/auth/me").json()["code"] == "AUTH_REQUIRED"
    response = client.get("/api/v1/auth/me", headers={"Authorization": "Bearer not-a-jwt"})
    assert response.status_code == 401


def test_refresh_rotates_and_reuse_revokes_every_session(client, database):
    first = login(client, "demo.readonly").json()
    second = client.post(REFRESH, json={"refresh_token": first["refresh_token"]})
    assert second.status_code == 200

    reused = client.post(REFRESH, json={"refresh_token": first["refresh_token"]})
    assert reused.status_code == 401

    after_reuse = client.post(REFRESH, json={"refresh_token": second.json()["refresh_token"]})
    assert after_reuse.status_code == 401


@pytest.fixture
def newcomer(database) -> Iterator[str]:
    with SessionLocal() as session:
        session.add(m.User(id="usr_test_new", tenant_id=TENANT_ID, username="test.newcomer", display_name="新同事",
                           password_hash=hash_password(TEST_PASSWORD), must_change_password=True))
        session.flush()
        session.add(m.UserRole(user_id="usr_test_new", role="operator"))
        session.commit()
    yield "test.newcomer"
    with SessionLocal() as session:
        session.execute(delete(m.User).where(m.User.id == "usr_test_new"))
        session.commit()


def test_temporary_password_must_be_changed_before_other_access(client, newcomer):
    tokens = login(client, newcomer).json()
    assert tokens["must_change_password"] is True
    headers = {"Authorization": f"Bearer {tokens['access_token']}"}

    blocked = client.get("/api/v1/me/preferences", headers=headers)
    assert blocked.status_code == 403
    assert blocked.json()["code"] == "PASSWORD_CHANGE_REQUIRED"
    assert client.get("/api/v1/auth/me", headers=headers).json()["must_change_password"] is True

    same = client.post("/api/v1/auth/password", headers=headers,
                       json={"current_password": TEST_PASSWORD, "new_password": TEST_PASSWORD})
    assert same.status_code == 422

    changed = client.post("/api/v1/auth/password", headers=headers,
                          json={"current_password": TEST_PASSWORD, "new_password": "brand-new-pass-1"})
    assert changed.status_code == 200
    assert changed.json()["must_change_password"] is False

    fresh = {"Authorization": f"Bearer {changed.json()['access_token']}"}
    assert client.get("/api/v1/me/preferences", headers=fresh).status_code == 200
    assert login(client, newcomer).status_code == 401
    assert login(client, newcomer, "brand-new-pass-1").status_code == 200
    # 改密前的刷新令牌已被吊销
    assert client.post(REFRESH, json={"refresh_token": tokens["refresh_token"]}).status_code == 401
