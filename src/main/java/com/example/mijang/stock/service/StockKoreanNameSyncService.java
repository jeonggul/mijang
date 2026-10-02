/*
 * StockKoreanNameSyncService — 한글 종목명을 채우는 곳
 *
 * 이 파일이 하는 일
 *   Wikidata 에서 티커별 한글 이름을 받아 stocks.name_ko 에 넣는다.
 *
 *   검색이 영어로만 되면 한국에서 쓰기 어렵다. "애플" 로도 AAPL 이 나와야 한다.
 *
 *   받은 것을 넣기만 하고 <b>지우지는 않는다.</b> Wikidata 가 어느 날 한 종목을 빠뜨려도
 *   이미 있던 한글명이 사라지면 안 된다 — 사용자 입장에서는 어제 되던 검색이 오늘 안 되는 것이다.
 *
 *   개별주는 대부분 이름이 있지만 ETF 는 거의 없다. Wikidata 가 회사는 잘 다루고
 *   펀드는 잘 다루지 않아서다. 없으면 NULL 로 두고 화면이 영문명을 쓴다.
 */
package com.example.mijang.stock.service;

import com.example.mijang.stock.client.WikidataClient;
import com.example.mijang.stock.mapper.StockMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockKoreanNameSyncService {

    private final WikidataClient wikidataClient;
    private final StockMapper stockMapper;

    /**
     * 전부 받아 채운다.
     *
     * <p>1,000건이 채 안 되고 하루 한 번만 도므로 나눠 넣지 않는다.
     *
     * <p>한 건씩 갱신한다. 우리 목록에 없는 티커는 0행이 바뀌고 그냥 넘어간다 —
     * Wikidata 에는 상장폐지됐거나 우리가 안 다루는 종목도 들어 있다.
     *
     * @return 실제로 채워진 종목 수
     */
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
