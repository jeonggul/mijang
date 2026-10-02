/*
 * HolderAtExDate — 배당락일 시점의 보유자 한 명
 *
 * 이 파일이 하는 일
 *   예상 배당 생성(PROFIT-12)의 대상이다. 배당락일 전일까지 보유한
 *   사람에게 배당이 나온다 — 수량은 그 시점의 매매 기록 합이다.
 */
package com.example.mijang.dividend.dto;

import java.math.BigDecimal;

/**
 * 배당락일 시점 보유자. 개발명세서(API) PROFIT-12
 */
public record HolderAtExDate(Long userId, Long portfolioId, BigDecimal quantity) {
}
