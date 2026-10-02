package com.example.mijang.user.mapper;

import com.example.mijang.user.domain.PasswordResetToken;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** password_reset_tokens 테이블 접근을 담당한다. */
@Mapper
public interface PasswordResetTokenMapper {

    /** 토큰 해시로 한 건 조회한다. */
    PasswordResetToken findByTokenHash(@Param("tokenHash") String tokenHash);

    /** 가장 최근의 미사용·미만료 토큰을 조회한다 — 재전송 쿨다운 판단에 쓴다. */
    PasswordResetToken findLatestActiveByUserId(@Param("userId") Long userId);

    /** 재설정 토큰 한 행을 넣는다. */
    int insert(@Param("userId") Long userId,
               @Param("tokenHash") String tokenHash,
               @Param("expiresAt") LocalDateTime expiresAt);

    /** 토큰을 사용 처리한다 — used_at IS NULL 조건으로 동시 진입 시 한쪽만 1을 받는다. */
    int markUsed(@Param("tokenId") Long tokenId);

    /** 이 사용자의 유효한 토큰을 전부 무효화한다 — 유효한 링크는 항상 최신 하나뿐이다. */
    int invalidateActiveByUserId(@Param("userId") Long userId);

    /** 만료가 하루 지난 행을 정리한다 — 요청 때마다 불러 별도 배치가 필요 없다. */
    int deleteExpired();
}
