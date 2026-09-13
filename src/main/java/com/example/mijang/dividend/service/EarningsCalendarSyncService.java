/*
 * EarningsCalendarSyncService — 어닝 캘린더 수집
 *
 * 이 파일이 하는 일
 *   Alpha Vantage 에서 향후 3개월 전체 어닝을 한 번 받아 stock_earnings 에 upsert 한다.
 *   응답에서 사라진 과거 행은 지우지 않는다 — report_date 가 지나면 조회에서 자연히 빠진다.
 *
 *   syncAll() 은 upsert 건수를 반환한다 — BatchLogWriter.run(String, IntSupplier) 이
 *   그 값을 처리 건수로 그대로 기록한다(다른 수집 서비스들과 같은 결).
 */
package com.example.mijang.dividend.service;

import com.example.mijang.dividend.domain.StockEarnings;
import com.example.mijang.dividend.mapper.StockEarningsMapper;
import com.example.mijang.stock.client.AlphaVantageEarningsClient;
import com.example.mijang.stock.client.EarningsRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarningsCalendarSyncService {

    private final AlphaVantageEarningsClient client;
    private final StockEarningsMapper mapper;

    public int syncAll() {
        List<EarningsRow> rows = client.fetchUpcoming();
        int n = 0;
        for (EarningsRow r : rows) {
            mapper.upsert(new StockEarnings(r.symbol(), r.reportDate(),
                    r.fiscalDateEnding(), r.estimateEps(), r.timeOfDay()));
            n++;
        }
        log.info("[실적] 어닝 캘린더 upsert {}건", n);
        return n;
    }
}
