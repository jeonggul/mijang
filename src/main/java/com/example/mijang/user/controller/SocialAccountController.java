/*
 * SocialAccountController — 내 소셜 연동을 보고 끊는다
 *
 * 이 파일이 하는 일
 *   설정 → 계정 카드가 쓰는 두 가지. 연동 목록을 주고, 연동을 끊는다.
 *
 *   끊어도 계정은 그대로다. 지우는 것은 oauth_accounts 의 한 행뿐이고
 *   users 는 건드리지 않는다.
 *
 *   대상 회원은 언제나 로그인 정보에서 가져온다. 경로로 받는 것은 provider 뿐이다 —
 *   userId 를 받으면 남의 연동을 끊을 수 있다.
 */
package com.example.mijang.user.controller;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import com.example.mijang.user.dto.SocialAccountResponse;
import com.example.mijang.user.mapper.OAuthAccountMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소셜 연동 관리. {@code AUTH-07} */
@RestController
@RequestMapping("/api/users/me/social")
@RequiredArgsConstructor
public class SocialAccountController {

    /** oauth_accounts.provider 의 enum 과 같다. 여기 없는 값은 받지 않는다. */
    private static final Set<String> PROVIDERS = Set.of("GOOGLE", "KAKAO");

    private final OAuthAccountMapper oauthMapper;

    /** 내가 연동한 것들. 하나도 없으면 빈 배열이다 — 화면이 행을 그리지 않는다. */
    @GetMapping
    public ApiResponse<List<SocialAccountResponse>> list(@LoginUser SessionUser me) {
        return ApiResponse.ok(oauthMapper.findByUser(me.userId()));
    }

    /**
     * 연동 해제.
     *
     * <p>연동돼 있지 않아도 성공으로 답한다. 두 번 눌러도 결과가 같아야 한다 —
     * {@code SocialLoginService.link()} 가 반대 방향에서 같은 원칙을 쓴다.
     *
     * <p>로그인 수단이 남지 않는 경우를 여기서 막지 않는다. 스키마가
     * {@code users.password_hash NOT NULL} 이라 비밀번호 없는 계정 자체가 없다.
     */
    @DeleteMapping("/{provider}")
    public ApiResponse<Void> unlink(@LoginUser SessionUser me, @PathVariable String provider) {
        String normalized = provider.toUpperCase(Locale.ROOT);
        if (!PROVIDERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "provider");
        }
        oauthMapper.deleteByUserAndProvider(me.userId(), normalized);
        return ApiResponse.ok(null);
    }
}
