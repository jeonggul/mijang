/*
 * UserController — 회원 API
 *
 * 이 파일이 하는 일
 *   마이페이지 화면이 부르는 것들을 내준다 — 내 정보 조회, 프로필 수정,
 *   보유 종목 요약.
 *   누구인지는 요청에서 받지 않고 로그인 토큰에서 꺼낸다. 요청에서 받으면
 *   남의 프로필을 들여다보거나 고칠 수 있게 된다.
 */
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

/**
 * 회원 API. 개발명세서(API) MY-01·MY-02 · 화면 SR-011·SR-012
 *
 * <p>사용자 식별자는 요청에서 받지 않고 토큰에서 꺼낸다 — 받으면 남의 프로필을
 * 조회하거나 고칠 수 있다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    /** 마이페이지 보유 요약에 보여줄 종목 수. 전체는 포트폴리오 화면이 담당한다. */
    private static final int SUMMARY_LIMIT = 5;

    private final UserService userService;
    private final HoldingService holdingService;

    /** 내 프로필. {@code MY-01} */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@LoginUser SessionUser me) {
        return ApiResponse.ok(userService.findMe(me.userId()));
    }

    /** 프로필 수정. 보낸 항목만 바뀐다. */
    @PatchMapping("/me")
    public ApiResponse<UserResponse> update(@LoginUser SessionUser me,
                                            @Valid @RequestBody ProfileUpdateForm form) {
        return ApiResponse.ok(userService.updateProfile(me.userId(), form));
    }

    /**
     * 보유 종목 요약. {@code MY-02}
     *
     * <p>포트폴리오 화면과 <b>같은 조회</b>를 쓴다(2.5). 별도 쿼리를 만들면 두 화면의
     * 값이 어긋난다. 여기서는 상위 몇 개만 자른다.
     */
    @GetMapping("/me/holdings")
    public ApiResponse<List<HoldingResponse>> holdings(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "" + SUMMARY_LIMIT) int limit) {
        List<HoldingResponse> all = holdingService.findByUser(me.userId());
        return ApiResponse.ok(all.size() <= limit ? all : all.subList(0, limit));
    }
}
