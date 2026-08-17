package com.example.mijang.stock.client;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.ExternalApiProperties;
import com.example.mijang.config.StockProperties;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Alpaca 종목 마스터·일봉 조회. SecEdgarClient 와 같은 구조다.
 *
 * <p>두 개의 기반 주소를 쓴다 — 종목 마스터는 trading, 봉 데이터는 data 쪽이다.
 * 인증 헤더는 {@code ExternalApiConfig} 가 이미 붙여 두었으므로 여기서는 경로만 신경 쓴다.
 *
 * <p>이 클래스는 <b>응답을 그대로 돌려주기만 한다.</b> 저장·가공은 서비스가 한다.
 * 벤더 응답 모양이 바뀌었을 때 고칠 곳을 한 군데로 모으기 위해서다.
 */
@Slf4j
@Component
public class AlpacaStockClient {

    private final RestClient dataClient;
    private final RestClient tradingClient;
    private final ExternalApiProperties.Alpaca config;
    private final StockProperties stockProps;

    public AlpacaStockClient(@Qualifier("alpacaDataClient") RestClient dataClient,
                             @Qualifier("alpacaTradingClient") RestClient tradingClient,
                             ExternalApiProperties props,
                             StockProperties stockProps) {
        this.dataClient = dataClient;
        this.tradingClient = tradingClient;
        this.config = props.alpaca();
        this.stockProps = stockProps;
    }

    /** 키가 채워져 있는지. 배치가 돌기 전에 먼저 본다. */
    public boolean configured() {
        return config.configured();
    }

    /**
     * 거래 가능한 전 종목. {@code SEARCH-01}~{@code 04} 의 원천이다.
     *
     * <p>{@code status=active}·{@code tradable=true} 로 걸러 받는다. 그러지 않으면
     * 상장폐지·거래정지 종목까지 1만 건 넘게 딸려 온다.
     *
     * <p>한 번에 전부 오는 API 라 페이지 처리가 없다. 응답이 수 MB 라 자주 부르지 않는다 —
     * 하루 한 번이면 충분하다(2.1).
     *
     * @throws BusinessException 벤더가 응답하지 않을 때
     */
    public JsonNode assets() {
        try {
            return tradingClient.get()
                    .uri(uri -> uri.path("/v2/assets")
                            .queryParam("status", "active")
                            .queryParam("asset_class", "us_equity")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Alpaca 종목 마스터 조회 실패", e);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
    }

    /**
     * 일봉. {@code PRICE-05} 의 원천이자 <b>손익 계산의 기준가</b>다.
     *
     * <p>여러 종목을 콤마로 묶어 한 번에 받는다. 종목마다 따로 부르면 1만 종목에
     * 1만 번이 되어 분당 200회 한도를 감당할 수 없다.
     *
     * <p>{@code adjustment=split} 을 준다. 액면분할이 반영되지 않은 원가로 받으면
     * 분할 전후 가격이 뚝 끊겨 차트가 계단처럼 보인다.
     *
     * <p>{@code feed} 를 <b>반드시 명시한다.</b> 생략하면 기본이 SIP 인데 무료 플랜은
     * 최근 SIP 를 거부한다(403). 값은 설정에서 온다 — 유료로 올리면 설정만 바꾼다.
     *
     * @param symbols 콤마로 묶을 티커들. 한 번에 100개 정도가 적당하다
     * @param from    시작 거래일(포함)
     * @param to      끝 거래일(포함)
     */
    public JsonNode dailyBars(List<String> symbols, LocalDate from, LocalDate to) {
        return bars(symbols, "1Day", from.toString(), to.toString());
    }

    /**
     * 시간대를 지정해 봉을 받는다. 차트가 쓴다.
     *
     * <p>{@code dailyBars} 와 같은 경로지만 시간대와 기간 표기가 다르다. 일봉은 날짜로
     * 충분한데 분봉은 시각까지 필요하고, Alpaca 는 둘 다 문자열로 받는다.
     *
     * <p>무료 요금제는 최근 데이터를 IEX 로만 준다(2.4). {@code feed} 를 빼면 SIP 가
     * 기본이라 403 이 난다 — 설정값을 반드시 실어 보낸다.
     *
     * @param timeframe Alpaca 표기 그대로. {@code 1Min}·{@code 5Min}·{@code 1Day}·{@code 1Week}·{@code 1Month}
     * @param start     ISO 문자열. 날짜만이어도 되고 시각까지여도 된다
     */
    public JsonNode bars(List<String> symbols, String timeframe, String start, String end) {
        return bars(symbols, timeframe, start, end, stockProps.getBarFeed());
    }

    /**
     * 피드를 지정해 봉을 받는다.
     *
     * <p>피드마다 보는 범위가 다르다. IEX 는 미국 전체 거래량의 일부만 보는 거래소 하나라
     * <b>장 시간 밖에는 체결이 거의 없다.</b> 실측으로 애프터마켓 4시간에 1봉이었다(2.16).
     * SIP 는 전 거래소를 합친 것이라 프리마켓·애프터마켓이 온전히 들어온다.
     *
     * <p>무료 요금제는 SIP 의 <b>최근</b> 데이터만 막는다. 끝 시각을 16분 이상 앞으로 두면
     * 열린다 — 이것이 장 시간 밖 차트를 그릴 수 있는 유일한 길이다.
     */
    /**
     * 여러 종목의 마지막 체결을 한 번에 받는다.
     *
     * <p>{@code delayed_sip} 피드는 무료 요금제에서 열린다. 15분 늦지만 전 거래소를
     * 합친 값이라 <b>장 시간 밖에도 움직인다</b> — IEX 웹소켓이 조용한 시간에 쓸 유일한 길이다.
     */
    public JsonNode latestTrades(List<String> symbols, String feed) {
        String joined = String.join(",", symbols);
        try {
            return dataClient.get()
                    .uri(uri -> uri.path("/v2/stocks/trades/latest")
                            .queryParam("symbols", joined)
                            .queryParam("feed", feed)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Alpaca 최신 체결 조회 실패 — {}건", symbols.size(), e);
            return null;
        }
    }

    public JsonNode bars(List<String> symbols, String timeframe, String start, String end, String feed) {
        String joined = String.join(",", symbols);
        try {
            return dataClient.get()
                    .uri(uri -> uri.path("/v2/stocks/bars")
                            .queryParam("symbols", joined)
                            .queryParam("timeframe", timeframe)
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("adjustment", "split")
                            .queryParam("feed", feed)
                            .queryParam("limit", 10000)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Alpaca 봉 조회 실패 — {}건 {}", symbols.size(), timeframe, e);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
    }
}
