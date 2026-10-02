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

/** 관심종목 등록·해제·조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistMapper watchlistMapper;
    private final StockMapper stockMapper;

    /** 관심종목 목록을 시세와 함께 돌려준다. */
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> list(Long userId) {
        return watchlistMapper.findByUser(userId);
    }

    /** 관심종목에 등록한다. 없는 종목·거래 불가 종목은 거절하고, 기본 그룹이 없으면 만든다. */
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

    /** 관심종목에서 해제한다. 없거나 남의 것이면 구분 없이 404 로 본다. */
    @Transactional
    public void remove(Long userId, Long itemId) {
        if (watchlistMapper.deleteItem(itemId, userId) == 0) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
        }
    }

    /** 관심종목에 담겨 있는지 돌려준다. 비로그인이면 항상 false 다. */
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
