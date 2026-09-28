package com.example.mijang.fx.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** 벤더에서 받은 환율 시세 한 건({@code fx_quotes} 한 행)을 담는다. */
public record FxQuote(String currencyCode, BigDecimal basePrice, Instant quotedAt) {
}
