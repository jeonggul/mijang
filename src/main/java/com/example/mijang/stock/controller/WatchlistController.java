package com.example.mijang.stock.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import com.example.mijang.stock.dto.WatchlistItemResponse;
import com.example.mijang.stock.service.WatchlistService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관심종목 API. 개발명세서(API) WATCH-01·WATCH-02 · 화면 SR-010
 *
 * <p>종목 조회와 달리 <b>전부 인증이 필요하다.</b> 누구의 관심종목인지가 있어야 하는 자원이다.
 * 사용자 식별자는 요청에서 받지 않고 토큰에서 꺼낸다 — 받으면 남의 것을 조회할 수 있다.
 */
@RestController
@RequestMapping("/api/watchlists")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    /** 목록. 시세가 함께 나온다. */
    @GetMapping
    public ApiResponse<List<WatchlistItemResponse>> list(@LoginUser SessionUser me) {
        return ApiResponse.ok(watchlistService.list(me.userId()));
    }

    /** 등록. {@code WATCH-01} */
    @PostMapping("/items")
    public ApiResponse<Void> add(@LoginUser SessionUser me,
                                 @RequestBody @jakarta.validation.Valid AddItemRequest request) {
        watchlistService.add(me.userId(), request.symbol());
        return ApiResponse.ok(null);
    }

    /** 해제. {@code WATCH-01} */
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> remove(@LoginUser SessionUser me, @PathVariable Long id) {
        watchlistService.remove(me.userId(), id);
        return ApiResponse.ok(null);
    }

    /** 등록 요청 본문. 그룹은 서버가 정한다(2.8). */
    public record AddItemRequest(@NotBlank String symbol) {
    }
}
