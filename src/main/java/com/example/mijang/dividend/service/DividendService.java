package com.example.mijang.dividend.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.dividend.domain.Dividend;
import com.example.mijang.dividend.dto.DividendConfirmForm;
import com.example.mijang.dividend.dto.DividendForm;
import com.example.mijang.dividend.dto.DividendResponse;
import com.example.mijang.dividend.dto.DividendSummaryResponse;
import com.example.mijang.dividend.mapper.DividendMapper;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배당 기록의 입력·확정·수정·삭제·요약 규칙을 담당한다. PROFIT-11·12 · SR-016. */
@Service
@RequiredArgsConstructor
public class DividendService {

    /** 원화 금액 자리수. 스키마의 DECIMAL(18,2). */
    private static final int KRW_SCALE = 2;

    private final DividendMapper dividendMapper;
    private final PortfolioMapper portfolioMapper;
    private final FxRateService fxRateService;

    /** 배당 내역을 최근 지급일 순으로 조회한다. */
    @Transactional(readOnly = true)
    public List<DividendResponse> list(Long userId) {
        return dividendMapper.findByUser(userId);
    }

    /** 올해 확정 누적·확정 대기·다음 배당 요약을 만든다. */
    @Transactional(readOnly = true)
    public DividendSummaryResponse summary(Long userId) {
        LocalDate today = LocalDate.now(TradingClock.SERVICE_ZONE);
        BigDecimal yearSum = dividendMapper.sumConfirmedKrwBetween(userId,
                today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31));
        BigDecimal pendingSum = dividendMapper.sumEstimatedKrw(userId);
        DividendResponse next = dividendMapper.findNextUpcoming(userId, today);
        return new DividendSummaryResponse(
                today.getYear(),
                yearSum == null ? BigDecimal.ZERO : yearSum,
                dividendMapper.countEstimated(userId),
                pendingSum == null ? BigDecimal.ZERO : pendingSum,
                next == null ? null : next.symbol(),
                next == null ? null : next.payDate());
    }

    /** 배당을 직접 입력해 바로 확정 상태로 저장한다. 같은 종목·지급일 중복이면 409 를 낸다. */
    @Transactional
    public DividendResponse create(Long userId, DividendForm form) {
        String symbol = form.getSymbol().trim().toUpperCase();
        BigDecimal fxRate = resolveFxRate(form.getFxRate(), form.getPayDate());
        BigDecimal krw = toKrw(form.getNetAmountUsd(), fxRate);

        Dividend row = new Dividend(null, userId, defaultPortfolioId(userId), symbol,
                null, form.getPayDate(), null, null, null, form.getNetAmountUsd(),
                Dividend.DEFAULT_WITHHOLDING, fxRate, krw, "CONFIRMED", "MANUAL", null);
        try {
            dividendMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.DIVIDEND_DUPLICATED, "payDate");
        }
        Long id = dividendMapper.findLastInsertedId();
        return new DividendResponse(id, symbol, null, form.getPayDate(), null, null,
                form.getNetAmountUsd(), fxRate, krw, "CONFIRMED", "MANUAL");
    }

    /** 예상 배당을 확정한다. 이미 확정이면 409 를 내며, 조건부 갱신이라 두 번 눌러도 한 번만 성공한다. */
    @Transactional
    public DividendResponse confirm(Long userId, Long id, DividendConfirmForm form) {
        Dividend found = require(userId, id);
        if (found.confirmed()) {
            throw new BusinessException(ErrorCode.DIVIDEND_ALREADY_CONFIRMED);
        }
        LocalDate payDate = form.getPayDate() != null ? form.getPayDate() : found.payDate();
        BigDecimal fxRate = form.getFxRate() != null ? form.getFxRate()
                : resolveFxRate(null, payDate);
        BigDecimal krw = toKrw(form.getNetAmountUsd(), fxRate);

        int changed = dividendMapper.confirm(id, userId,
                form.getNetAmountUsd(), fxRate, krw, payDate);
        if (changed == 0) {
            // 조회와 갱신 사이에 다른 요청이 먼저 확정한 경우다
            throw new BusinessException(ErrorCode.DIVIDEND_ALREADY_CONFIRMED);
        }
        return new DividendResponse(id, found.symbol(), found.exDate(), payDate,
                found.amountPerShare(), found.quantityAtExDate(),
                form.getNetAmountUsd(), fxRate, krw, "CONFIRMED", found.source());
    }

    /** 세후 금액·환율·지급일을 고치고 원화 환산을 다시 구한다. */
    @Transactional
    public DividendResponse update(Long userId, Long id, DividendForm form) {
        Dividend found = require(userId, id);
        BigDecimal fxRate = resolveFxRate(form.getFxRate(), form.getPayDate());
        BigDecimal krw = toKrw(form.getNetAmountUsd(), fxRate);
        dividendMapper.update(id, userId, form.getNetAmountUsd(), fxRate, krw, form.getPayDate());
        return new DividendResponse(id, found.symbol(), found.exDate(), form.getPayDate(),
                found.amountPerShare(), found.quantityAtExDate(),
                form.getNetAmountUsd(), fxRate, krw, found.status(), found.source());
    }

    /** 배당에 삭제 표시를 한다. 없는(남의) 기록이면 404 를 낸다. */
    @Transactional
    public void delete(Long userId, Long id) {
        if (dividendMapper.softDelete(id, userId) == 0) {
            throw new BusinessException(ErrorCode.DIVIDEND_NOT_FOUND);
        }
    }

    private Dividend require(Long userId, Long id) {
        Dividend found = dividendMapper.findById(id, userId);
        if (found == null) {
            throw new BusinessException(ErrorCode.DIVIDEND_NOT_FOUND);
        }
        return found;
    }

    /** 환율을 정한다. 비워 냈으면 지급일 환율을 쓰고, 그것도 없으면 예외를 낸다. */
    private BigDecimal resolveFxRate(BigDecimal given, LocalDate payDate) {
        if (given != null) {
            return given;
        }
        BigDecimal resolved = fxRateService.rateOf(payDate);
        if (resolved == null) {
            throw new BusinessException(ErrorCode.FX_RATE_NOT_FOUND, "fxRate");
        }
        return resolved;
    }

    private BigDecimal toKrw(BigDecimal usd, BigDecimal fxRate) {
        return usd.multiply(fxRate).setScale(KRW_SCALE, RoundingMode.HALF_UP);
    }

    /** 기본 포트폴리오 id 를 얻고, 없으면 만든다. */
    private Long defaultPortfolioId(Long userId) {
        Long id = portfolioMapper.findDefaultId(userId);
        if (id != null) {
            return id;
        }
        portfolioMapper.insertDefault(userId);
        return portfolioMapper.findDefaultId(userId);
    }
}
