package com.example.mijang.news.client;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.ExternalApiProperties;
import com.example.mijang.news.dto.EconomicEventResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * BLS 발표 일정(iCalendar) 조회·파싱.
 *
 * <p>개발명세서(MVC) · 뉴스·정보 · client — 기능명세서 INFO-07
 *
 * <p>BLS 는 발표 일정을 JSON API 가 아니라 iCalendar 파일로 준다. 형식이 단순해서
 * (한 파일에 VEVENT 300여 개, 접힘 없음) 라이브러리를 붙이지 않고 직접 읽는다.
 */
@Slf4j
@Component
public class BlsScheduleClient {

    /**
     * 시장이 크게 움직이는 발표. BLS 파일의 CATEGORIES 필드는 전 항목이 {@code IMPORTANT} 로
     * 똑같이 찍혀 나와서 구분에 쓸 수 없다. 그래서 발표명으로 가른다.
     */
    private static final Set<String> HIGH_IMPACT = Set.of(
            "Consumer Price Index",
            "Employment Situation",
            "Producer Price Index",
            "Job Openings and Labor Turnover Survey",
            "Employment Cost Index",
            "Real Earnings");

    private static final DateTimeFormatter ICS_DATETIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient client;
    private final ExternalApiProperties.Bls config;

    public BlsScheduleClient(@Qualifier("blsClient") RestClient client, ExternalApiProperties props) {
        this.client = client;
        this.config = props.bls();
    }

    /** 일정 전체를 받아 파싱한다. 과거·미래가 모두 들어 있다. */
    public List<EconomicEventResponse> fetchAll() {
        String ics;
        try {
            ics = client.get().uri(config.scheduleUrl()).retrieve().body(String.class);
        } catch (RestClientException e) {
            log.error("BLS 일정 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
        if (ics == null || ics.isBlank()) {
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
        return parse(ics);
    }

    private List<EconomicEventResponse> parse(String ics) {
        List<EconomicEventResponse> events = new ArrayList<>();
        String summary = null;
        LocalDate date = null;
        LocalTime time = null;

        for (String line : unfold(ics).split("\n")) {
            String trimmed = line.strip();
            if (trimmed.equals("BEGIN:VEVENT")) {
                summary = null;
                date = null;
                time = null;
            } else if (trimmed.startsWith("SUMMARY:")) {
                summary = unescape(trimmed.substring("SUMMARY:".length()).strip());
            } else if (trimmed.startsWith("DTSTART")) {
                int colon = trimmed.indexOf(':');
                if (colon >= 0) {
                    String value = trimmed.substring(colon + 1).strip();
                    date = parseDate(value);
                    time = parseTime(value);
                }
            } else if (trimmed.equals("END:VEVENT") && summary != null && date != null) {
                events.add(new EconomicEventResponse(
                        date, time, summary, "BLS", importanceOf(summary), null));
            }
        }
        if (events.isEmpty()) {
            log.error("BLS 일정을 한 건도 파싱하지 못했다. 파일 형식이 바뀌었을 수 있다.");
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
        log.info("BLS 발표 일정 {}건 파싱", events.size());
        return events;
    }

    /**
     * iCalendar 는 긴 줄을 다음 줄 맨 앞 공백으로 이어 붙인다(RFC 5545 폴딩). 현재 BLS 파일에는
     * 접힌 줄이 없지만 규격상 언제든 생길 수 있어 먼저 펴 둔다.
     */
    private static String unfold(String ics) {
        return ics.replace("\r\n", "\n").replaceAll("\n[ \t]", "");
    }

    private static String unescape(String value) {
        return value.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").strip();
    }

    private static LocalDate parseDate(String value) {
        try {
            if (value.length() >= 15 && value.charAt(8) == 'T') {
                return LocalDateTime.parse(value.substring(0, 15), ICS_DATETIME).toLocalDate();
            }
            return LocalDate.parse(value.substring(0, 8), ICS_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /** 날짜만 있는 종일 일정도 있을 수 있어 시각은 없을 수 있다. */
    private static LocalTime parseTime(String value) {
        try {
            if (value.length() >= 15 && value.charAt(8) == 'T') {
                return LocalDateTime.parse(value.substring(0, 15), ICS_DATETIME).toLocalTime();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String importanceOf(String summary) {
        return HIGH_IMPACT.contains(summary)
                ? EconomicEventResponse.IMPORTANCE_HIGH
                : EconomicEventResponse.IMPORTANCE_NORMAL;
    }
}
