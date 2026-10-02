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

/**
 * 종목 검색. 개발명세서(API) SEARCH-01~04
 *
 * <p>벤더를 부르지 않는다. 배치가 채워 둔 {@code stocks} 안에서만 찾는다(2.1).
 */
@Service
@RequiredArgsConstructor
public class StockSearchService {

    private final StockMapper stockMapper;
    private final StockProperties props;

    /** 한 번에 내주는 최대 건수. 화면이 감당할 수 있는 크기다 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 티커·종목명 전방 일치 검색.
     *
     * <p>빈 검색어에 <b>DB 를 부르지 않는다.</b> 자동완성은 글자를 지울 때도 호출되는데,
     * 빈 문자열로 조회하면 전방 일치 조건이 사라져 전 종목이 걸린다.
     *
     * <p>티커는 대문자로 저장돼 있으므로 입력을 대문자로 맞춘다. 종목명은 원문 그대로
     * 비교해도 MySQL 의 기본 콜레이션이 대소문자를 구분하지 않는다.
     *
     * @return 최대 {@code mijang.stock.search-limit} 건. 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<StockSearchResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String keyword = q.trim().toUpperCase(Locale.ROOT);
        return stockMapper.searchByPrefix(keyword, props.getSearchLimit());
    }

    /**
     * 시장·자산군별 목록. {@code SEARCH-03}·{@code SEARCH-04}
     *
     * <p>둘 다 비면 활성 종목 전체가 되므로 상한을 반드시 건다.
     *
     * <p>페이지로 나눠 준다. 화면이 "더보기" 로 이어 받는다.
     *
     * @param exchange   NASDAQ·NYSE 등. 비어 있으면 전체
     * @param assetClass STOCK 또는 ETF. 비어 있으면 전체
     * @param page       0 부터
     * @param size       한 번에 받을 건수. 100 을 넘기면 100 으로 줄인다
     */
    @Transactional(readOnly = true)
    public PageResponse<StockSearchResponse> list(String exchange, String assetClass, int page, int size) {
        String ex = blankToNull(exchange);
        String cls = blankToNull(assetClass);

        /* 한 번에 너무 많이 내주지 않는다. 13,000 종목을 통째로 그리면 화면이 멎는다 */
        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        /* long 으로 곱한다. int 로 곱하면 page 가 클 때 넘쳐서 음수가 된다 —
           page=200000000·size=20 이면 40억이라 int 를 벗어나고, 음수가 된 offset 은
           아래 "범위 밖" 검사도 통과해 음수 OFFSET 으로 SQL 에 내려가 500 이 난다.
           page 는 로그인 없이 아무 값이나 넣을 수 있는 자리다 */
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
