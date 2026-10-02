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
import com.example.mijang.community.dto.MyCommentResponse;
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
import com.example.mijang.portfolio.service.LedgerService;
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

/** 게시글 저장·조회·반응을 처리한다. 작성 시점 주가·배지·매매 카드는 서버가 정한다. */
@Service
@RequiredArgsConstructor
public class PostService {

    /** 목록에 회색 한 줄로 뜨는 본문 앞머리 길이. */
    private static final int EXCERPT_LENGTH = 120;

    /** 수익률 자리수. RatioTypeHandler 와 같다. */
    private static final int RATE_SCALE = 4;

    /** 남에게 보이는 상태. 나머지(HIDDEN·DELETED)는 쓴 사람에게만 보인다. */
    private static final String PUBLISHED = "PUBLISHED";

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final ReactionMapper reactionMapper;
    private final StockMapper stockMapper;
    private final TransactionMapper transactionMapper;
    private final HoldingMapper holdingMapper;
    private final LedgerService ledgerService;
    private final QuoteService quoteService;
    private final FxRateService fxRateService;
    private final TradingClock tradingClock;
    private final AdminSettingService settingService;
    private final UserMapper userMapper;

    /** 게시글을 저장한다. 작성 시점 주가를 못 구해도 저장은 막지 않는다. */
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

    /** 일반 커뮤니티 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<PostSummary> listByBoard(BoardType board, String sort, int page, int size) {
        return postMapper.findByBoard(board.name(), normalizeSort(sort), size, page * size)
                .stream().map(PostService::toSummary).toList();
    }

    /** 일반 커뮤니티 글 수를 센다. */
    @Transactional(readOnly = true)
    public long countByBoard(BoardType board) {
        return postMapper.countByBoard(board.name());
    }

    /** 내가 쓴 글을 최신순으로 조회한다. 숨김·삭제된 글도 함께 나간다. */
    @Transactional(readOnly = true)
    public List<PostSummary> listByUser(Long userId, int page, int size) {
        return postMapper.findByUser(userId, size, page * size)
                .stream().map(PostService::toSummary).toList();
    }

    /** 내가 쓴 글 수를 센다. */
    @Transactional(readOnly = true)
    public long countByUser(Long userId) {
        return postMapper.countByUser(userId);
    }

    /** 내가 스크랩한 글을 최신 스크랩 순으로 조회한다. 공개된 글만 나간다. */
    @Transactional(readOnly = true)
    public List<PostSummary> listScrappedByUser(Long userId, int page, int size) {
        return postMapper.findScrappedByUser(userId, size, page * size)
                .stream().map(PostService::toSummary).toList();
    }

    /** 내가 스크랩한 글 수를 센다. */
    @Transactional(readOnly = true)
    public long countScrappedByUser(Long userId) {
        return postMapper.countScrappedByUser(userId);
    }

    /** 내가 쓴 댓글을 조회한다. */
    @Transactional(readOnly = true)
    public List<MyCommentResponse> listCommentsByUser(Long userId, int page, int size) {
        return commentMapper.findByUser(userId, size, page * size);
    }

    /** 내가 쓴 댓글 수를 센다. */
    @Transactional(readOnly = true)
    public long countCommentsByUser(Long userId) {
        return commentMapper.countByUser(userId);
    }

    /** 종목별 게시판 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<PostSummary> listBySymbol(String symbol, String sort, int page, int size) {
        return postMapper.findBySymbol(normalize(symbol), normalizeSort(sort), size, page * size)
                .stream().map(PostService::toSummary).toList();
    }

    /** 종목별 게시글 수를 센다. */
    @Transactional(readOnly = true)
    public long countBySymbol(String symbol) {
        return postMapper.countBySymbol(normalize(symbol));
    }

    /** 상세와 댓글을 조회한다. 본인 글이면 숨김·삭제된 것도 열리고, 조회수는 공개 글에만 올린다. */
    @Transactional
    public PostDetail detail(Long viewerId, Long postId) {
        PostRow row = postMapper.findAnyById(postId);
        if (row == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
        boolean mine = viewerId != null && viewerId.equals(row.authorId());
        boolean published = PUBLISHED.equals(row.status());
        if (!published && !mine) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        /* 읽고 나서 올리므로 방금 올린 1 을 더해서 내보낸다 */
        long viewCount = row.viewCount();
        if (published) {
            postMapper.increaseViewCount(postId);
            viewCount++;
        }
        List<CommentResponse> comments = commentMapper.findByPost(postId);
        List<String> myTypes = viewerId == null
                ? List.of() : reactionMapper.findTypes(postId, viewerId);
        return new PostDetail(row.id(), row.board(), row.symbol(), row.title(), row.content(),
                row.authorName(), row.shareholder(), row.priceAtWrite(), toTradeCard(row),
                row.likeCount(), row.commentCount(), viewCount, row.createdAt(),
                row.status(), mine,
                myTypes.contains("LIKE"), myTypes.contains("SCRAP"), comments);
    }

    /** 좋아요·스크랩을 토글한다. 상태를 먼저 읽지 않고 "지워 보고 없으면 넣는다"로 푼다. */
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
        /* HOT 정렬이 posts.like_count 를 읽으므로 재집계로 맞춰 둔다 */
        if ("LIKE".equals(type)) {
            reactionMapper.syncLikeCount(postId);
        }
        PostRow after = postMapper.findById(postId);
        return new ReactionState(active, after == null ? 0 : after.likeCount());
    }

    /** 토글 결과를 담는다. */
    public record ReactionState(boolean active, long likeCount) {
    }

    /** 글의 제목·본문을 수정한다. 작성 시점 값은 그대로 둔다. */
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

    /** 글을 삭제한다. 지우지 않고 status 만 바꾼다. */
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

    /** 작성 규칙(가입 직후 제한·금칙어)을 검사한다. */
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

    /** 자유·질문 글을 저장한다. 종목 관련 값은 전부 null 이다. */
    private Long insertGeneral(Long userId, BoardType board, PostForm form) {
        postMapper.insert(userId, board.name(), null, form.getTitle(), form.getContent(),
                null, null, false, null,
                null, null, null, null, null, null, null);
        return postMapper.findLastInsertedId();
    }

    /** 붙일 매매를 스냅샷으로 뜬다. 매도면 실현손익까지 지금 계산해 박고 매수는 null 로 둔다. */
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

        /* LedgerService 를 거쳐야 분할 보정이 반영된다 */
        HoldingCalculator.Calculation calc = ledgerService.calculationOf(userId, symbol);
        BigDecimal realized = calc.realizedBySellId().get(tx.id());
        BigDecimal cost = calc.costBasisBySellId().get(tx.id());
        return new TradeSnapshot(tx.id(), tx.side(), tx.symbol(), tx.price(), tx.tradedAt(),
                realized, rateOf(realized, cost));
    }

    /** 실현 수익률을 계산한다. 원가가 0 이거나 없으면 null 이다. */
    private static BigDecimal rateOf(BigDecimal realizedKrw, BigDecimal costKrw) {
        if (realizedKrw == null || costKrw == null || costKrw.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return realizedKrw.divide(costKrw, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 지금 주가를 구한다. 벤더가 못 주면 null 이다. */
    private BigDecimal currentPrice(String symbol) {
        return quoteService.quote(symbol).map(q -> q.price()).orElse(null);
    }

    private static PostSummary toSummary(PostRow row) {
        return new PostSummary(row.id(), row.board(), row.symbol(), row.title(),
                excerpt(row.content()), row.authorName(), row.shareholder(),
                row.priceAtWrite(), toTradeCard(row),
                row.likeCount(), row.commentCount(), row.createdAt(), row.status());
    }

    /** 매매 카드를 만든다. 매매를 안 붙인 글이면 null 이다. */
    private static TradeCard toTradeCard(PostRow row) {
        if (row.tradeSide() == null) {
            return null;
        }
        return new TradeCard(row.tradeSide(), row.tradeSymbol(), row.tradePrice(),
                row.tradeAt(), row.tradePnlKrw(), row.tradePnlRate());
    }

    /** 본문 앞머리를 잘라 만든다. */
    private static String excerpt(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.replace('\n', ' ').strip();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH) + "…";
    }

    /** 아는 정렬 값만 통과시킨다. */
    private static String normalizeSort(String sort) {
        return "HOT".equalsIgnoreCase(sort) ? "HOT" : "NEW";
    }

    private static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }

    /** 글에 박아 둘 매매 값 묶음이다. */
    private record TradeSnapshot(Long txId, String side, String symbol, BigDecimal price,
                                 java.time.LocalDateTime tradedAt,
                                 BigDecimal realizedPnlKrw, BigDecimal realizedPnlRate) {

        static TradeSnapshot none() {
            return new TradeSnapshot(null, null, null, null, null, null, null);
        }
    }
}
