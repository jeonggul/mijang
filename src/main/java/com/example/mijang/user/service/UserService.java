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

/** 마이페이지 프로필 조회·수정을 담당한다. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /** 내 프로필을 돌려준다. */
    @Transactional(readOnly = true)
    public UserResponse findMe(Long userId) {
        UserResponse user = userMapper.findProfile(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /** 보낸 항목만 프로필에 반영한다. 닉네임 금지어·중복을 확인하고, 바꿀 것이 없으면 DB 를 부르지 않는다. */
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
