package com.example.mijang.stock.mapper;

import com.example.mijang.stock.dto.WatchlistItemResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * watchlists·watchlist_groups 접근.
 *
 * <p>MVP 는 사용자마다 기본 그룹 하나만 쓴다(2.8).
 */
@Mapper
public interface WatchlistMapper {

    /** 사용자의 기본 그룹 id. 없으면 null. */
    Long findDefaultGroupId(@Param("userId") Long userId);

    /**
     * 기본 그룹을 만든다. 처음 등록할 때만 불린다.
     *
     * <p>가입 시점이 아니라 첫 등록 시점에 만드는 이유 — 관심종목을 한 번도 쓰지 않는
     * 사용자에게 빈 그룹이 남는다.
     */
    int insertDefaultGroup(@Param("userId") Long userId);

    /** 방금 만든 그룹 id. {@code insertDefaultGroup} 직후에만 의미가 있다. */
    Long findLastInsertedGroupId();

    /**
     * 목록 조회. 시세를 함께 붙인다.
     *
     * <p>종목별 최근 종가와 직전 종가를 상관 서브쿼리로 가져온다. 관심종목은 많아야
     * 수십 건이라 이 방식으로 충분하다.
     */
    List<WatchlistItemResponse> findByUser(@Param("userId") Long userId);

    /**
     * 등록. 이미 있으면 아무 일도 하지 않는다.
     *
     * <p>{@code uk_watchlists_group_symbol} 이 있어 중복 삽입은 예외가 된다.
     * 별표를 두 번 눌렀다고 오류를 띄울 이유가 없어 무시한다.
     *
     * @return 실제로 들어간 행 수. 이미 있었으면 0
     */
    int insertItem(@Param("groupId") Long groupId,
                   @Param("userId") Long userId,
                   @Param("symbol") String symbol);

    /**
     * 해제. <b>본인 것만</b> 지운다.
     *
     * <p>{@code user_id} 조건이 없으면 남의 관심종목 id 를 넣어 지울 수 있다.
     *
     * @return 지워진 행 수. 0 이면 없거나 남의 것이다
     */
    int deleteItem(@Param("id") Long id, @Param("userId") Long userId);

    /** 이미 담은 종목인지. 상세 화면의 별표 상태를 그리는 데 쓴다. */
    boolean existsByUserAndSymbol(@Param("userId") Long userId, @Param("symbol") String symbol);
}
