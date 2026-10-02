/*
 * AdminSettingController — 운영 설정 API
 *
 * 이 파일이 하는 일
 *   설정을 통째로 내주고, 한 칸씩 받아 저장한다.
 *   한 번에 전부 받지 않는 이유 — 화면에서 스위치 하나를 누르면 그것만 바뀌면 된다.
 *   전체를 받으면 두 관리자가 다른 칸을 만졌을 때 나중 저장이 앞 것을 덮는다.
 */
package com.example.mijang.admin.controller;

import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 설정 API. 화면 SR-013 운영 설정
 *
 * <p>{@code /api/admin/**} 전체가 {@code SecurityConfig} 에서 ROLE_ADMIN 으로 막혀 있다.
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final AdminSettingService settingService;

    /** 현재 설정 전부. 화면이 알약·스위치의 눌린 상태를 이걸로 그린다. */
    @GetMapping
    public ApiResponse<Map<String, String>> all() {
        return ApiResponse.ok(settingService.all());
    }

    /** 한 칸 저장. 모르는 키나 받을 수 없는 값이면 400 이다. */
    @PutMapping
    public ApiResponse<Map<String, String>> update(@LoginUser SessionUser me,
                                                   @Valid @RequestBody SettingForm form) {
        settingService.update(me.userId(), form.key(), form.value());
        return ApiResponse.ok(settingService.all());
    }

    /** 저장 요청. 값은 참거짓도 숫자도 문자열로 온다 — 해석은 서버가 한다. */
    public record SettingForm(@NotBlank String key, @NotBlank String value) {
    }
}
