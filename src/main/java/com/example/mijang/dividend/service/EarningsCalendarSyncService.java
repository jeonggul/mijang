package com.example.mijang.dividend.service;

import com.example.mijang.dividend.domain.StockEarnings;
import com.example.mijang.dividend.mapper.StockEarningsMapper;
import com.example.mijang.stock.client.AlphaVantageEarningsClient;
import com.example.mijang.stock.client.EarningsRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Alpha Vantage 에서 향후 3개월 어닝 캘린더를 받아 stock_earnings 에 upsert 한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarningsCalendarSyncService {

    private final AlphaVantageEarningsClient client;
    private final StockEarningsMapper mapper;

    /** 전체 어닝 캘린더를 수집하고 upsert 건수를 반환한다. */
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
