package com.example.mijang.admin.controller;

import com.example.mijang.admin.dto.AdminUserResponse;
import com.example.mijang.admin.service.AdminUserService;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 사용자 API. 권한은 {@code /api/admin/**} 보안 규칙이 막는다. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> users(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.users(me.userId(), status, q, limit));
    }

    @GetMapping("/count")
    public ApiResponse<Integer> count(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.userCount(status, q));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> changeStatus(
            @LoginUser SessionUser me,
            @PathVariable Long userId,
            @RequestBody @Valid StatusRequest request) {
        service.changeStatus(me.userId(), userId, request.status());
        return ApiResponse.ok(null);
    }

    /** 관리자 권한 해제. {@code ADMIN-03} 본인과 마지막 활성 관리자는 내릴 수 없다. */
    @PatchMapping("/{userId}/demote")
    public ApiResponse<Void> demote(@LoginUser SessionUser me, @PathVariable Long userId) {
        service.demote(me.userId(), userId);
        return ApiResponse.ok(null);
    }

    public record StatusRequest(
            @NotBlank
            @Pattern(regexp = "ACTIVE|SUSPENDED", message = "ACTIVE 또는 SUSPENDED여야 합니다")
            String status) {
    }
}
