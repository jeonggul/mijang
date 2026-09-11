/*
 * AlphaVantageEarningsClient — 실적 발표 일정을 CSV 로 받는다
 *
 * 이 파일이 하는 일
 *   EARNINGS_CALENDAR 한 번 호출로 향후 3개월 전체 어닝을 CSV 로 받아 파싱한다.
 *   심볼별로 부르지 않는다 — 통짜 한 방이라 무료 한도(25/일) 안에서 하루 1회면 된다.
 *
 *   응답이 CSV 가 아니면(유료 안내 JSON·빈 본문) 빈 리스트로 접는다. 배치가 다음 날
 *   다시 돌고, 그 사이 화면은 어제 수집분을 그대로 보여 준다.
 */
package com.example.mijang.stock.client;

import com.example.mijang.config.ExternalApiProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AlphaVantageEarningsClient {

    private static final String EXPECTED_HEADER_START = "symbol,name,reportDate";

    private final RestClient client;
    private final ExternalApiProperties.Alphavantage config;

    public AlphaVantageEarningsClient(RestClient alphaVantageClient, ExternalApiProperties props) {
        this.client = alphaVantageClient;
        this.config = props.alphavantage();
    }

    /** 향후 3개월 전체 어닝. 실패·빈 응답이면 빈 리스트. */
    public List<EarningsRow> fetchUpcoming() {
        if (!config.configured()) {
            log.warn("[실적] Alpha Vantage 키가 없어 수집을 건너뜀");
            return List.of();
        }
        try {
            String body = client.get()
                    .uri(uri -> uri.path("/query")
                            .queryParam("function", "EARNINGS_CALENDAR")
                            .queryParam("horizon", "3month")
                            .queryParam("apikey", config.apiKey())
                            .build())
                    .retrieve()
                    .body(String.class);
            return parseCsv(body);
        } catch (Exception e) {
            log.warn("[실적] Alpha Vantage 조회 실패", e);
            return List.of();
        }
    }

    /**
     * CSV 본문을 파싱한다. 순수 함수라 테스트가 이걸 고정한다.
     *
     * <p>헤더가 {@code symbol,name,reportDate…} 로 시작하지 않으면(유료 안내 JSON·빈 본문)
     * 빈 리스트다. reportDate 가 비거나 깨진 행은 건너뛴다 — 캘린더의 핵심 값이라 없으면 쓸모없다.
     */
    public static List<EarningsRow> parseCsv(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String[] lines = body.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].startsWith(EXPECTED_HEADER_START)) {
            return List.of();
        }
        List<EarningsRow> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] c = lines[i].split(",", -1);
            if (c.length < 7) {
                continue;
            }
            LocalDate report = parseDate(c[2]);
            if (report == null) {
                continue;   // 발표일 없으면 캘린더에 못 얹는다
            }
            out.add(new EarningsRow(
                    c[0].trim(),
                    report,
                    parseDate(c[3]),
                    parseDecimal(c[4]),
                    blankToNull(c[6])));
        }
        return out;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
