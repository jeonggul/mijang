/*
 * FxQuote — 환율 시세 한 건
 *
 * 이 파일이 하는 일
 *   벤더에서 받은 환율 하나를 담는다. fx_quotes 한 행에 대응한다.
 *
 *   "언제 시점의 값인가"(quotedAt)를 값과 함께 들고 다니는 것이 요점이다.
 *   환율은 시장이 닫히면 멈추는데, 값만 보면 그것이 방금 값인지 금요일 값인지
 *   알 수 없다. 확정값을 만들 때 이 시각으로 대체 여부를 판정한다.
 */
package com.example.mijang.fx.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 환율 시세 한 건. {@code fx_quotes} 한 행.
 *
 * @param currencyCode ISO 4217. 지금은 USD 만 쓴다
 * @param basePrice    1 단위당 원화. 무료 플랜은 USD 기준이라 응답의 {@code rates.KRW} 가 그대로 들어온다
 * @param quotedAt     벤더가 찍은 시각. 우리가 받은 시각이 아니다
 */
public record FxQuote(String currencyCode, BigDecimal basePrice, Instant quotedAt) {
}
