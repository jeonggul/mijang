package com.example.mijang.user.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 조회·수정. 개발명세서(API) USER-001 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * USER-001 내 프로필.
     *
     * <p>토큰에 담긴 닉네임을 쓰지 않고 DB 를 다시 읽는다. 토큰은 최대 30분 전 값이라
     * 프로필을 방금 고쳤어도 옛 이름이 보일 수 있다.
     *
     * @throws BusinessException 토큰은 유효한데 계정이 사라졌을 때(404)
     */
    @Transactional(readOnly = true)
    public UserResponse findMe(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return new UserResponse(user.id(), user.email(), user.nickname(), user.createdAt());
    }
}
