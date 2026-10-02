package com.example.mijang.admin.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** admin_settings 접근 — 운영 설정을 통째로 읽고 한 칸씩 덮어쓴다. */
@Mapper
public interface AdminSettingMapper {

    /** 설정 전부를 키·값으로 읽는다. */
    List<Map<String, Object>> findAll();

    /** 한 칸을 덮어쓴다. 없던 키면 넣는다(UPSERT). */
    int upsert(@Param("key") String key,
               @Param("value") String value,
               @Param("adminId") Long adminId);
}
