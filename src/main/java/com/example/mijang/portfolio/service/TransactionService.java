/*
 * TransactionService — 매매 기록의 본체
 *
 * 이 파일이 하는 일
 *   거래를 저장하고 지우고 목록으로 내준다.
 *   저장할 때마다 그 종목의 보유 현황을 곧바로 다시 계산한다 — 나중에 몰아서 하면
 *   그 사이에 사용자가 보는 숫자가 틀리기 때문이다.
 *   환율을 비워 보내면 그날 환율을 채워 넣고, 매도인데 가진 것보다 많으면 거절한다.
 */
package com.example.mijang.portfolio.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.domain.Holding;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.TransactionForm;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.mapper.StockMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매매 기록. 개발명세서(API) ACCOUNT-01·02·03·06
 *
 * <p>저장할 때마다 해당 종목의 보유 현황을 다시 계산한다(2.2).
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionMapper transactionMapper;
    private final PortfolioMapper portfolioMapper;
    private final StockMapper stockMapper;
    private final FxRateService fxRateService;
    private final HoldingService holdingService;
    private final TradingClock tradingClock;

    /**
     * 매매 기록 저장.
     *
     * <p>검사 순서에 이유가 있다 — <b>DB 를 건드리기 전에 값부터 본다.</b>
     *
     * <ol>
     *   <li>종목이 있는가 · 거래 가능한가 (2.6)</li>
     *   <li>거래일이 미래가 아닌가 (2.6)</li>
     *   <li>환율이 있는가 — 없으면 거래일 환율로 채운다 (2.7)</li>
     *   <li>저장 후 재계산 — 수량이 음수가 되면 되돌린다 (2.5)</li>
     * </ol>
     *
     * <p>마지막 검사가 저장 <b>뒤</b>인 것이 중요하다. 과거 날짜로 끼워 넣는 경우는
     * 저장해 보고 전체를 다시 계산해야만 알 수 있다. 예외를 던지면 트랜잭션이
     * 통째로 되돌아가므로 잘못된 기록이 남지 않는다.
     *
     * @return 생성된 기록 id
     */
    @Transactional
    public Long create(Long userId, TransactionForm form) {
        String symbol = normalize(form.getSymbol());

        Stock stock = stockMapper.findBySymbol(symbol);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        // 이미 기록된 폐지 종목은 건드리지 않는다. 새로 넣는 것만 막는다(2.6)
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

        // 재계산 결과가 음수면 이 기록을 넣을 수 없다는 뜻이다. 예외로 전체를 되돌린다(2.5)
        Holding holding = holdingService.recalculate(userId, portfolioId, symbol);
        if (holding.quantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING, "quantity");
        }
        return id;
    }

    /**
     * 삭제. 지우지 않고 표시만 하고(2.9) 해당 종목을 다시 계산한다.
     *
     * <p>삭제 <b>전에</b> 종목을 알아내야 한다. 지운 뒤에는 어느 종목을 다시 계산해야
     * 하는지 알 수 없다.
     *
     * @throws BusinessException 없거나 남의 기록일 때(404)
     */
    @Transactional
    public void delete(Long userId, Long txId) {
        Transaction tx = transactionMapper.findById(txId, userId);
        if (tx == null) {
            throw new BusinessException(ErrorCode.TX_NOT_FOUND);
        }
        transactionMapper.softDelete(txId, userId);
        holdingService.recalculate(userId, tx.portfolioId(), tx.symbol());
    }

    /** 목록 조회. {@code symbol} 을 주면 그 종목만 본다. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> list(Long userId, String symbol, int page, int size) {
        String filter = (symbol == null || symbol.isBlank()) ? null : normalize(symbol);
        return transactionMapper.findByUser(userId, filter, size, page * size);
    }

    /** 총 건수. 페이징에 쓴다. */
    @Transactional(readOnly = true)
    public long count(Long userId, String symbol) {
        return transactionMapper.countByUser(
                userId, (symbol == null || symbol.isBlank()) ? null : normalize(symbol));
    }

    /**
     * 적용 환율을 정한다.
     *
     * <p>사용자가 적었으면 그 값을 쓴다. 안 적었으면 거래일 환율을 가져온다(2.7).
     *
     * <p><b>환율이 아예 없으면 저장을 막는다.</b> 환율 없이 저장하면 그 기록만 손익 계산에서
     * 빠져 총액이 조용히 틀린다. 눈에 보이는 오류가 낫다.
     *
     * @throws BusinessException 거래일 환율을 구할 수 없을 때(400)
     */
    private BigDecimal resolveFxRate(BigDecimal given, LocalDate tradeDate) {
        if (given != null) {
            return given;
        }
        BigDecimal resolved = fxRateService.rateOf(tradeDate);
        if (resolved == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "fxRate");
        }
        return resolved;
    }

    /** 기본 포트폴리오를 찾고, 없으면 만들어 id 를 돌려준다(2.8). */
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
