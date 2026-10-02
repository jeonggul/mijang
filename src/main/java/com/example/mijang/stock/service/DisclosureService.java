package com.example.mijang.stock.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.ExternalApiProperties;
import com.example.mijang.stock.client.SecEdgarClient;
import com.example.mijang.stock.dto.FilingResponse;
import com.example.mijang.stock.dto.FinancialFactResponse;
import tools.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SEC 공시·재무제표 조회.
 *
 * <p>개발명세서(MVC) · 종목 · service — [[미장-외부-데이터-출처]] 공시·재무제표 = SEC EDGAR
 *
 * <p>SEC 원문에는 그대로 쓰기 어려운 구석이 세 군데 있고, 이 클래스가 그걸 흡수한다.
 * <ol>
 *   <li>조회 키가 티커가 아니라 CIK 다 — 매핑 전체를 받아 캐시한다.</li>
 *   <li>같은 재무 개념도 회사마다 태그가 다르다 — 후보를 순서대로 시도한다.</li>
 *   <li>정정 제출로 같은 기간이 중복된다 — 제출일이 가장 늦은 것만 남긴다.</li>
 * </ol>
 */
@Slf4j
@Service
public class DisclosureService {

    /**
     * 재무 지표별 XBRL 태그 후보. 앞에서부터 시도해 처음 값이 있는 태그를 쓴다.
     *
     * <p>예를 들어 매출은 애플이 {@code RevenueFromContractWithCustomerExcludingAssessedTax} 를 쓰고
     * 다른 회사는 {@code Revenues} 를 쓴다. 하나만 하드코딩하면 상당수 종목에서 빈 값이 나온다.
     */
    private static final Map<String, List<String>> METRIC_TAGS = Map.of(
            "revenue", List.of(
                    "RevenueFromContractWithCustomerExcludingAssessedTax",
                    "Revenues",
                    "RevenueFromContractWithCustomerIncludingAssessedTax",
                    "SalesRevenueNet"),
            "netIncome", List.of(
                    "NetIncomeLoss",
                    "ProfitLoss"),
            "operatingIncome", List.of(
                    "OperatingIncomeLoss"),
            "assets", List.of(
                    "Assets"),
            "equity", List.of(
                    "StockholdersEquity",
                    "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"),
            "eps", List.of(
                    "EarningsPerShareDiluted",
                    "EarningsPerShareBasic"));

    /** 분기 실적으로 볼 기간 길이의 상한. 분기는 대략 90일, 반기부터는 이보다 길다. */
    private static final int QUARTER_MAX_DAYS = 100;

    private final SecEdgarClient client;
    private final ExternalApiProperties.Sec config;

    private final Map<String, String> tickerToCik = new ConcurrentHashMap<>();
    private volatile Instant cikLoadedAt = null;

    public DisclosureService(SecEdgarClient client, ExternalApiProperties props) {
        this.client = client;
        this.config = props.sec();
    }

    /**
     * 티커에 해당하는 10자리 CIK. SEC 는 티커를 받지 않으므로 모든 조회가 여기서 시작한다.
     */
    public String cikOf(String symbol) {
        ensureCikLoaded();
        String cik = tickerToCik.get(symbol.toUpperCase(Locale.ROOT));
        if (cik == null) {
            throw new BusinessException(ErrorCode.STOCK_CIK_NOT_FOUND);
        }
        return cik;
    }

    /** 최근 공시 목록. 최신순. */
    public List<FilingResponse> filings(String symbol, String formFilter, int limit) {
        String cik = cikOf(symbol);
        JsonNode recent = client.submissions(cik).path("filings").path("recent");
        if (recent.isMissingNode()) {
            throw new BusinessException(ErrorCode.STOCK_DISCLOSURE_NOT_FOUND);
        }

        JsonNode forms = recent.path("form");
        JsonNode filingDates = recent.path("filingDate");
        JsonNode reportDates = recent.path("reportDate");
        JsonNode accessions = recent.path("accessionNumber");
        JsonNode primaryDocs = recent.path("primaryDocument");

        List<FilingResponse> result = new ArrayList<>();
        for (int i = 0; i < forms.size() && result.size() < limit; i++) {
            String form = forms.path(i).asString();
            if (formFilter != null && !formFilter.isBlank() && !form.equalsIgnoreCase(formFilter)) {
                continue;
            }
            String accession = accessions.path(i).asString();
            result.add(new FilingResponse(
                    form,
                    parseDate(filingDates.path(i).asString(null)),
                    parseDate(reportDates.path(i).asString(null)),
                    accession,
                    documentUrl(cik, accession, primaryDocs.path(i).asString(null))));
        }
        return result;
    }

    /**
     * 재무 지표 시계열. 최신 기간부터.
     *
     * @param metric {@link #METRIC_TAGS} 의 키 (revenue, netIncome, ...)
     */
    public List<FinancialFactResponse> financials(String symbol, String metric, int limit) {
        List<String> candidates = METRIC_TAGS.get(metric);
        if (candidates == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST);
        }
        String cik = cikOf(symbol);

        // 후보 태그를 하나씩 보고 "처음 값이 있는 것"에서 멈추면 안 된다. 회계기준이 바뀌면서
        // 태그를 갈아탄 회사가 많아서(NVDA 는 옛 태그가 FY2022 에서 끊긴다) 옛 태그에 걸리면
        // 최신 실적이 통째로 빠진다. 그래서 전부 조회해 기간 단위로 합친다.
        Map<String, FinancialFactResponse> merged = new LinkedHashMap<>();
        for (String tag : candidates) {
            client.companyConcept(cik, "us-gaap", tag)
                    .ifPresent(concept -> mergeFacts(merged, concept, tag));
        }

        if (merged.isEmpty()) {
            // companyconcept 가 빈 배열을 주는데 companyfacts 에는 값이 있는 조합이 실존한다.
            // 코카콜라(KO)의 Revenues 가 그렇다 — 앞의 경로만 믿으면 조용히 빈 화면이 나간다.
            mergeFromCompanyFacts(merged, symbol, cik, candidates);
        }

        if (merged.isEmpty()) {
            log.info("XBRL 태그 후보 소진 symbol={} candidates={}", symbol, candidates);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(FinancialFactResponse::end).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * companyconcept 가 전부 빈 값일 때만 타는 보정 경로. 응답이 커서 평소에는 쓰지 않는다.
     */
    private void mergeFromCompanyFacts(Map<String, FinancialFactResponse> merged,
                                       String symbol, String cik, List<String> candidates) {
        Optional<JsonNode> facts = client.companyFacts(cik);
        if (facts.isEmpty()) {
            return;
        }
        JsonNode usGaap = facts.get().path("facts").path("us-gaap");
        for (String tag : candidates) {
            JsonNode concept = usGaap.path(tag);
            if (!concept.isMissingNode()) {
                mergeFacts(merged, concept, tag);
            }
        }
        if (!merged.isEmpty()) {
            log.info("companyfacts 보정 경로로 조회 symbol={}", symbol);
        }
    }

    /** 지원하는 재무 지표 키 목록. */
    public List<String> supportedMetrics() {
        return METRIC_TAGS.keySet().stream().sorted().toList();
    }

    /**
     * 같은 기간이 여러 번 나오는 문제를 여기서 정리한다. 최초 보고 뒤 정정 제출이 들어오면 SEC 는
     * 두 값을 모두 남기므로, 기간(start~end)이 같으면 제출일이 가장 늦은 것만 취한다.
     *
     * <p>여러 태그의 결과를 같은 map 에 누적한다. 태그가 달라도 기간이 같으면 같은 실적이므로
     * 제출일이 늦은 쪽을 남긴다.
     */
    private void mergeFacts(Map<String, FinancialFactResponse> latestByPeriod, JsonNode concept, String tag) {
        JsonNode units = concept.path("units");
        if (units.isMissingNode() || units.isEmpty()) {
            return;
        }
        String unit = pickUnit(units);
        if (unit == null) {
            return;
        }

        for (JsonNode item : units.path(unit)) {
            LocalDate end = parseDate(item.path("end").asString(null));
            if (end == null) {
                continue;
            }
            LocalDate start = parseDate(item.path("start").asString(null));
            LocalDate filed = parseDate(item.path("filed").asString(null));

            FinancialFactResponse fact = new FinancialFactResponse(
                    tag,
                    unit,
                    start,
                    end,
                    item.path("val").decimalValue(null),
                    isQuarterly(start, end),
                    item.path("form").asString(null),
                    item.hasNonNull("fy") ? item.path("fy").asInt() : null,
                    item.path("fp").asString(null),
                    filed);

            String periodKey = start + "~" + end;
            FinancialFactResponse kept = latestByPeriod.get(periodKey);
            if (kept == null || isNewer(fact.filed(), kept.filed())) {
                latestByPeriod.put(periodKey, fact);
            }
        }
    }

    /**
     * 한 태그 안에 단위가 여러 개 들어 있을 때 쓸 단위를 고른다.
     *
     * <p>대개는 USD 하나뿐이지만 EPS 는 {@code USD/shares} 로 오고, 옛 공시의 표기 오류가
     * {@code pure} 같은 엉뚱한 단위로 남아 있기도 하다. 코카콜라 EPS 가 그런 경우로,
     * 단위 목록의 첫 번째를 그냥 쓰면 2009년 값이 최신 실적 자리에 올라온다.
     * 그래서 가장 최근 데이터를 가진 단위를 고르고, 같으면 자료가 많은 쪽을 쓴다.
     */
    private static String pickUnit(JsonNode units) {
        String best = null;
        String bestEnd = null;
        int bestSize = 0;
        for (String unit : units.propertyNames()) {
            JsonNode items = units.path(unit);
            if (items.isEmpty()) {
                continue;
            }
            String latestEnd = null;
            for (JsonNode item : items) {
                String end = item.path("end").asString(null);
                if (end != null && (latestEnd == null || end.compareTo(latestEnd) > 0)) {
                    latestEnd = end;
                }
            }
            if (latestEnd == null) {
                continue;
            }
            int compared = bestEnd == null ? 1 : latestEnd.compareTo(bestEnd);
            if (compared > 0 || (compared == 0 && items.size() > bestSize)) {
                best = unit;
                bestEnd = latestEnd;
                bestSize = items.size();
            }
        }
        return best;
    }

    private static boolean isQuarterly(LocalDate start, LocalDate end) {
        // 재무상태표 항목(자산·자본)은 시점 값이라 start 가 없다. 누적/분기 구분 자체가 없으므로 false.
        if (start == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(start, end) <= QUARTER_MAX_DAYS;
    }

    private static boolean isNewer(LocalDate candidate, LocalDate kept) {
        if (candidate == null) {
            return false;
        }
        return kept == null || candidate.isAfter(kept);
    }

    /**
     * 공시 원문 링크를 조립한다. accession 번호는 경로에서는 하이픈을 뺀 형태, 파일명에서는 그대로 쓴다.
     */
    private static String documentUrl(String cik, String accession, String primaryDocument) {
        if (accession == null || accession.isBlank()) {
            return null;
        }
        String cikNumeric = String.valueOf(Long.parseLong(cik));
        String accessionNoDash = accession.replace("-", "");
        String base = "https://www.sec.gov/Archives/edgar/data/" + cikNumeric + "/" + accessionNoDash;
        return (primaryDocument == null || primaryDocument.isBlank())
                ? base + "/" + accession + "-index.htm"
                : base + "/" + primaryDocument;
    }

    /**
     * 티커→CIK 매핑을 통째로 받아 캐시한다. 전 종목이 한 파일이라 종목마다 부르면 낭비고,
     * 신규 상장 반영 주기를 생각하면 하루 한 번이면 충분하다.
     */
    private void ensureCikLoaded() {
        Instant loadedAt = cikLoadedAt;
        if (loadedAt != null && Duration.between(loadedAt, Instant.now()).toHours() < config.cikCacheHours()) {
            return;
        }
        synchronized (this) {
            if (cikLoadedAt != null
                    && Duration.between(cikLoadedAt, Instant.now()).toHours() < config.cikCacheHours()) {
                return;
            }
            JsonNode root = client.companyTickers();
            Map<String, String> loaded = new ConcurrentHashMap<>();
            root.forEach(entry -> {
                String ticker = entry.path("ticker").asString(null);
                if (ticker != null && !ticker.isBlank()) {
                    loaded.put(ticker.toUpperCase(Locale.ROOT), padCik(entry.path("cik_str").asLong()));
                }
            });
            if (loaded.isEmpty()) {
                throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
            }
            tickerToCik.clear();
            tickerToCik.putAll(loaded);
            cikLoadedAt = Instant.now();
            log.info("SEC 티커→CIK 매핑 적재 완료: {}건", tickerToCik.size());
        }
    }

    /** SEC 는 CIK 를 10자리 0채움 형태로 받는다. 320193 → 0000320193. */
    private static String padCik(long cik) {
        return String.format("%010d", cik);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
