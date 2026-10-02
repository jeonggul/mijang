/*
 * FixedSettings — 시험용 운영 설정
 *
 * 이 파일이 하는 일
 *   AdminSettingService 를 상속해 DB 없이 값을 정해 준다.
 *   기본은 마이그레이션이 넣는 값과 같다 — 설정을 만지지 않는 시험이
 *   설정 때문에 결과가 달라지면 안 된다.
 *
 *   시험마다 가짜를 새로 만들면 기본값이 제각각이 되어, 어떤 시험은 글쓰기 제한에
 *   걸리고 어떤 시험은 안 걸리는 일이 생긴다. 한 곳에 둔다.
 */
package com.example.mijang.support;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.service.AdminSettingService;
import java.util.EnumMap;
import java.util.Map;

/** 값을 정해 두고 답하는 운영 설정. DB 를 타지 않는다. */
public class FixedSettings extends AdminSettingService {

    private final Map<AdminSettingKey, String> values = new EnumMap<>(AdminSettingKey.class);

    public FixedSettings() {
        super(null);
    }

    /** 이 키만 값을 바꾼다. 나머지는 기본값 그대로다. */
    public FixedSettings with(AdminSettingKey key, String value) {
        values.put(key, value);
        return this;
    }

    @Override
    public boolean isOn(AdminSettingKey key) {
        return Boolean.parseBoolean(values.getOrDefault(key, key.defaultValue()));
    }

    @Override
    public int number(AdminSettingKey key) {
        return Integer.parseInt(values.getOrDefault(key, key.defaultValue()));
    }
}
