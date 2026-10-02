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

/** Alpha Vantage EARNINGS_CALENDAR 를 CSV 로 받아 향후 3개월 어닝 일정을 파싱하는 클라이언트다. */
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

    /** 향후 3개월 전체 어닝을 받는다. 실패·빈 응답이면 빈 리스트를 돌려준다. */
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

    /** CSV 본문을 파싱한다. 헤더가 안 맞으면 빈 리스트, reportDate 가 깨진 행은 건너뛴다. */
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
