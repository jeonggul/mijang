package com.example.mijang.community.mapper;

import com.example.mijang.community.domain.PostRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** posts(게시글) 테이블에 접근한다. */
@Mapper
public interface PostMapper {

    /** 게시글을 저장한다. 제목·본문·배지 외 값은 전부 서버가 정한다. */
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

    /** 방금 저장한 글의 id 를 반환한다. insert 직후에만 유효하다. */
    Long findLastInsertedId();

    /** 일반 커뮤니티 목록을 조회한다. HOT 이면 좋아요 순, 그 외 최신순이다. */
    List<PostRow> findByBoard(@Param("board") String board,
                              @Param("sort") String sort,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    /** 일반 커뮤니티 글 수를 센다. */
    long countByBoard(@Param("board") String board);

    /** 내가 쓴 글을 조회한다. 숨김·삭제된 것도 함께 돌려준다. */
    List<PostRow> findByUser(@Param("userId") Long userId,
                             @Param("limit") int limit,
                             @Param("offset") int offset);

    /** 내가 쓴 글 수를 센다. */
    long countByUser(@Param("userId") Long userId);

    /** 내가 스크랩한 글을 최신 스크랩 순으로 조회한다. 공개된 글만 준다. */
    List<PostRow> findScrappedByUser(@Param("userId") Long userId,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    /** 내가 스크랩한 글 수를 센다. */
    long countScrappedByUser(@Param("userId") Long userId);

    /** 종목별 게시판 목록을 조회한다. */
    List<PostRow> findBySymbol(@Param("symbol") String symbol,
                               @Param("sort") String sort,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    /** 종목별 게시글 수를 센다. */
    long countBySymbol(@Param("symbol") String symbol);

    /** 공개된 글 한 건을 조회한다. 숨김·삭제된 글은 없는 것으로 본다. */
    PostRow findById(@Param("postId") Long postId);

    /** 상태를 가리지 않고 한 건을 조회한다. 남의 숨김 글 차단은 서비스가 맡는다. */
    PostRow findAnyById(@Param("postId") Long postId);

    /** 조회수를 1 올린다. */
    int increaseViewCount(@Param("postId") Long postId);

    /** 제목·본문을 수정한다. 작성 시점 값은 건드리지 않는다. */
    int updateContent(@Param("postId") Long postId,
                      @Param("title") String title,
                      @Param("content") String content);

    /** 글 상태를 바꾼다. 삭제·숨김·복원 모두 이 문으로 처리한다. */
    int updateStatus(@Param("postId") Long postId, @Param("status") String status);

    /** 공개 상태일 때만 상태를 바꾼다. 관리자가 복원한 글을 다시 내리지 않기 위함이다. */
    int updateStatusIfPublished(@Param("postId") Long postId, @Param("status") String status);

    /** 댓글 수를 1 올린다. */
    int increaseCommentCount(@Param("postId") Long postId);
}
