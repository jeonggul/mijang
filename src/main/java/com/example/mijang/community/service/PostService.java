/*
 * PostService — 게시글의 본체
 *
 * 이 파일이 하는 일
 *   커뮤니티가 둘로 갈린다. 자유·질문은 종목이 없고, 종목별 게시판은 종목이 있다.
 *   갈림길은 여기 한 곳뿐이고 컨트롤러는 어느 쪽인지만 알려 준다.
 *
 *   종목별 글을 쓸 때 세 가지를 서버가 박는다.
 *     작성 시점 주가   나중에 "그때 이 말이 맞았나" 를 대조하려면 그 시점 값이 있어야 한다
 *     주주 배지        보유 중이었는지. 수량은 남기되 내보내지 않는다
 *     매매 카드        내 매매 한 건을 스냅샷으로 떠서 붙인다
 *
 *   전부 화면이 보낸 값을 그대로 쓰지 않는다. 화면이 보낸 숫자를 저장하면
 *   원하는 수익률을 적어 넣을 수 있다.
 */
package com.example.mijang.community.service;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.community.domain.BoardType;
import com.example.mijang.community.domain.PostRow;
import com.example.mijang.community.dto.CommentResponse;
import com.example.mijang.community.dto.PostDetail;
import com.example.mijang.community.dto.PostForm;
import com.example.mijang.community.dto.PostSummary;
import com.example.mijang.community.dto.TradeCard;
import com.example.mijang.community.mapper.CommentMapper;
import com.example.mijang.community.mapper.ReactionMapper;
import com.example.mijang.community.mapper.PostMapper;
import com.example.mijang.community.policy.CommunityPolicy;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.market.service.QuoteService;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.portfolio.service.HoldingCalculator;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.mapper.StockMapper;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글. 개발명세서(API) COM-001~003 · 화면 SR-009 — 확장(부록 C)
 *
 * <p>게시판이 셋이다 — 자유·질문·종목별({@link BoardType}). 앞의 둘은 종목을 갖지 않고,
 * 작성 시점 주가·주주 배지·매매 카드는 <b>종목별에만</b> 붙는다.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    /** 목록에 회색 한 줄로 뜨는 본문 앞머리 길이. */
    private static final int EXCERPT_LENGTH = 120;

    /** 수익률 자리수. {@code RatioTypeHandler} 와 같다 — 0.1590 은 15.90%. */
    private static final int RATE_SCALE = 4;

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final ReactionMapper reactionMapper;
    private final StockMapper stockMapper;
    private final TransactionMapper transactionMapper;
    private final HoldingMapper holdingMapper;
    private final QuoteService quoteService;
    private final FxRateService fxRateService;
    private final TradingClock tradingClock;
    private final AdminSettingService settingService;
    private final UserMapper userMapper;

    /**
     * 게시글 저장. {@code COM-002}
     *
     * <p>검사 순서에 이유가 있다 — <b>DB 를 건드리기 전에 값부터 본다.</b>
     *
     * <ol>
     *   <li>게시판과 종목이 서로 맞는가 (자유·질문에 종목이 오면 거절)</li>
     *   <li>종목별이면 그 종목이 실제로 있는가</li>
     *   <li>붙이려는 매매가 내 것이고, 이 게시판 종목인가</li>
     *   <li>작성 시점 주가·환율·보유 수량을 구해서 박는다</li>
     * </ol>
     *
     * <p>작성 시점 주가를 못 구해도 저장은 막지 않는다. 주가는 글의 부속이고,
     * 벤더가 잠깐 죽었다고 글을 못 쓰게 하는 편이 더 나쁘다 — 없으면 null 로 두고
     * 화면이 — 로 그린다. 0 을 넣으면 계산된 값처럼 읽힌다.
     *
     * @param board  경로가 정한 게시판. 컨트롤러가 넘긴다
     * @param symbol 종목별이면 티커, 아니면 null
     * @return 생성된 글 id
     */
    @Transactional
    public Long create(Long userId, BoardType board, String symbol, PostForm form) {
        guardWrite(userId, form.getTitle(), form.getContent());
        if (!board.needsSymbol()) {
            return insertGeneral(userId, board, form);
        }

        String ticker = normalize(symbol);
        Stock stock = stockMapper.findBySymbol(ticker);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }

        BigDecimal holdingQty = holdingMapper.findQuantity(userId, ticker);
        boolean held = holdingQty != null && holdingQty.compareTo(BigDecimal.ZERO) > 0;

        TradeSnapshot trade = snapshotTrade(userId, ticker, form.getTradeTxId());

        postMapper.insert(userId, board.name(), ticker,
                form.getTitle(), form.getContent(),
                currentPrice(ticker), fxRateService.rateOf(tradingClock.today()),
                // 켜지 않았으면 수량도 남기지 않는다. 배지에만 쓰는 값이다
                form.isShowHoldingBadge() && held,
                form.isShowHoldingBadge() ? holdingQty : null,
                trade.txId(), trade.side(), trade.symbol(), trade.price(), trade.tradedAt(),
                trade.realizedPnlKrw(), trade.realizedPnlRate());
        return postMapper.findLastInsertedId();
    }

    /** 일반 커뮤니티 목록. {@code COM-001} */
    @Transactional(readOnly = true)
    public List<PostSummary> listByBoard(BoardType board, String sort, int page, int size) {
        return postMapper.findByBoard(board.name(), normalizeSort(sort), size, page * size)
                .stream().map(PostService::toSummary).toList();
    }

    /** 일반 커뮤니티 글 수. */
    @Transactional(readOnly = true)
    public long countByBoard(BoardType board) {
        return postMapper.countByBoard(board.name());
    }

    /** 종목별 게시판 목록. {@code COM-001} */
    @Transactional(readOnly = true)
    public List<PostSummary> listBySymbol(String symbol, String sort, int page, int size) {
        return postMapper.findBySymbol(normalize(symbol), normalizeSort(sort), size, page * size)
                .stream().map(PostService::toSummary).toList();
    }

    /** 종목별 게시글 수. */
    @Transactional(readOnly = true)
    public long countBySymbol(String symbol) {
        return postMapper.countBySymbol(normalize(symbol));
    }

    /**
     * 상세와 댓글. {@code COM-003}
     *
     * <p>조회수를 먼저 올리고 읽는다. 읽고 나서 올리면 방금 올린 1 이 화면에 안 보인다.
     *
     * @param viewerId 로그인하지 않았으면 null. 수정·삭제 버튼을 띄울지 정하는 데만 쓴다
     * @throws BusinessException 없거나 숨겨진 글일 때(404)
     */
    @Transactional
    public PostDetail detail(Long viewerId, Long postId) {
        postMapper.increaseViewCount(postId);
        PostRow row = postMapper.findById(postId);
        if (row == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        List<CommentResponse> comments = commentMapper.findByPost(postId);
        /* 버튼 상태(눌려 있음)를 그리려면 내 반응이 필요하다. 비로그인은 조회하지 않는다 */
        List<String> myTypes = viewerId == null
                ? List.of() : reactionMapper.findTypes(postId, viewerId);
        return new PostDetail(row.id(), row.board(), row.symbol(), row.title(), row.content(),
                row.authorName(), row.shareholder(), row.priceAtWrite(), toTradeCard(row),
                row.likeCount(), row.commentCount(), row.viewCount(), row.createdAt(),
                viewerId != null && viewerId.equals(row.authorId()),
                myTypes.contains("LIKE"), myTypes.contains("SCRAP"), comments);
    }

    /**
     * 좋아요·스크랩 토글. 4.5 점검 4.2 — 표는 있고 API 가 없어 목록의 좋아요가 항상 0 이었다.
     *
     * <p>상태를 먼저 읽지 않는다. "지워 보고, 지워진 게 없으면 넣는다" — 읽고 나서 쓰면
     * 그 사이에 다른 요청이 끼어들어 두 번 눌렀는데 두 개가 쌓이는 일이 생긴다.
     *
     * @return 토글 후 상태. active 와, LIKE 면 새 좋아요 수
     */
    @Transactional
    public ReactionState toggleReaction(Long userId, Long postId, String type) {
        PostRow row = postMapper.findById(postId);
        if (row == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        boolean active;
        if (reactionMapper.delete(postId, userId, type) > 0) {
            active = false;
        } else {
            try {
                reactionMapper.insert(postId, userId, type);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 지우기와 넣기 사이에 같은 요청이 먼저 넣었다. 이미 켜져 있으니 그대로 둔다
            }
            active = true;
        }
        /* 목록 정렬(HOT)이 posts.like_count 를 읽으므로 반응과 함께 맞춰 둔다.
           증감이 아니라 재집계라 언제 어긋났든 여기서 맞는 값으로 돌아온다 */
        if ("LIKE".equals(type)) {
            reactionMapper.syncLikeCount(postId);
        }
        PostRow after = postMapper.findById(postId);
        return new ReactionState(active, after == null ? 0 : after.likeCount());
    }

    /** 토글 결과. 화면이 버튼과 숫자를 다시 그리는 데 필요한 전부다. */
    public record ReactionState(boolean active, long likeCount) {
    }

    /**
     * 글 수정. 제목·본문만 바뀐다.
     *
     * <p>작성 시점 주가·환율·매매 카드는 그대로 둔다 — 등록 시 1회만 기록한다는
     * 원칙(2.3)이 수정에서 깨지면 "그때 얼마였다" 를 믿을 수 없게 된다.
     *
     * @throws BusinessException 없는 글(404), 남의 글(403)
     */
    @Transactional
    public void update(Long userId, Long postId, String title, String content) {
        PostRow row = postMapper.findById(postId);
        if (row == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        if (!userId.equals(row.authorId())) {
            throw new BusinessException(ErrorCode.COMMUNITY_FORBIDDEN);
        }
        postMapper.updateContent(postId, title, content);
    }

    /**
     * 글 삭제. 지우지 않고 status 만 바꾼다(2.6) — 댓글과 신고가 이 글을 참조한다.
     *
     * @throws BusinessException 없는 글(404), 남의 글(403)
     */
    @Transactional
    public void delete(Long userId, Long postId) {
        PostRow row = postMapper.findById(postId);
        if (row == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        if (!userId.equals(row.authorId())) {
            throw new BusinessException(ErrorCode.COMMUNITY_FORBIDDEN);
        }
        postMapper.updateStatus(postId, "DELETED");
    }

    /**
     * 운영 설정이 켜 둔 작성 규칙을 본다.
     *
     * <p>저장 직전이 아니라 <b>맨 앞</b>에서 본다. 종목 조회·시세 조회를 다 하고 나서
     * 막으면 헛일을 하고, 벤더 호출까지 낭비한다.
     *
     * @throws BusinessException 가입 직후 제한에 걸리거나(403) 금칙어가 있을 때(400)
     */
    private void guardWrite(Long userId, String title, String content) {
        int delayDays = settingService.number(AdminSettingKey.COMMUNITY_WRITE_DELAY_DAYS);
        if (delayDays > 0) {
            User me = userMapper.findById(userId);
            if (me != null && CommunityPolicy.tooEarlyToWrite(
                    me.createdAt(), delayDays, LocalDateTime.now(TradingClock.SERVICE_ZONE))) {
                throw new BusinessException(ErrorCode.COMMUNITY_WRITE_TOO_EARLY);
            }
        }
        if (settingService.isOn(AdminSettingKey.COMMUNITY_BADWORD_ENABLED)
                && CommunityPolicy.containsBannedWord(title, content)) {
            throw new BusinessException(ErrorCode.COMMUNITY_BADWORD, "content");
        }
    }

    /**
     * 자유·질문 글 저장.
     *
     * <p>종목이 없으니 주가·배지·매매 카드도 없다. 종목별 경로를 그대로 쓰면서 값만
     * null 로 넘기면 "여기서는 안 쓴다" 가 코드에 안 드러나므로 갈래를 나눠 둔다.
     */
    private Long insertGeneral(Long userId, BoardType board, PostForm form) {
        postMapper.insert(userId, board.name(), null, form.getTitle(), form.getContent(),
                null, null, false, null,
                null, null, null, null, null, null, null);
        return postMapper.findLastInsertedId();
    }

    /**
     * 붙일 매매를 스냅샷으로 뜬다.
     *
     * <p>{@code txId} 가 없으면 빈 스냅샷이다 — 매매를 안 고른 글이 대부분이다.
     *
     * <p>매도면 실현손익과 수익률까지 계산해서 박는다. 그 값은 매도 시점의 평단가에서
     * 나오는 파생값이라 나중에 과거 매매를 끼워 넣으면 달라진다(2.1). 글에 붙은 숫자가
     * 소리 없이 바뀌면 안 되므로 지금 계산해 둔다.
     *
     * <p>매수에는 손익을 넣지 않는다. 산 순간에는 확정된 손익이 없고, 0 을 넣으면
     * "본전" 으로 읽힌다.
     *
     * @throws BusinessException 없거나 남의 기록일 때(404), 이 게시판 종목이 아닐 때(400)
     */
    private TradeSnapshot snapshotTrade(Long userId, String symbol, Long txId) {
        if (txId == null) {
            return TradeSnapshot.none();
        }
        Transaction tx = transactionMapper.findById(txId, userId);
        if (tx == null) {
            throw new BusinessException(ErrorCode.TX_NOT_FOUND, "tradeTxId");
        }
        // 게시판 종목과 카드 종목이 다르면 읽는 사람이 남의 종목 수익률을 이 종목 것으로 읽는다
        if (!symbol.equals(tx.symbol())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "tradeTxId");
        }
        if (tx.buy()) {
            return new TradeSnapshot(tx.id(), tx.side(), tx.symbol(), tx.price(), tx.tradedAt(),
                    null, null);
        }

        HoldingCalculator.Calculation calc = HoldingCalculator.calculateAll(
                symbol, transactionMapper.findForRecalc(userId, symbol));
        BigDecimal realized = calc.realizedBySellId().get(tx.id());
        BigDecimal cost = calc.costBasisBySellId().get(tx.id());
        return new TradeSnapshot(tx.id(), tx.side(), tx.symbol(), tx.price(), tx.tradedAt(),
                realized, rateOf(realized, cost));
    }

    /**
     * 실현 수익률. 원가가 0 이거나 없으면 null 이다.
     *
     * <p>원가 0 은 값이 안 나오는 경우지 수익률 0% 가 아니다 — 0 을 돌려주면
     * 화면이 "본전" 으로 그린다.
     */
    private static BigDecimal rateOf(BigDecimal realizedKrw, BigDecimal costKrw) {
        if (realizedKrw == null || costKrw == null || costKrw.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return realizedKrw.divide(costKrw, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 지금 주가. 벤더가 못 주면 null 이고, 글은 그대로 저장된다. */
    private BigDecimal currentPrice(String symbol) {
        return quoteService.quote(symbol).map(q -> q.price()).orElse(null);
    }

    private static PostSummary toSummary(PostRow row) {
        return new PostSummary(row.id(), row.board(), row.symbol(), row.title(),
                excerpt(row.content()), row.authorName(), row.shareholder(),
                row.priceAtWrite(), toTradeCard(row),
                row.likeCount(), row.commentCount(), row.createdAt());
    }

    /** 매매를 안 붙인 글이면 null. 빈 카드를 내보내면 화면이 빈 상자를 그린다. */
    private static TradeCard toTradeCard(PostRow row) {
        if (row.tradeSide() == null) {
            return null;
        }
        return new TradeCard(row.tradeSide(), row.tradeSymbol(), row.tradePrice(),
                row.tradeAt(), row.tradePnlKrw(), row.tradePnlRate());
    }

    /** 본문 앞머리. 자른 자리에 말줄임을 붙여 잘렸다는 걸 드러낸다. */
    private static String excerpt(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.replace('\n', ' ').strip();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH) + "…";
    }

    /** 아는 정렬만 통과시킨다. 모르는 값이 SQL 까지 흘러가면 정렬이 조용히 사라진다. */
    private static String normalizeSort(String sort) {
        return "HOT".equalsIgnoreCase(sort) ? "HOT" : "NEW";
    }

    private static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 글에 박아 둘 매매 값 묶음.
     *
     * <p>insert 인자로 흩어 넣으면 매수인데 손익이 붙는 조합을 막을 곳이 없다.
     * 여기서 한 번 만들면 그 뒤로는 통째로 다닌다.
     */
    private record TradeSnapshot(Long txId, String side, String symbol, BigDecimal price,
                                 java.time.LocalDateTime tradedAt,
                                 BigDecimal realizedPnlKrw, BigDecimal realizedPnlRate) {

        static TradeSnapshot none() {
            return new TradeSnapshot(null, null, null, null, null, null, null);
        }
    }
}
