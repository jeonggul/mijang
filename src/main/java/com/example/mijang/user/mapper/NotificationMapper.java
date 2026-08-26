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

    /**
     * 배당락일이 이틀 안으로 다가온 보유 종목. NOTI-04 첫 갈래.
     *
     * <p>락일 전일까지 보유해야 배당이 나온다 — 지나서 알리면 안내가 아니라 통보다.
     * 같은 락일에 대해서는 한 번만 알린다(락일 7일 전부터의 발송 이력으로 거른다).
     */
    java.util.List<com.example.mijang.user.dto.DividendExDateHit> findDividendExDateHits(
            @Param("today") java.time.LocalDate today);

    /**
     * 아직 알리지 않은 예상 배당(ESTIMATED). NOTI-04 둘째 갈래.
     *
     * <p>예상 행 하나에 알림 하나다 — 알림이 예상 생성 이후에 발송됐으면 다시
     * 걸리지 않고, 확정하면 상태가 바뀌어 자연히 빠진다.
     */
    java.util.List<com.example.mijang.user.dto.DividendPayHit> findDividendPayHits();
}
