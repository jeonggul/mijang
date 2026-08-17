/*
 * FxConfirmService — 그날 환율을 확정하는 곳
 *
 * 이 파일이 하는 일
 *   하루가 끝나면 그날 마지막 시세를 fx_rates 에 확정으로 옮긴다.
 *
 *   왜 옮기는가 — fx_quotes 는 계속 늘어나는 이력이라 "그날 값" 이 하나로 정해지지 않는다.
 *   환차손익과 일별 스냅샷은 하나로 정해진 값이 필요하다. 그것도 나중에 바뀌지 않는 값이어야
 *   한다. 스냅샷 배치가 몇 시에 도느냐에 따라 그날 손익이 달라지면 안 되기 때문이다.
 *
 *   그날 시세가 하나도 없으면 직전 확정값을 복사하고 복사했다는 사실을 남긴다.
 *   미국 장은 한국 주말에도 열리므로(금요일 밤) 반드시 생기는 일이다.
 */
package com.example.mijang.fx.service;

import com.example.mijang.config.FxProperties;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 확정 환율 만들기. {@code GLOBAL-01} */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxConfirmService {

    private static final String USD = "USD";

    private final FxQuoteMapper quoteMapper;
    private final FxRateMapper rateMapper;
    private final FxProperties props;

    /**
     * 그날 환율을 확정한다.
     *
     * <p>이미 확정돼 있으면 손대지 않는다. 한 번 정한 값을 나중에 바꾸면 그것으로 계산해 둔
     * 손익과 어긋난다.
     *
     * @return 확정된 값. 시세도 없고 거슬러 올라갈 값도 없으면 비어 있음
     */
    @Transactional
    public Optional<FxRate> confirm(LocalDate date) {
        FxRate already = rateMapper.findByDate(date);
        if (already != null) {
            return Optional.of(already);
        }

        FxQuote last = quoteMapper.findLastOfDate(USD, date);
        if (last != null) {
            FxRate confirmed = FxRate.confirmed(date, last.basePrice());
            rateMapper.insertIgnore(confirmed);
            log.info("[환율] {} 확정 {}", date, confirmed.usdKrw());
            return Optional.of(confirmed);
        }

        /* 그날 시세가 하나도 없다. 직전 확정값을 복사한다.
           대체했다는 사실을 행에 박아 둔다 — 조회할 때마다 거슬러 찾으면 같은 날짜를 물어도
           결과가 달라질 수 있고, 그러면 과거에 계산한 손익과 어긋난다(2.6) */
        FxRate previous = rateMapper.findLatestBefore(date, props.getSubstituteLookbackDays());
        if (previous == null) {
            log.warn("[환율] {} 확정 실패 — 시세도 직전 값도 없다", date);
            return Optional.empty();
        }
        FxRate substitute = FxRate.substitute(date, previous.usdKrw(), previous.rateDate());
        rateMapper.insertIgnore(substitute);
        log.info("[환율] {} 확정 {} (← {} 복사)", date, substitute.usdKrw(), previous.rateDate());
        return Optional.of(substitute);
    }
}
