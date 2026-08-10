package com.example.mijang.user.service;

import com.example.mijang.user.dto.LoginForm;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 가입·로그인·로그아웃. 개발명세서(API) AUTH-001~003
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;

    public Long signup(SignupForm form) {
        throw new UnsupportedOperationException("TODO AUTH-001: 이메일/닉네임 중복 확인 후 users 저장");
    }

    public Long login(LoginForm form) {
        throw new UnsupportedOperationException("TODO AUTH-002: 비밀번호 검증 후 세션 발급");
    }

    public void logout() {
        throw new UnsupportedOperationException("TODO AUTH-003: 세션 폐기");
    }
}
