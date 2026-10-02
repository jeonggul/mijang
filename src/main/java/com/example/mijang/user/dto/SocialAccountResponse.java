/*
 * SocialAccountResponse — 연동된 소셜 계정 한 건
 *
 * 이 파일이 하는 일
 *   설정 화면의 "연결된 계정" 행을 그리는 데 필요한 값만 담는다.
 *
 *   provider_user_id 는 담지 않는다. 화면이 쓸 일이 없고, 제공자 쪽 식별자를
 *   굳이 밖으로 낼 이유가 없다.
 */
package com.example.mijang.user.dto;

import java.time.LocalDateTime;

/**
 * 연동된 소셜 계정.
 *
 * @param provider {@code GOOGLE} 또는 {@code KAKAO}. 한글 이름은 화면이 고른다
 * @param linkedAt 연동한 시각. oauth_accounts.created_at
 */
public record SocialAccountResponse(String provider, LocalDateTime linkedAt) {
}
