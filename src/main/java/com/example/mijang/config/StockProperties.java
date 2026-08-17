package com.example.mijang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 종목 범위 설정. application.properties 의 mijang.stock.* 를 받는다.
 */
@ConfigurationProperties(prefix = "mijang.stock")
public class StockProperties {

    /** 검색 결과 최대 건수. 자동완성 드롭다운이 감당할 만한 크기로 자른다. */
    private int searchLimit = 20;

    /** 일봉 수집 시 한 번에 요청할 종목 수. Alpaca 는 심볼을 콤마로 묶어 받는다. */
    private int barBatchSize = 100;

    /** 52주 최고·최저를 계산할 기간(일). */
    private int highLowDays = 365;

    /**
     * 일봉을 받아올 피드. {@code iex} 또는 {@code sip}.
     *
     * <p><b>무료 플랜은 최근 SIP 데이터를 주지 않는다</b> — 403 과 함께
     * "subscription does not permit querying recent SIP data" 가 온다.
     * 파라미터를 생략해도 기본이 SIP 라 같은 오류가 난다. 그래서 기본을 iex 로 둔다.
     *
     * <p>과거 데이터(1년 전 등)는 무료로도 SIP 가 열린다. 유료 플랜으로 올리면
     * 이 값만 sip 으로 바꾸면 된다.
     */
    private String barFeed = "iex";

    // 아래는 스프링이 값을 넣고 꺼내기 위한 접근자다.

    /** 검색 상한을 읽는다. */
    public int getSearchLimit() { return searchLimit; }
    /** mijang.stock.search-limit 주입. */
    public void setSearchLimit(int searchLimit) { this.searchLimit = searchLimit; }

    /** 일봉 수집 묶음 크기를 읽는다. */
    public int getBarBatchSize() { return barBatchSize; }
    /** mijang.stock.bar-batch-size 주입. */
    public void setBarBatchSize(int barBatchSize) { this.barBatchSize = barBatchSize; }

    /** 최고·최저 집계 기간을 읽는다. */
    public int getHighLowDays() { return highLowDays; }
    /** mijang.stock.high-low-days 주입. */
    public void setHighLowDays(int highLowDays) { this.highLowDays = highLowDays; }

    /** 일봉 피드를 읽는다. */
    public String getBarFeed() { return barFeed; }
    /** mijang.stock.bar-feed 주입. iex 또는 sip. */
    public void setBarFeed(String barFeed) { this.barFeed = barFeed; }
}
