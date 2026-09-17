from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.db import models as m
from app.schemas.auth import Preferences, PreferencesUpdate
from app.services.auth import CurrentUser


def get_preferences(session: Session, user: CurrentUser) -> Preferences:
    row = session.get(m.UserPreference, user.id)
    if row is None:
        return Preferences(theme_mode="system", theme_preset="aurora-blue", table_columns={}, revision=0)
    return Preferences.model_validate({
        "theme_mode": row.theme_mode, "theme_preset": row.theme_preset, "custom_primary": row.custom_primary,
        "table_columns": row.table_columns, "revision": row.revision,
    })


def update_preferences(session: Session, user: CurrentUser, body: PreferencesUpdate) -> Preferences:
    row = session.get(m.UserPreference, user.id, with_for_update=True)
    current_revision = row.revision if row else 0
    if body.revision != current_revision:
        session.rollback()
        raise ApiError(409, "CONFLICT", "偏好已在其他设备修改", "请刷新后重试")
    values = body.model_dump(exclude={"revision"})
    if row is None:
        row = m.UserPreference(user_id=user.id, **values, revision=1)
        session.add(row)
    else:
        for field, value in values.items():
            setattr(row, field, value)
        row.revision = current_revision + 1
    session.commit()
    return Preferences.model_validate({**values, "revision": row.revision})
