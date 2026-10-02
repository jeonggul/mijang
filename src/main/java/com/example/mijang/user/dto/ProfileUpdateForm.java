package com.example.mijang.user.dto;

import com.example.mijang.user.policy.SignupPolicy;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 프로필 수정 입력이다 — 모든 값이 선택이고 보낸 것만 바꾼다. */
@Getter
@Setter
public class ProfileUpdateForm {

    /** 닉네임이다 — 가입 때와 같은 규칙을 쓰고 null이면 안 바꾼다. */
    @Pattern(regexp = SignupPolicy.NICKNAME_REGEX,
             message = SignupPolicy.NICKNAME_GUIDE + "로 입력해주세요")
    private String nickname;

    /** 프로필 이미지 URL이다 — 빈 문자열이면 이미지를 지우는 뜻으로 본다. */
    @Size(max = 512)
    private String profileImageUrl;

    /** KRW 또는 USD. */
    @Pattern(regexp = "KRW|USD", message = "KRW 또는 USD 여야 합니다")
    private String baseCurrency;

    /** SYSTEM·LIGHT·DARK 중 하나. */
    @Pattern(regexp = "SYSTEM|LIGHT|DARK", message = "허용되지 않는 값입니다")
    private String theme;
}
