/*
 * FxQuoteMapper — fx_quotes 접근
 *
 * 이 파일이 하는 일
 *   환율 시세 이력을 넣고 읽는다.
 *
 *   넣기는 INSERT IGNORE 다. 벤더가 준 시각에 유니크가 걸려 있어,
 *   값이 안 바뀌었으면 아무 일도 일어나지 않는다.
 *   덕분에 폴링 주기와 저장 주기가 분리되고, 배치가 두 번 돌아도 행이 늘지 않는다.
 */
package com.example.mijang.fx.mapper;

import com.example.mijang.fx.domain.FxQuote;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FxQuoteMapper {

    /**
     * 시세 한 건을 넣는다.
     *
     * <p>같은 시각이 이미 있으면 넘어간다(2.3).
     *
     * @return 실제로 들어간 행 수. 0 이면 값이 안 바뀌었다는 뜻이라 정상이다
     */
    int insertIgnore(FxQuote quote);

    /** 가장 최근 시세. 화면의 "현재 환율" 이 이걸 본다. 없으면 null */
    FxQuote findLatest(@Param("currencyCode") String currencyCode);

    /**
     * 그 날짜(KST 기준)의 마지막 시세. 확정값을 만들 때 쓴다.
     *
     * <p>날짜 경계를 KST 로 자른다. {@code quoted_at} 은 UTC 라 그냥 자르면 한국의 하루와
     * 어긋난다 — 한국 시각 오전 9시 이전 값이 전날로 잡힌다.
     *
     * @return 그날 값이 하나도 없으면 null
     */
    FxQuote findLastOfDate(@Param("currencyCode") String currencyCode,
                           @Param("date") LocalDate date);
}
