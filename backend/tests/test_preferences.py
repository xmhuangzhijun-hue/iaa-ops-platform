PREFERENCES = "/api/v1/me/preferences"


def test_preferences_default_save_and_conflict(client, auth_headers):
    headers = auth_headers("demo.company")
    current = client.get(PREFERENCES, headers=headers).json()
    assert current["revision"] == 0
    assert current["theme_mode"] == "system"

    body = {"theme_mode": "dark", "theme_preset": "nebula-purple",
            "table_columns": {"report-aggregate": ["product", "cost", "roi"]}, "revision": 0}
    saved = client.put(PREFERENCES, headers=headers, json=body)
    assert saved.status_code == 200
    assert saved.json()["revision"] == 1

    stale = client.put(PREFERENCES, headers=headers, json=body)
    assert stale.status_code == 409
    assert stale.json()["code"] == "CONFLICT"

    missing_color = client.put(PREFERENCES, headers=headers, json={**body, "theme_preset": "custom", "revision": 1})
    assert missing_color.status_code == 422

    reloaded = client.get(PREFERENCES, headers=headers).json()
    assert reloaded["theme_preset"] == "nebula-purple"
    assert reloaded["table_columns"] == {"report-aggregate": ["product", "cost", "roi"]}
