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

/** 관심종목 API 다. 전부 인증이 필요하고 사용자 식별자는 토큰에서 꺼낸다. */
@RestController
@RequestMapping("/api/watchlists")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    /** 관심종목 목록을 시세와 함께 돌려준다. */
    @GetMapping
    public ApiResponse<List<WatchlistItemResponse>> list(@LoginUser SessionUser me) {
        return ApiResponse.ok(watchlistService.list(me.userId()));
    }

    /** 관심종목을 등록한다. */
    @PostMapping("/items")
    public ApiResponse<Void> add(@LoginUser SessionUser me,
                                 @RequestBody @jakarta.validation.Valid AddItemRequest request) {
        watchlistService.add(me.userId(), request.symbol());
        return ApiResponse.ok(null);
    }

    /** 관심종목을 해제한다. */
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> remove(@LoginUser SessionUser me, @PathVariable Long id) {
        watchlistService.remove(me.userId(), id);
        return ApiResponse.ok(null);
    }

    /** 등록 요청 본문. 그룹은 서버가 정한다(2.8). */
    public record AddItemRequest(@NotBlank String symbol) {
    }
}
