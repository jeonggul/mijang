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

/** BLS 발표 일정(iCalendar)을 받아 직접 파싱한다. INFO-07. */
@Slf4j
@Component
public class BlsScheduleClient {

    /** 시장이 크게 움직이는 발표명. BLS 의 CATEGORIES 는 전 항목이 같아 발표명으로 가른다. */
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

    /** RFC 5545 폴딩(줄 이어 붙이기)을 먼저 펴 둔다. */
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

    /** 시각을 파싱한다. 종일 일정은 null 이다. */
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
