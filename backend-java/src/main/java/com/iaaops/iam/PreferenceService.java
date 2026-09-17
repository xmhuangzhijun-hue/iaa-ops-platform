package com.iaaops.iam;

import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.persistence.UserPreferenceEntity;
import com.iaaops.iam.persistence.UserPreferenceRepository;
import com.iaaops.shared.error.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

    private final UserPreferenceRepository preferences;

    PreferenceService(UserPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public Preferences get(CurrentUser user) {
        return preferences.findById(user.id())
                .map(entity -> new Preferences(entity.getThemeMode(), entity.getThemePreset(),
                        entity.getCustomPrimary(), entity.getTableColumns(), entity.getRevision()))
                .orElseGet(() -> new Preferences("system", "aurora-blue", null, Map.of(), 0));
    }

    @Transactional
    public Preferences update(CurrentUser user, Preferences requested) {
        UserPreferenceEntity entity = preferences.findById(user.id()).orElse(null);
        int current = entity == null ? 0 : entity.getRevision();
        if (requested.revision() != current) {
            throw ApiException.conflict("偏好已在其他设备修改", "请刷新后重试");
        }
        int next = current + 1;
        if (entity == null) {
            entity = new UserPreferenceEntity(user.id(), requested.themeMode(), requested.themePreset(),
                    requested.customPrimary(), requested.tableColumns(), next);
            preferences.save(entity);
        } else {
            entity.apply(requested.themeMode(), requested.themePreset(), requested.customPrimary(),
                    requested.tableColumns(), next);
        }
        return new Preferences(requested.themeMode(), requested.themePreset(), requested.customPrimary(),
                requested.tableColumns(), next);
    }

    public record Preferences(
            String themeMode,
            String themePreset,
            String customPrimary,
            Map<String, List<String>> tableColumns,
            int revision) {
    }
}
