package com.example.mijang.news.service;

import com.example.mijang.config.ExternalApiProperties;
import com.example.mijang.news.client.BlsScheduleClient;
import com.example.mijang.news.dto.EconomicEventResponse;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 경제 캘린더.
 *
 * <p>개발명세서(MVC) · 뉴스·정보 · service — 기능명세서 INFO-07
 *
 * <p>출처가 둘이다. 지표 발표는 <b>BLS</b> 가 iCalendar 로 공개하고, FOMC 회의는 통계 발표가
 * 아니라 거기 없어서 <b>연준</b> 일정을 자원 파일로 들고 있다. 둘을 합쳐 한 줄기로 내보낸다.
 *
 * <p>원래 Finnhub {@code /calendar/economic} 을 쓰기로 했으나 무료 티어에서 403(유료 전용)이
 * 확인되어 교체했다. 지금 구성은 원천 기관을 직접 보는 셈이라 키도 필요 없고 지연도 없다.
 */
@Slf4j
@Service
public class EconomicCalendarService {

    /** FOMC 결정 발표 시각 — 회의 이틀째 미 동부시각 오후 2시. */
    private static final LocalTime FOMC_ANNOUNCE_TIME = LocalTime.of(14, 0);

    /** 자원 파일에 남은 일정이 이보다 적게 남으면 갱신하라고 알린다. */
    private static final int SEED_WARN_MONTHS = 6;

    private final BlsScheduleClient blsClient;
    private final ExternalApiProperties.Bls config;
    private final List<EconomicEventResponse> fomcMeetings;

    private volatile List<EconomicEventResponse> blsCache = List.of();
    private volatile Instant blsCachedAt = null;

    public EconomicCalendarService(BlsScheduleClient blsClient, ExternalApiProperties props) {
        this.blsClient = blsClient;
        this.config = props.bls();
        this.fomcMeetings = loadFomcMeetings();
    }

    /**
     * 기간 안의 발표 일정. 날짜·시각 순.
     *
     * @param highOnly true 면 시장이 크게 움직이는 발표만
     */
    public List<EconomicEventResponse> events(LocalDate from, LocalDate to, boolean highOnly) {
        List<EconomicEventResponse> all = new ArrayList<>(blsEvents());
        all.addAll(fomcMeetings);

        return all.stream()
                .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
                .filter(e -> !highOnly || EconomicEventResponse.IMPORTANCE_HIGH.equals(e.importance()))
                .sorted(Comparator.comparing(EconomicEventResponse::date)
                        .thenComparing(EconomicEventResponse::timeEt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** 오늘 이후 다가오는 일정. 대시보드 위젯용. */
    public List<EconomicEventResponse> upcoming(int limit, boolean highOnly) {
        LocalDate today = LocalDate.now();
        return events(today, today.plusYears(2), highOnly).stream().limit(limit).toList();
    }

    /**
     * BLS 일정은 파일 하나를 통째로 받는 구조라 매 요청마다 부르면 낭비다. 발표 일정이 바뀌는
     * 빈도를 생각하면 반나절 캐시로 충분하다.
     */
    private List<EconomicEventResponse> blsEvents() {
        Instant cachedAt = blsCachedAt;
        if (cachedAt != null
                && Duration.between(cachedAt, Instant.now()).toHours() < config.cacheHours()) {
            return blsCache;
        }
        synchronized (this) {
            if (blsCachedAt != null
                    && Duration.between(blsCachedAt, Instant.now()).toHours() < config.cacheHours()) {
                return blsCache;
            }
            try {
                blsCache = blsClient.fetchAll();
                blsCachedAt = Instant.now();
            } catch (RuntimeException e) {
                // BLS 가 죽어도 FOMC 일정은 살아 있어야 한다. 캘린더 전체를 못 쓰게 만들지 않는다.
                log.warn("BLS 일정 갱신 실패. 이전 캐시({}건)로 이어간다.", blsCache.size());
            }
            return blsCache;
        }
    }

    /**
     * FOMC 일정을 자원 파일에서 읽는다.
     *
     * <p>연준 페이지를 긁는 방법도 되지만, 연 8회에 1~2년 앞서 확정되는 데이터라 HTML 구조가
     * 바뀌면 깨지는 위험을 지느니 파일로 두고 해마다 갱신하는 편이 낫다.
     */
    private List<EconomicEventResponse> loadFomcMeetings() {
        try (InputStream in = new ClassPathResource("data/fomc-meetings.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            List<EconomicEventResponse> meetings = new ArrayList<>();
            for (JsonNode node : root.path("meetings")) {
                LocalDate date = LocalDate.parse(node.path("date").asString());
                boolean projections = node.path("projections").asBoolean(false);
                meetings.add(new EconomicEventResponse(
                        date,
                        FOMC_ANNOUNCE_TIME,
                        "FOMC 정례회의 결과 발표",
                        "Federal Reserve",
                        EconomicEventResponse.IMPORTANCE_HIGH,
                        projections ? "경제전망 요약(SEP) 발표 · 의장 기자회견" : null));
            }
            warnIfSeedRunningOut(meetings);
            return List.copyOf(meetings);
        } catch (Exception e) {
            // 캘린더가 반쪽이 되더라도 앱은 떠야 한다. BLS 지표 일정은 그대로 나간다.
            log.error("FOMC 일정 파일을 읽지 못했다. 경제 캘린더에서 FOMC 가 빠진다: {}", e.getMessage());
            return List.of();
        }
    }

    private void warnIfSeedRunningOut(List<EconomicEventResponse> meetings) {
        LocalDate last = meetings.stream()
                .map(EconomicEventResponse::date)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (last == null || last.isBefore(LocalDate.now().plusMonths(SEED_WARN_MONTHS))) {
            log.warn("FOMC 일정이 {} 까지밖에 없다. 연준 페이지에서 다음 해 일정을 "
                    + "data/fomc-meetings.json 에 추가할 것.", last);
        } else {
            log.info("FOMC 일정 {}건 적재 (마지막 {})", meetings.size(), last);
        }
    }
}
