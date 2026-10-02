package com.example.mijang.user.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.service.HoldingService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import com.example.mijang.user.dto.ProfileUpdateForm;
import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 조회·프로필 수정·보유 종목 요약 등 마이페이지 API를 제공한다 — 사용자 식별자는 토큰에서만 꺼낸다. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    /** 마이페이지 보유 요약에 보여줄 종목 수다. */
    private static final int SUMMARY_LIMIT = 5;

    private final UserService userService;
    private final HoldingService holdingService;

    /** 내 프로필을 돌려준다. */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@LoginUser SessionUser me) {
        return ApiResponse.ok(userService.findMe(me.userId()));
    }

    /** 프로필을 수정한다 — 보낸 항목만 바뀐다. */
    @PatchMapping("/me")
    public ApiResponse<UserResponse> update(@LoginUser SessionUser me,
                                            @Valid @RequestBody ProfileUpdateForm form) {
        return ApiResponse.ok(userService.updateProfile(me.userId(), form));
    }

    /** 보유 종목 상위 몇 개를 요약으로 돌려준다 — 포트폴리오 화면과 같은 조회를 써서 값이 어긋나지 않게 한다. */
    @GetMapping("/me/holdings")
    public ApiResponse<List<HoldingResponse>> holdings(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "" + SUMMARY_LIMIT) int limit) {
        List<HoldingResponse> all = holdingService.findByUser(me.userId());
        return ApiResponse.ok(all.size() <= limit ? all : all.subList(0, limit));
    }
}
