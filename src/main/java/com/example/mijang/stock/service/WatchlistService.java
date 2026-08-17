package com.example.mijang.stock.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.WatchlistItemResponse;
import com.example.mijang.stock.mapper.StockMapper;
import com.example.mijang.stock.mapper.WatchlistMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심종목. 개발명세서(API) WATCH-01·WATCH-02
 */
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistMapper watchlistMapper;
    private final StockMapper stockMapper;

    /** 목록 조회. 시세가 붙어 나온다. */
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> list(Long userId) {
        return watchlistMapper.findByUser(userId);
    }

    /**
     * 등록.
     *
     * <p>없는 종목을 담지 못하게 먼저 확인한다. 확인하지 않으면 오타로 만든 티커가
     * 목록에 남고, 조인이 어긋나 이름이 빈 줄이 생긴다.
     *
     * <p>상장폐지 종목은 담을 수 없다. 이미 담은 것은 그대로 두지만(과거 기록과 같은 이유)
     * 새로 담을 이유는 없다.
     *
     * <p>기본 그룹이 없으면 여기서 만든다(2.8).
     *
     * @throws BusinessException 없는 종목(404)·거래 불가 종목(422)
     */
    @Transactional
    public void add(Long userId, String symbol) {
        String key = normalize(symbol);
        Stock stock = stockMapper.findBySymbol(key);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        if (!stock.tradable()) {
            throw new BusinessException(ErrorCode.TX_STOCK_INACTIVE, "symbol");
        }
        watchlistMapper.insertItem(defaultGroupId(userId), userId, key);
    }

    /**
     * 해제.
     *
     * <p>지워진 행이 0 이면 없거나 남의 것이다. 둘을 구분하지 않는다 —
     * 나누면 "이 id 가 존재하는가"를 확인하는 통로가 된다.
     *
     * @throws BusinessException 지울 것이 없을 때(404)
     */
    @Transactional
    public void remove(Long userId, Long itemId) {
        if (watchlistMapper.deleteItem(itemId, userId) == 0) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
        }
    }

    /** 상세 화면의 별표 상태. 비로그인이면 항상 false 다. */
    @Transactional(readOnly = true)
    public boolean contains(Long userId, String symbol) {
        return userId != null && watchlistMapper.existsByUserAndSymbol(userId, normalize(symbol));
    }

    /** 기본 그룹을 찾고, 없으면 만들어 그 id 를 돌려준다. */
    private Long defaultGroupId(Long userId) {
        Long groupId = watchlistMapper.findDefaultGroupId(userId);
        if (groupId != null) {
            return groupId;
        }
        watchlistMapper.insertDefaultGroup(userId);
        return watchlistMapper.findLastInsertedGroupId();
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
