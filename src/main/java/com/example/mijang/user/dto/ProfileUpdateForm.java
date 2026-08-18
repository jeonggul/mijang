/*
 * ProfileUpdateForm — 프로필 수정 요청
 *
 * 이 파일이 하는 일
 *   화면이 보내는 수정 내용이다. 네 값 모두 선택이라 바꿀 것만 보내면 된다.
 *   닉네임만 바꾸려는데 이미지 주소까지 다시 보내야 하면 화면이 번거로워진다.
 *   보내지 않은 값은 건드리지 않는다.
 */
package com.example.mijang.user.dto;

import com.example.mijang.user.policy.SignupPolicy;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 프로필 수정. 개발명세서(API) MY-01
 *
 * <p>세 값 모두 <b>선택</b>이다. 보낸 것만 바꾼다 — 닉네임만 바꾸려는데 이미지 URL 까지
 * 다시 보내야 하면 화면이 번거로워진다.
 */
@Getter
@Setter
public class ProfileUpdateForm {

    /** 가입 때와 같은 규칙(2.2). null 이면 안 바꾼다. */
    @Pattern(regexp = SignupPolicy.NICKNAME_REGEX,
             message = SignupPolicy.NICKNAME_GUIDE + "로 입력해주세요")
    private String nickname;

    /** 업로드가 아니라 URL 이다(2.3). 빈 문자열이면 이미지를 지우는 뜻으로 본다. */
    @Size(max = 512)
    private String profileImageUrl;

    /** KRW 또는 USD. */
    @Pattern(regexp = "KRW|USD", message = "KRW 또는 USD 여야 합니다")
    private String baseCurrency;

    /** SYSTEM·LIGHT·DARK 중 하나. */
    @Pattern(regexp = "SYSTEM|LIGHT|DARK", message = "허용되지 않는 값입니다")
    private String theme;
}
