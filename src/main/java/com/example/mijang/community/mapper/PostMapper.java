/*
 * PostMapper — posts(게시글) 테이블 접근
 *
 * 이 파일이 하는 일
 *   목록·상세·저장 통로다. 목록이 둘로 갈린다 —
 *     findByBoard   자유·질문. 종목이 없다
 *     findBySymbol  종목별. 게시판이 곧 종목이다
 *   한 메서드에 board 와 symbol 을 함께 받아 분기시킬 수도 있지만, 그러면 두 조회가
 *   서로 다른 인덱스를 타는데 SQL 한 덩이가 그 사실을 가린다.
 */
package com.example.mijang.community.mapper;

import com.example.mijang.community.domain.PostRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * posts(게시글) 접근.
 *
 * <p>개발명세서(MVC) · 커뮤니티 · mapper — 확장(부록 C)
 */
@Mapper
public interface PostMapper {

    /**
     * 저장.
     *
     * <p>인자가 많지만 전부 서버가 정한 값이다. 화면이 보낸 것은 제목·본문·배지 여부뿐이고
     * 나머지는 서비스가 구해서 넣는다.
     */
    int insert(@Param("userId") Long userId,
               @Param("board") String board,
               @Param("symbol") String symbol,
               @Param("title") String title,
               @Param("content") String content,
               @Param("priceAtWrite") BigDecimal priceAtWrite,
               @Param("fxAtWrite") BigDecimal fxAtWrite,
               @Param("showHoldingBadge") boolean showHoldingBadge,
               @Param("holdingQtyAtWrite") BigDecimal holdingQtyAtWrite,
               @Param("tradeTxId") Long tradeTxId,
               @Param("tradeSide") String tradeSide,
               @Param("tradeSymbol") String tradeSymbol,
               @Param("tradePrice") BigDecimal tradePrice,
               @Param("tradeAt") LocalDateTime tradeAt,
               @Param("tradePnlKrw") BigDecimal tradePnlKrw,
               @Param("tradePnlRate") BigDecimal tradePnlRate);

    /** 방금 저장한 글의 id. insert 직후에만 의미가 있다. */
    Long findLastInsertedId();

    /**
     * 일반 커뮤니티 목록. {@code COM-001}
     *
     * @param sort {@code HOT} 이면 좋아요 순, 그 외에는 최신순
     */
    List<PostRow> findByBoard(@Param("board") String board,
                              @Param("sort") String sort,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    /** 일반 커뮤니티 글 수. 페이징에 쓴다. */
    long countByBoard(@Param("board") String board);

    /**
     * 내가 쓴 글. 게시판을 가리지 않고, 숨김·삭제된 것도 함께 돌려준다.
     *
     * <p>남에게 안 보이는 글도 <b>쓴 사람에게는 보여야</b> 한다 — 왜 목록에서
     * 사라졌는지 알 수 없으면 고장으로 읽힌다.
     */
    List<PostRow> findByUser(@Param("userId") Long userId,
                             @Param("limit") int limit,
                             @Param("offset") int offset);

    long countByUser(@Param("userId") Long userId);

    /** 종목별 게시판 목록. {@code COM-001} */
    List<PostRow> findBySymbol(@Param("symbol") String symbol,
                               @Param("sort") String sort,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    /** 종목별 게시글 수. COM-001 */
    long countBySymbol(@Param("symbol") String symbol);

    /** 상세. 숨김·삭제된 글은 없는 것으로 본다. {@code COM-003} */
    PostRow findById(@Param("postId") Long postId);

    /** 조회수 +1. 상세를 열 때마다 부른다. */
    int increaseViewCount(@Param("postId") Long postId);

    /** 제목·본문 수정. 작성 시점 값(주가·환율·매매 카드)은 건드리지 않는다(2.3). */
    int updateContent(@Param("postId") Long postId,
                      @Param("title") String title,
                      @Param("content") String content);

    /** 상태 전환. 삭제·숨김·복원이 전부 이 문 하나다 — 지우는 경로는 없다(2.6). */
    int updateStatus(@Param("postId") Long postId, @Param("status") String status);

    /**
     * 지금 공개 상태일 때만 바꾼다. 신고 자동 숨김이 쓴다.
     *
     * <p>조건 없이 바꾸면 관리자가 손으로 복원해 둔 글을 신고 한 건이 다시 끌어내린다.
     */
    int updateStatusIfPublished(@Param("postId") Long postId, @Param("status") String status);

    /** 댓글 수 +1. 댓글을 달 때마다 부른다 — 목록에서 매번 세면 글 수만큼 COUNT 가 나간다. */
    int increaseCommentCount(@Param("postId") Long postId);
}
