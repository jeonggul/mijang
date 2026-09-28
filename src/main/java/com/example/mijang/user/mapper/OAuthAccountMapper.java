package com.example.mijang.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** oauth_accounts 테이블 접근 — 제공자 쪽 사용자와 우리 회원을 잇는다. */
@Mapper
public interface OAuthAccountMapper {

    /** 이 제공자 계정에 연결된 회원 id를 조회한다 — 없으면 null이다. */
    Long findUserId(@Param("provider") String provider,
                    @Param("providerUserId") String providerUserId);

    /** 이 회원이 이 제공자를 이미 연결했는지 확인한다. */
    boolean existsByUserAndProvider(@Param("userId") Long userId,
                                    @Param("provider") String provider);

    /** 소셜 연동 한 행을 넣는다. */
    int insert(@Param("userId") Long userId,
               @Param("provider") String provider,
               @Param("providerUserId") String providerUserId);

    /** 이 회원의 소셜 연동 목록을 조회한다. */
    java.util.List<com.example.mijang.user.dto.SocialAccountResponse> findByUser(
            @Param("userId") Long userId);

    /** 연동 한 행만 지운다 — 회원은 건드리지 않으며 연동이 없었으면 0을 돌려준다. */
    int deleteByUserAndProvider(@Param("userId") Long userId,
                                @Param("provider") String provider);

    /** 이 회원의 소셜 연동을 모두 지운다 — 남겨 두면 같은 소셜로 재가입할 때 유니크 제약에 막힌다. */
    int deleteByUser(@Param("userId") Long userId);
}
