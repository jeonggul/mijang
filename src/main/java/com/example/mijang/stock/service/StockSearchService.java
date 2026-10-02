package com.example.mijang.stock.service;

import com.example.mijang.config.StockProperties;
import com.example.mijang.common.response.PageResponse;
import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.stock.mapper.StockMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종목 검색을 담당한다. 벤더를 부르지 않고 배치가 채운 stocks 안에서만 찾는다. */
@Service
@RequiredArgsConstructor
public class StockSearchService {

    private final StockMapper stockMapper;
    private final StockProperties props;

    /** 한 번에 내주는 최대 건수. */
    private static final int MAX_PAGE_SIZE = 100;

    /** 티커·종목명 전방 일치 검색을 한다. 빈 검색어는 DB 를 부르지 않고 빈 목록을 준다. */
    @Transactional(readOnly = true)
    public List<StockSearchResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String keyword = q.trim().toUpperCase(Locale.ROOT);
        return stockMapper.searchByPrefix(keyword, props.getSearchLimit());
    }

    /** 시장·자산군별 목록을 페이지로 돌려준다. 필터가 비면 전체로 보고 크기 상한을 건다. */
    @Transactional(readOnly = true)
    public PageResponse<StockSearchResponse> list(String exchange, String assetClass, int page, int size) {
        String ex = blankToNull(exchange);
        String cls = blankToNull(assetClass);

        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // long 으로 곱한다. int 곱은 page 가 크면 넘쳐 음수 OFFSET 이 SQL 로 내려간다
        long offset = (long) Math.max(page, 0) * limit;

        long total = stockMapper.countByFilter(ex, cls);
        if (offset >= total) {
            return PageResponse.empty(page, limit);
        }
        return PageResponse.of(stockMapper.findByFilter(ex, cls, (int) offset, limit), page, limit, total);
    }

    /** 빈 문자열과 null 을 같게 취급한다. XML 의 {@code <if>} 가 null 만 보기 때문이다. */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
