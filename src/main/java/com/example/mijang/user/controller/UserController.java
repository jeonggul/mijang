package com.example.mijang.user.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 API. 개발명세서(API) USER-001 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * USER-001 내 프로필.
     *
     * <p>{@code /api/users/**} 는 SecurityConfig 의 anyRequest().authenticated() 에 걸리므로
     * 여기까지 왔다면 인증된 요청이고 me 는 null 이 아니다. null 검사를 두지 않는 이유다.
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@LoginUser SessionUser me) {
        return ApiResponse.ok(userService.findMe(me.userId()));
    }
}
