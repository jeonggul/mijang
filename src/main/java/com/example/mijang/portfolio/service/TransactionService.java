package com.example.mijang.portfolio.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.TransactionForm;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.mapper.StockMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매매 기록의 저장·수정·삭제·조회를 담당하는 서비스다. 저장할 때마다 보유 현황을 다시 계산한다. */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionMapper transactionMapper;
    private final PortfolioMapper portfolioMapper;
    private final StockMapper stockMapper;
    private final FxRateService fxRateService;
    private final HoldingService holdingService;
    private final LedgerService ledgerService;
    private final TradingClock tradingClock;

    /** 매매 기록을 저장하고 보유 현황을 재계산한다. 보유 초과 매도면 트랜잭션이 되돌아간다. */
    @Transactional
    public Long create(Long userId, TransactionForm form) {
        String symbol = normalize(form.getSymbol());

        Stock stock = stockMapper.findBySymbol(symbol);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        // 폐지 종목은 새 기록만 막는다
        if (!stock.tradable()) {
            throw new BusinessException(ErrorCode.TX_STOCK_INACTIVE, "symbol");
        }

        LocalDate tradeDate = tradingClock.tradeDate(
                form.getTradedAt().atZone(TradingClock.SERVICE_ZONE).toInstant());
        if (tradeDate.isAfter(tradingClock.today())) {
            throw new BusinessException(ErrorCode.TX_TRADE_DATE_FUTURE, "tradedAt");
        }

        BigDecimal fxRate = resolveFxRate(form.getFxRate(), tradeDate);
        Long portfolioId = defaultPortfolioId(userId);
        BigDecimal fee = form.getFee() == null ? BigDecimal.ZERO : form.getFee();

        transactionMapper.insert(userId, portfolioId, symbol,
                form.getSide(), form.getQuantity(), form.getPrice(), fxRate, fee,
                form.getTradedAt(), tradeDate,
                form.getBuyReason(), form.getTargetPrice(), form.getSentiment());
        Long id = transactionMapper.findLastInsertedId();

        // 재계산이 보유 초과를 잡으면 예외로 트랜잭션이 되돌아가 방금 넣은 기록도 사라진다
        holdingService.recalculate(userId, portfolioId, symbol);
        return id;
    }

    /** 매매 기록 한 건을 조회한다. 없거나 남의 기록이면 예외를 던진다. */
    @Transactional(readOnly = true)
    public TransactionResponse detail(Long userId, Long txId) {
        Transaction tx = transactionMapper.findById(txId, userId);
        if (tx == null) {
            throw new BusinessException(ErrorCode.TX_NOT_FOUND);
        }
        Stock stock = stockMapper.findBySymbol(tx.symbol());
        return new TransactionResponse(tx.id(), tx.symbol(),
                stock == null ? null : stock.name(),
                tx.side(), tx.quantity(), tx.price(), tx.fxRate(), tx.fee(),
                tx.tradedAt(), tx.tradeDate(),
                tx.buyReason(), tx.targetPrice(), tx.sentiment(), null);
    }

    /** 매매 기록 한 건을 고치고 관련 종목의 보유 현황을 재계산한다. 종목이 바뀌면 두 종목 모두 재계산한다. */
    @Transactional
    public void update(Long userId, Long txId, TransactionForm form) {
        Transaction before = transactionMapper.findById(txId, userId);
        if (before == null) {
            throw new BusinessException(ErrorCode.TX_NOT_FOUND);
        }
        String symbol = normalize(form.getSymbol());

        Stock stock = stockMapper.findBySymbol(symbol);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        // 폐지 종목으로 옮기는 것만 막는다. 원래 그 종목이던 기록은 고칠 수 있어야 한다
        if (!stock.tradable() && !symbol.equals(before.symbol())) {
            throw new BusinessException(ErrorCode.TX_STOCK_INACTIVE, "symbol");
        }

        LocalDate tradeDate = tradingClock.tradeDate(
                form.getTradedAt().atZone(TradingClock.SERVICE_ZONE).toInstant());
        if (tradeDate.isAfter(tradingClock.today())) {
            throw new BusinessException(ErrorCode.TX_TRADE_DATE_FUTURE, "tradedAt");
        }

        BigDecimal fxRate = resolveFxRate(form.getFxRate(), tradeDate);
        BigDecimal fee = form.getFee() == null ? BigDecimal.ZERO : form.getFee();

        transactionMapper.update(txId, userId, symbol,
                form.getSide(), form.getQuantity(), form.getPrice(), fxRate, fee,
                form.getTradedAt(), tradeDate,
                form.getBuyReason(), form.getTargetPrice(), form.getSentiment());

        // 종목이 바뀌었으면 옛 종목도 재계산한다
        if (!symbol.equals(before.symbol())) {
            holdingService.recalculate(userId, before.portfolioId(), before.symbol());
        }
        holdingService.recalculate(userId, before.portfolioId(), symbol);
    }

    /** 삭제 표시를 하고 해당 종목의 보유 현황을 재계산한다. */
    @Transactional
    public void delete(Long userId, Long txId) {
        Transaction tx = transactionMapper.findById(txId, userId);
        if (tx == null) {
            throw new BusinessException(ErrorCode.TX_NOT_FOUND);
        }
        transactionMapper.softDelete(txId, userId);
        holdingService.recalculate(userId, tx.portfolioId(), tx.symbol());
    }

    /** 목록을 페이지로 조회한다. 매도 행에는 실현손익을 채워 준다. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> list(Long userId, String symbol, int page, int size) {
        String filter = (symbol == null || symbol.isBlank()) ? null : normalize(symbol);
        List<TransactionResponse> rows = transactionMapper.findByUser(userId, filter, size, page * size);

        return withRealizedPnl(userId, rows);
    }

    /** CSV 내보내기용으로 필터에 맞는 전체 원장을 조회한다. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> exportRows(Long userId, String symbol, String side, Integer year) {
        String symbolFilter = (symbol == null || symbol.isBlank()) ? null : normalize(symbol);
        String sideFilter = normalizeSide(side);
        LocalDate from = null;
        LocalDate toExclusive = null;
        if (year != null) {
            if (year < 1900 || year > 9998) {
                throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "year");
            }
            from = LocalDate.of(year, 1, 1);
            toExclusive = from.plusYears(1);
        }
        return withRealizedPnl(userId, transactionMapper.findForExport(
                userId, symbolFilter, sideFilter, from, toExclusive));
    }

    /** 매도 행에 해당 시점 실현손익을 채운다. */
    private List<TransactionResponse> withRealizedPnl(Long userId, List<TransactionResponse> rows) {
        Set<String> soldSymbols = rows.stream()
                .filter(r -> "SELL".equals(r.side()))
                .map(TransactionResponse::symbol)
                .collect(Collectors.toSet());
        if (soldSymbols.isEmpty()) {
            return rows;
        }

        // 입력은 LedgerService 를 거쳐야 분할 보정이 빠지지 않는다
        Map<Long, BigDecimal> realized = new HashMap<>();
        for (String sold : soldSymbols) {
            realized.putAll(ledgerService.calculationOf(userId, sold).realizedBySellId());
        }
        return rows.stream()
                .map(r -> "SELL".equals(r.side())
                        ? r.withRealizedPnlKrw(realized.get(r.id()))
                        : r)
                .toList();
    }

    private String normalizeSide(String side) {
        if (side == null || side.isBlank() || "ALL".equalsIgnoreCase(side)) {
            return null;
        }
        String normalized = side.trim().toUpperCase(Locale.ROOT);
        if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "side");
        }
        return normalized;
    }

    /** 거래한 적 있는 종목 티커를 조회한다. 전량 매도한 종목도 포함한다. */
    @Transactional(readOnly = true)
    public List<String> tradedSymbols(Long userId) {
        return transactionMapper.findSymbolsByUser(userId);
    }

    /** 필터에 맞는 총 건수를 센다. */
    @Transactional(readOnly = true)
    public long count(Long userId, String symbol) {
        return transactionMapper.countByUser(
                userId, (symbol == null || symbol.isBlank()) ? null : normalize(symbol));
    }

    /** 적용 환율을 정한다. 비어 있으면 거래일 환율을 쓰고, 그마저 없으면 예외를 던진다. */
    private BigDecimal resolveFxRate(BigDecimal given, LocalDate tradeDate) {
        if (given != null) {
            return given;
        }
        BigDecimal resolved = fxRateService.rateOf(tradeDate);
        if (resolved == null) {
            throw new BusinessException(ErrorCode.TX_FX_RATE_REQUIRED, "fxRate");
        }
        return resolved;
    }

    /** 기본 포트폴리오 id 를 찾고, 없으면 만들어 돌려준다. */
    private Long defaultPortfolioId(Long userId) {
        Long id = portfolioMapper.findDefaultId(userId);
        if (id != null) {
            return id;
        }
        portfolioMapper.insertDefault(userId);
        return portfolioMapper.findLastInsertedId();
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
