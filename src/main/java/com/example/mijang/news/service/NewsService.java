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

/** 벤더 기사를 표에 남기고 종목과 잇는다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    /** 원문 링크 최대 길이. 표가 VARCHAR(1000) 이라 넘기면 저장이 실패한다. */
    private static final int URL_MAX = 1000;
    private static final int HEADLINE_MAX = 500;
    private static final int SUMMARY_MAX = 1000;

    private final NewsMapper newsMapper;
    private final NewsStockMapper newsStockMapper;
    private final StockNewsFetchService fetchService;

    /** 수집 대상 종목(보유·관심에 담긴 것)을 조회한다. */
    @Transactional(readOnly = true)
    public List<String> symbolsOfInterest() {
        return newsMapper.findSymbolsOfInterest();
    }

    /** 한 종목의 기사를 받아 표에 남기고 새로 저장된 건수를 반환한다. 실패해도 예외를 밖으로 내지 않는다. */
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
            // 벤더가 기사 id 를 주지 않아 원문 URL 을 식별자로 쓴다
            String vendorId = vendorIdOf(item.url());
            LocalDateTime publishedAt = LocalDateTime.ofInstant(item.publishedAt(), ZoneOffset.UTC);

            int inserted = newsMapper.insertIgnore(vendorId,
                    cut(item.headline(), HEADLINE_MAX), cut(item.summary(), SUMMARY_MAX),
                    cut(item.url(), URL_MAX), item.source(), publishedAt);

            // id 를 따로 묻지 않고 vendor_id 로 바로 잇는다
            newsStockMapper.linkByVendorId(vendorId, symbol);
            saved += inserted;
        }
        return saved;
    }

    /** 원문 URL 을 SHA-256 16진수(64자)로 줄여 vendor_id 로 쓴다. String.hashCode 는 충돌 위험이 있어 쓰지 않는다. */
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

    /** 표 컬럼 길이에 맞춰 자른다. */
    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
