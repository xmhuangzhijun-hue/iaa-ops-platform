import pytest

from app.domain.access import navigation_for, permissions_for


def pages(navigation):
    return {group["title"]: [page["id"] for page in group["pages"]] for group in navigation}


def test_agency_admin_sees_only_external_views():
    navigation = pages(navigation_for(permissions_for(["agency_admin"])))
    assert navigation == {
        "看盘分析": ["report-aggregate", "report-daily"],
        "系统管理": ["user-manage", "metric-catalog"],
    }


def test_super_admin_sees_every_page():
    navigation = pages(navigation_for(permissions_for(["super_admin"])))
    assert len(navigation["看盘分析"]) == 5
    assert len(navigation["系统管理"]) == 5


def test_real_and_external_metrics_cannot_be_combined():
    with pytest.raises(ValueError):
        permissions_for(["operator", "customer"])


def test_unknown_role_is_rejected():
    with pytest.raises(ValueError):
        permissions_for(["boss"])
