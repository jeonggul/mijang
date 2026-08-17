/*
 * FxCollectService — 환율을 받아 쌓는 곳
 *
 * 이 파일이 하는 일
 *   벤더에서 현재 환율을 받아 fx_quotes 에 넣는다.
 *
 *   이것이 벤더를 부르는 유일한 자리다. 화면은 이 표만 읽는다.
 *   방문자가 몰려도 벤더 쪽에서 보이는 호출량은 1시간에 한 번으로 고정이다 —
 *   무료 한도가 월 1,000회뿐이라, 이 격리가 없으면 사람이 조금만 몰려도
 *   그 달 남은 날을 환율 없이 보내게 된다.
 */
package com.example.mijang.fx.service;

import com.example.mijang.fx.client.FxRateClient;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 환율 수집. {@code GLOBAL-01} */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxCollectService {

    private final FxRateClient client;
    private final FxQuoteMapper quoteMapper;

    /**
     * 지금 환율을 받아 넣는다.
     *
     * <p>값이 안 바뀌었으면 아무 일도 일어나지 않는다 — 벤더가 준 시각에 유니크가 걸려 있어
     * {@code INSERT IGNORE} 가 그대로 넘어간다(2.3). 그래서 <b>넣은 행이 0 인 것은 정상이다.</b>
     * 벤더 갱신이 1시간 주기라 폴링이 그보다 촘촘하면 늘 0 이 된다.
     *
     * @return 실제로 저장된 시세. 못 받았으면 비어 있음
     */
    @Transactional
    public Optional<FxQuote> collect() {
        Optional<FxQuote> quote = client.latest();
        if (quote.isEmpty()) {
            return Optional.empty();
        }
        FxQuote q = quote.get();
        int inserted = quoteMapper.insertIgnore(q);
        if (inserted > 0) {
            log.info("[환율] {} {} (기준 {})", q.currencyCode(), q.basePrice(), q.quotedAt());
        } else {
            log.debug("[환율] 값이 그대로다 — {}", q.quotedAt());
        }
        return quote;
    }
}
