/*
 * AdminSettingMapper — admin_settings 접근
 *
 * 이 파일이 하는 일
 *   운영 설정을 통째로 읽고, 한 칸씩 덮어쓴다.
 *   읽기는 항상 전체다 — 여덟 줄짜리 표라 한 줄씩 부르는 것이 더 비싸고,
 *   설정을 쓰는 쪽은 대개 두세 개를 같이 본다.
 */
package com.example.mijang.admin.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * admin_settings 접근. 화면 SR-013 운영 설정
 */
@Mapper
public interface AdminSettingMapper {

    /** 전부. 키 → 값. */
    List<Map<String, Object>> findAll();

    /**
     * 덮어쓴다. 없던 키면 넣는다.
     *
     * <p>마이그레이션이 기본값을 미리 넣지만, 나중에 키가 늘어나면 그 줄이 없을 수 있다.
     * UPSERT 로 두면 표에 줄이 있든 없든 같은 문장이 통한다.
     */
    int upsert(@Param("key") String key,
               @Param("value") String value,
               @Param("adminId") Long adminId);
}
