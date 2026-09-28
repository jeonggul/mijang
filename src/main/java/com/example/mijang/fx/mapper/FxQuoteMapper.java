package com.example.mijang.fx.mapper;

import com.example.mijang.fx.domain.FxQuote;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 환율 시세 이력({@code fx_quotes})을 넣고 읽는다. */
@Mapper
public interface FxQuoteMapper {

    /** 시세 한 건을 넣는다. 같은 시각이 이미 있으면 넘어가며 0 행도 정상이다. */
    int insertIgnore(FxQuote quote);

    /** 가장 최근 시세를 찾는다. 없으면 null 을 준다. */
    FxQuote findLatest(@Param("currencyCode") String currencyCode);

    /** 그 날짜(KST 경계)의 마지막 시세를 찾는다. 없으면 null 을 준다. */
    FxQuote findLastOfDate(@Param("currencyCode") String currencyCode,
                           @Param("date") LocalDate date);
}
