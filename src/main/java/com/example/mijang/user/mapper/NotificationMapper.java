package com.example.mijang.user.mapper;

import com.example.mijang.user.dto.NotificationResponse;
import com.example.mijang.user.dto.NotificationSettingsForm;
import com.example.mijang.user.dto.NotificationSettingsResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** notifications·알림 설정 테이블 접근과 알림 판정 조회를 담당한다. */
@Mapper
public interface NotificationMapper {

    /** 최근 알림을 limit 건까지 조회한다. */
    List<NotificationResponse> findRecent(@Param("userId") Long userId, @Param("limit") int limit);

    /** 이 사용자의 알림을 모두 읽음으로 바꾼다. */
    int markAllRead(@Param("userId") Long userId);

    /** 이 사용자의 알림 설정을 조회한다 — 행이 없으면 null이다. */
    NotificationSettingsResponse findSettings(@Param("userId") Long userId);

    /** 알림 설정을 넣거나 갱신한다. */
    int upsertSettings(@Param("userId") Long userId, @Param("form") NotificationSettingsForm form);

    /** 알림 한 건을 넣는다 — 만드는 쪽(배치)만 부른다. */
    int insert(@Param("userId") Long userId,
               @Param("type") String type,
               @Param("symbol") String symbol,
               @Param("title") String title,
               @Param("body") String body,
               @Param("linkUrl") String linkUrl);

    /** 목표가에 처음 닿은 보유 종목을 조회한다 — 전일 고가는 목표 아래, 당일 고가는 목표 이상인 교차로 판정한다. */
    java.util.List<com.example.mijang.user.dto.TargetPriceHit> findTargetPriceHits(
            @Param("tradeDate") java.time.LocalDate tradeDate);

    /** 하루 변동이 사용자 임계값(기본 5%)을 넘은 보유 종목을 조회한다. */
    java.util.List<com.example.mijang.user.dto.VolatilityHit> findVolatilityHits(
            @Param("tradeDate") java.time.LocalDate tradeDate);

    /** 배당락일이 이틀 안으로 다가온 보유 종목을 조회한다 — 같은 락일에 대해서는 한 번만 걸린다. */
    java.util.List<com.example.mijang.user.dto.DividendExDateHit> findDividendExDateHits(
            @Param("today") java.time.LocalDate today);

    /** 아직 알리지 않은 예상 배당(ESTIMATED)을 조회한다 — 예상 행 하나에 알림 하나다. */
    java.util.List<com.example.mijang.user.dto.DividendPayHit> findDividendPayHits();

    /** 이 종목의 새 기사를 알릴 사용자를 조회한다 — 뉴스 알림을 켠 보유·관심 사용자 중 오늘 이미 받은 사람은 뺀다. */
    java.util.List<Long> findNewsRecipients(@Param("symbol") String symbol);
}
