package com.example.mijang.stock.mapper;

import com.example.mijang.stock.dto.WatchlistItemResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** watchlists·watchlist_groups 테이블 접근 매퍼다. */
@Mapper
public interface WatchlistMapper {

    /** 사용자의 기본 그룹 id 를 조회한다. 없으면 null 이다. */
    Long findDefaultGroupId(@Param("userId") Long userId);

    /** 기본 그룹을 만든다. 첫 등록 시점에만 불린다. */
    int insertDefaultGroup(@Param("userId") Long userId);

    /** 방금 만든 그룹 id 를 조회한다. insertDefaultGroup 직후에만 의미가 있다. */
    Long findLastInsertedGroupId();

    /** 사용자의 관심종목 목록을 시세와 함께 조회한다. */
    List<WatchlistItemResponse> findByUser(@Param("userId") Long userId);

    /** 관심종목을 등록한다. 이미 있으면 아무 일도 하지 않고 0 을 돌려준다. */
    int insertItem(@Param("groupId") Long groupId,
                   @Param("userId") Long userId,
                   @Param("symbol") String symbol);

    /** 관심종목을 해제한다. user_id 조건으로 본인 것만 지운다 — 조건을 빼면 남의 것을 지울 수 있다. */
    int deleteItem(@Param("id") Long id, @Param("userId") Long userId);

    /** 이미 담은 종목인지 확인한다. */
    boolean existsByUserAndSymbol(@Param("userId") Long userId, @Param("symbol") String symbol);

    /** 이 사용자가 관심등록한 종목 심볼을 조회한다. */
    List<String> findSymbolsByUser(@Param("userId") Long userId);
}
