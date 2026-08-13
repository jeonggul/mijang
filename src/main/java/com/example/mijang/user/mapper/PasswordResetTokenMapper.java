package com.example.mijang.user.mapper;

import com.example.mijang.user.domain.PasswordResetToken;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * password_reset_tokens 접근. 문장은 resources/mapper/PasswordResetTokenMapper.xml 에 둔다.
 */
@Mapper
public interface PasswordResetTokenMapper {

    /** 해시로 한 건 조회. 링크 진입과 저장 두 곳에서 쓴다. */
    PasswordResetToken findByTokenHash(@Param("tokenHash") String tokenHash);

    /** 가장 최근의 미사용·미만료 토큰. 재전송 쿨다운을 판단할 때만 쓴다. */
    PasswordResetToken findLatestActiveByUserId(@Param("userId") Long userId);

    int insert(@Param("userId") Long userId,
               @Param("tokenHash") String tokenHash,
               @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 일회용 처리.
     *
     * <p>{@code used_at IS NULL} 조건이 붙어 있어 같은 링크로 동시에 두 번 들어와도
     * 한 쪽만 1을 받는다. 잠금 없이 이 조건 하나로 중복 사용이 막힌다.
     *
     * @return 바뀐 행 수. 0 이면 이미 누가 썼다는 뜻이다
     */
    int markUsed(@Param("tokenId") Long tokenId);

    /** 새로 발급하기 전에 이전 링크를 전부 무효화한다. 유효한 링크는 항상 최신 하나뿐이다. */
    int invalidateActiveByUserId(@Param("userId") Long userId);

    /** 만료 하루 뒤 행 정리. 요청 때마다 부르므로 별도 배치가 필요 없다. */
    int deleteExpired();
}
