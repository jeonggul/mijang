package com.example.mijang.user.service;

import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 회원 조회·수정. 개발명세서(API) USER-001 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public UserResponse findMe(Long userId) {
        throw new UnsupportedOperationException("TODO USER-001: users 조회 후 UserResponse 매핑");
    }
}
