package com.example.mijang.stock.service;

import com.example.mijang.stock.client.WikidataClient;
import com.example.mijang.stock.mapper.StockMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Wikidata 의 한글 종목명을 stocks.name_ko 에 채운다. 넣기만 하고 기존 한글명을 지우지는 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockKoreanNameSyncService {

    private final WikidataClient wikidataClient;
    private final StockMapper stockMapper;

    /** 한글명 전체를 받아 반영하고 실제로 채워진 종목 수를 돌려준다. */
    @Transactional
    public int syncAll() {
        Map<String, String> names = wikidataClient.koreanNames();

        int updated = 0;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            updated += stockMapper.updateNameKo(entry.getKey(), entry.getValue());
        }

        int total = stockMapper.countWithNameKo();
        log.info("[한글명] {}건 중 {}건 반영 — 지금 한글명이 있는 종목 {}건",
                names.size(), updated, total);
        return updated;
    }
}
