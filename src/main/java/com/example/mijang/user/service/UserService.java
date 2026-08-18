/*
 * UserService — 프로필 조회와 수정
 *
 * 이 파일이 하는 일
 *   마이페이지의 본체다. 내 정보를 꺼내 주고, 수정 요청을 받아 반영한다.
 *   바꾸려는 닉네임이 남의 것과 겹치는지 먼저 보고, 바뀐 것이 하나도 없으면
 *   DB 를 건드리지 않고 거절한다.
 */
package com.example.mijang.user.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.user.dto.ProfileUpdateForm;
import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.policy.SignupPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 조회·수정. 개발명세서(API) MY-01 · 화면 SR-011·SR-012
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 내 프로필.
     *
     * @throws BusinessException 없는 사용자일 때(404). 토큰은 유효한데 계정이 사라진 경우다
     */
    @Transactional(readOnly = true)
    public UserResponse findMe(Long userId) {
        UserResponse user = userMapper.findProfile(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 프로필 수정. 보낸 항목만 바꾼다.
     *
     * <p>닉네임은 가입 때와 같은 규칙으로 본다(2.2). 형식은 {@code @Pattern} 이 이미 걸렀고
     * 여기서는 <b>금지어와 중복</b>만 확인한다 — 둘 다 DTO 애너테이션으로는 판정할 수 없다.
     *
     * <p>바꿀 것이 하나도 없으면 DB 를 부르지 않는다. XML 의 {@code <set>} 이 비면
     * 문장이 만들어지지 않는다.
     *
     * @throws BusinessException 금지어(422)·중복(409)
     */
    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateForm form) {
        if (form.getNickname() != null) {
            if (SignupPolicy.containsForbiddenWord(form.getNickname())) {
                throw new BusinessException(ErrorCode.AUTH_NICKNAME_FORBIDDEN, "nickname");
            }
            if (userMapper.countByNicknameExcluding(form.getNickname(), userId) > 0) {
                throw new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED, "nickname");
            }
        }
        if (hasAnyChange(form)) {
            userMapper.updateProfile(userId, form.getNickname(), form.getProfileImageUrl(),
                    form.getBaseCurrency(), form.getTheme());
        }
        return findMe(userId);
    }

    /** 바꿀 항목이 하나라도 있는지. 전부 null 이면 UPDATE 문을 만들 수 없다. */
    private boolean hasAnyChange(ProfileUpdateForm form) {
        return form.getNickname() != null
                || form.getProfileImageUrl() != null
                || form.getBaseCurrency() != null
                || form.getTheme() != null;
    }
}
