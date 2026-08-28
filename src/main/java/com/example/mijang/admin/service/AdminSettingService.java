/*
 * AdminSettingService — 운영 설정의 본체
 *
 * 이 파일이 하는 일
 *   설정을 읽고 쓰는 유일한 통로다. 값의 해석(참거짓·정수)도 여기서 한다.
 *
 *   읽기가 아주 잦다. 글 하나 쓸 때마다 글쓰기 제한 일수를 보고, 시세 한 건마다
 *   실시간 공급 여부를 본다. 그래서 메모리에 들고 있다가 바꿀 때만 다시 읽는다 —
 *   요청마다 표를 때리면 여덟 줄짜리 표에 초당 수십 번 질의가 나간다.
 *
 *   캐시는 이 인스턴스 안에만 있다. 서버가 여러 대가 되면 다른 대에는 늦게 반영되는데,
 *   지금은 한 대라 문제가 없고 그때는 표를 매번 읽거나 무효화를 붙이면 된다.
 */
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

/**
 * 운영 설정. 화면 SR-013 운영 설정 탭
 *
 * <p>설정이 없거나 값이 깨져 있으면 <b>기본값으로 답한다.</b> 설정 하나를 못 읽었다고
 * 서비스가 멈추면 안 된다 — 운영 설정은 서비스의 부속이지 전제가 아니다.
 */
@Service
@RequiredArgsConstructor
public class AdminSettingService {

    private final AdminSettingMapper settingMapper;

    /** 마지막으로 읽은 설정. null 이면 아직 안 읽었다는 뜻이다. */
    private final AtomicReference<Map<AdminSettingKey, String>> cache = new AtomicReference<>();

    /** 전부. 화면이 현재 상태를 그릴 때 쓴다. 키 문자열 → 값. */
    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<AdminSettingKey, String> current = current();
        Map<String, String> out = new LinkedHashMap<>();
        for (AdminSettingKey k : AdminSettingKey.values()) {
            out.put(k.key(), current.getOrDefault(k, k.defaultValue()));
        }
        return out;
    }

    /**
     * 한 칸 저장.
     *
     * <p>알려진 키인지, 그 키가 받을 수 있는 값인지 <b>둘 다</b> 본다. 키만 보면
     * `news.refresh.minutes` 에 `-1` 이 들어가고, 값만 보면 아무 키나 표에 쌓인다.
     *
     * @throws BusinessException 모르는 키이거나 받을 수 없는 값일 때(400)
     */
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

    /** 참거짓 설정. 못 읽으면 기본값이다. */
    public boolean isOn(AdminSettingKey key) {
        return Boolean.parseBoolean(current().getOrDefault(key, key.defaultValue()));
    }

    /** 정수 설정. 값이 깨져 있으면 기본값으로 답한다. */
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
