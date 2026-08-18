/*
 * UserResponse — 내 프로필 응답
 *
 * 이 파일이 하는 일
 *   마이페이지가 받아 가는 내 정보다. 닉네임·이메일·가입일과
 *   화면 설정(기준 통화·테마)을 담는다.
 *   이메일은 바꿀 수 없지만 보여는 준다 — 어느 계정으로 들어와 있는지
 *   확인할 수 있어야 하기 때문이다. 비밀번호 해시는 절대 담지 않는다.
 */
package com.example.mijang.user.dto;

import java.time.LocalDateTime;

/**
 * 내 프로필. 개발명세서(API) MY-01
 *
 * <p>이메일은 바꿀 수 없지만 보여는 준다(2.1). 화면이 "변경 불가"를 표시한다.
 */
public record UserResponse(
        Long userId,
        String email,
        String nickname,
        String profileImageUrl,
        String role,
        String baseCurrency,
        String theme,
        LocalDateTime joinedAt) {
}
