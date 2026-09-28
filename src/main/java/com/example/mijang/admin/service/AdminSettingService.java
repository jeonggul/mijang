package com.example.mijang.admin.service;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.mapper.AdminSettingMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영 설정을 읽고 쓰는 유일한 통로 — 인스턴스 캐시로 들고 있고, 값이 없거나 깨지면 기본값으로 답한다. */
@Service
@RequiredArgsConstructor
public class AdminSettingService {

    private final AdminSettingMapper settingMapper;

    /** 마지막으로 읽은 설정. null 이면 아직 안 읽은 것이다. */
    private final AtomicReference<Map<AdminSettingKey, String>> cache = new AtomicReference<>();

    /** 설정 전부를 키 문자열 → 값으로 돌려준다. */
    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<AdminSettingKey, String> current = current();
        Map<String, String> out = new LinkedHashMap<>();
        for (AdminSettingKey k : AdminSettingKey.values()) {
            out.put(k.key(), current.getOrDefault(k, k.defaultValue()));
        }
        return out;
    }

    /** 한 칸을 저장한다. 모르는 키거나 받을 수 없는 값이면 400 이다. */
    @Transactional
    public void update(Long adminId, String key, String value) {
        AdminSettingKey setting = AdminSettingKey.of(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "key"));
        if (!setting.accepts(value)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "value");
        }
        settingMapper.upsert(setting.key(), setting.normalize(value), adminId);
        cache.set(null);        // 다음 읽기에서 다시 채운다
    }

    /** 참거짓 설정을 읽는다. 못 읽으면 기본값이다. */
    public boolean isOn(AdminSettingKey key) {
        return Boolean.parseBoolean(current().getOrDefault(key, key.defaultValue()));
    }

    /** 정수 설정을 읽는다. 값이 깨져 있으면 기본값으로 답한다. */
    public int number(AdminSettingKey key) {
        String raw = current().getOrDefault(key, key.defaultValue());
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return Integer.parseInt(key.defaultValue());
        }
    }

    /** 캐시가 비어 있으면 표에서 채운다. 모르는 키가 표에 있으면 무시한다. */
    private Map<AdminSettingKey, String> current() {
        Map<AdminSettingKey, String> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        Map<AdminSettingKey, String> loaded = new EnumMap<>(AdminSettingKey.class);
        for (Map<String, Object> row : settingMapper.findAll()) {
            Object k = row.get("settingKey");
            Object v = row.get("settingValue");
            if (k != null && v != null) {
                AdminSettingKey.of(k.toString()).ifPresent(key -> loaded.put(key, v.toString()));
            }
        }
        cache.set(loaded);
        return loaded;
    }
}
