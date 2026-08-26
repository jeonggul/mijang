package com.example.mijang.user.mapper;

import com.example.mijang.user.dto.NotificationResponse;
import com.example.mijang.user.dto.NotificationSettingsForm;
import com.example.mijang.user.dto.NotificationSettingsResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    List<NotificationResponse> findRecent(@Param("userId") Long userId, @Param("limit") int limit);

    int markAllRead(@Param("userId") Long userId);

    NotificationSettingsResponse findSettings(@Param("userId") Long userId);

    int upsertSettings(@Param("userId") Long userId, @Param("form") NotificationSettingsForm form);

    /** 알림 한 건을 넣는다. 만드는 쪽(배치)만 부른다. */
    int insert(@Param("userId") Long userId,
               @Param("type") String type,
               @Param("symbol") String symbol,
               @Param("title") String title,
               @Param("body") String body,
               @Param("linkUrl") String linkUrl);

    /**
     * 목표가에 <b>처음</b> 닿은 보유 종목. NOTI-01.
     *
     * <p>"처음" 은 교차로 판정한다 — 전일 고가는 목표 아래, 당일 고가는 목표 이상.
     * 이미 넘어서 있는 날은 다시 걸리지 않으므로 알림이 하루 한 번을 넘지 않는다.
     * 목표가는 그 종목에 마지막으로 남긴 판단 메모의 값이다.
     *
     * @param tradeDate 판정할 ET 거래일. 그날 일봉이 이미 수집돼 있어야 한다
     */
    java.util.List<com.example.mijang.user.dto.TargetPriceHit> findTargetPriceHits(
            @Param("tradeDate") java.time.LocalDate tradeDate);

    /**
     * 하루 변동이 사용자 임계값을 넘은 보유 종목. NOTI-02.
     *
     * <p>임계값은 사용자마다 다르다(기본 5%). 설정 행이 없는 사용자는 기본값으로 본다 —
     * 행은 설정 화면을 처음 열 때야 생긴다.
     */
    java.util.List<com.example.mijang.user.dto.VolatilityHit> findVolatilityHits(
            @Param("tradeDate") java.time.LocalDate tradeDate);
}
