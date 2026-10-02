package com.example.mijang.fx.service;

import com.example.mijang.fx.client.FxRateClient;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 벤더에서 현재 환율을 받아 {@code fx_quotes} 에 쌓는다. {@code GLOBAL-01} */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxCollectService {

    private final FxRateClient client;
    private final FxQuoteMapper quoteMapper;

    /** 현재 환율을 받아 저장한다. 값이 그대로면 넣지 않고 넘어간다. */
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
