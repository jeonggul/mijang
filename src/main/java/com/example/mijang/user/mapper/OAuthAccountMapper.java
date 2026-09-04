/*
 * OAuthAccountMapper — 소셜 계정 연결
 *
 * 이 파일이 하는 일
 *   oauth_accounts 를 읽고 쓴다. "이 제공자의 이 사용자"가 우리 회원 누구인지를 잇는다.
 *
 *   provider_user_id 로 찾는 이유 — 이메일은 바뀔 수 있다. 구글에서 이메일을 바꿔도
 *   같은 사람이어야 하므로, 변하지 않는 제공자 쪽 식별자를 열쇠로 쓴다.
 */
package com.example.mijang.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OAuthAccountMapper {

    /** 이 제공자 계정에 연결된 회원 id. 없으면 null. */
    Long findUserId(@Param("provider") String provider,
                    @Param("providerUserId") String providerUserId);

    /** 이 회원이 이 제공자를 이미 연결했는지. 중복 연결을 막는다. */
    boolean existsByUserAndProvider(@Param("userId") Long userId,
                                    @Param("provider") String provider);

    int insert(@Param("userId") Long userId,
               @Param("provider") String provider,
               @Param("providerUserId") String providerUserId);
}
