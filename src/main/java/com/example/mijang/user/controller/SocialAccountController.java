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

/** 내 소셜 연동 목록 조회와 연동 해제 API를 제공한다. */
@RestController
@RequestMapping("/api/users/me/social")
@RequiredArgsConstructor
public class SocialAccountController {

    /** oauth_accounts.provider enum과 같다 — 여기 없는 값은 받지 않는다. */
    private static final Set<String> PROVIDERS = Set.of("GOOGLE", "KAKAO");

    private final OAuthAccountMapper oauthMapper;

    /** 내 소셜 연동 목록을 돌려준다 — 없으면 빈 배열이다. */
    @GetMapping
    public ApiResponse<List<SocialAccountResponse>> list(@LoginUser SessionUser me) {
        return ApiResponse.ok(oauthMapper.findByUser(me.userId()));
    }

    /** 소셜 연동을 해제한다 — 연동돼 있지 않아도 성공으로 답해 멱등을 지킨다. */
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
