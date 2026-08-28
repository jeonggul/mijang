/*
 * NewsService — 뉴스 수집
 *
 * 이 파일이 하는 일
 *   벤더에서 받은 기사를 표에 남기고 종목과 잇는다.
 *
 *   화면은 이미 StockNewsFetchService 로 그때그때 받아 쓰고 있다. 그런데도 표에
 *   쌓는 이유는 둘이다 — 뉴스 알림(NOTI-03)은 "지난번에 없던 기사" 를 알아야 하고,
 *   그 판단은 어딘가에 남아 있어야만 가능하다. 그리고 벤더가 죽어도 목록이 비지 않는다.
 *
 *   본문은 저장하지 않는다. 제목·요약·원문 링크만이다(저작권).
 */
package com.example.mijang.news.service;

import com.example.mijang.news.dto.NewsItemResponse;
import com.example.mijang.news.mapper.NewsMapper;
import com.example.mijang.news.mapper.NewsStockMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뉴스 수집·조회. 개발명세서(API) NEWS-001 · 수집원은 Finnhub — 확장(부록 C)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    /** 원문 링크 최대 길이. 표가 VARCHAR(1000) 이라 넘으면 저장이 실패한다. */
    private static final int URL_MAX = 1000;
    private static final int HEADLINE_MAX = 500;
    private static final int SUMMARY_MAX = 1000;

    private final NewsMapper newsMapper;
    private final NewsStockMapper newsStockMapper;
    private final StockNewsFetchService fetchService;

    /** 수집 대상 종목. 누군가 보유하거나 관심에 담아 둔 것만이다. */
    @Transactional(readOnly = true)
    public List<String> symbolsOfInterest() {
        return newsMapper.findSymbolsOfInterest();
    }

    /**
     * 한 종목의 기사를 받아 표에 남긴다.
     *
     * <p>이미 있는 기사는 건너뛴다. <b>새로 들어온 건수를 돌려준다</b> — 뉴스 알림이
     * 그 값으로 "알릴 것이 있는가" 를 판단한다.
     *
     * <p>벤더가 죽거나 한 종목이 실패해도 예외를 밖으로 내지 않는다. 배치가 종목 하나
     * 때문에 멈추면 나머지 수십 종목이 통째로 밀린다.
     *
     * @return 새로 저장된 기사 수
     */
    @Transactional
    public int collect(String symbol) {
        List<NewsItemResponse> items;
        try {
            items = fetchService.news(symbol);
        } catch (RuntimeException e) {
            log.warn("[뉴스] {} 수집 실패: {}", symbol, e.toString());
            return 0;
        }

        int saved = 0;
        for (NewsItemResponse item : items) {
            if (item.url() == null || item.headline() == null || item.publishedAt() == null) {
                continue;   // 링크나 제목이 없으면 화면에 쓸 수가 없다
            }
            /* 벤더가 기사 id 를 따로 주지 않아 원문 URL 을 식별자로 쓴다.
               같은 기사가 다른 매체에 실려도 URL 은 다르므로 중복으로 묶이지 않는다 */
            String vendorId = vendorIdOf(item.url());
            LocalDateTime publishedAt = LocalDateTime.ofInstant(item.publishedAt(), ZoneOffset.UTC);

            int inserted = newsMapper.insertIgnore(vendorId,
                    cut(item.headline(), HEADLINE_MAX), cut(item.summary(), SUMMARY_MAX),
                    cut(item.url(), URL_MAX), item.source(), publishedAt);

            Long newsId = newsMapper.findIdByVendorId(vendorId);
            if (newsId != null) {
                newsStockMapper.link(newsId, symbol);
            }
            saved += inserted;
        }
        return saved;
    }

    /**
     * 원문 URL 을 벤더 식별자로 줄인다.
     *
     * <p>표의 `vendor_id` 는 VARCHAR(64) 라 URL 을 그대로 넣을 수 없다. SHA-256 16진수가
     * 정확히 64자라 그대로 들어간다.
     *
     * <p><b>String.hashCode 를 쓰지 않는다.</b> 32비트라 충돌이 현실적으로 일어나고,
     * 충돌하면 서로 다른 기사가 한 건으로 묶여 하나가 영영 저장되지 않는다.
     */
    private static String vendorIdOf(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 표준 JRE 에 반드시 있다. 없으면 환경이 깨진 것이다
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }

    /** 표 길이에 맞춰 자른다. 넘치면 저장이 통째로 실패한다. */
    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
