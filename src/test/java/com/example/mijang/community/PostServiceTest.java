package com.example.mijang.community;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.support.FixedSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.community.domain.BoardType;
import com.example.mijang.community.domain.PostRow;
import com.example.mijang.community.dto.CommentResponse;
import com.example.mijang.community.dto.PostForm;
import com.example.mijang.community.mapper.CommentMapper;
import com.example.mijang.community.mapper.PostMapper;
import com.example.mijang.community.service.PostService;
import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.market.dto.QuoteResponse;
import com.example.mijang.market.service.QuoteService;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.stock.mapper.StockMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 게시글 저장 규칙.
 *
 * <p>DB 도 스프링도 부르지 않는다. 여기서 보려는 것은 넷이다 —
 * <b>게시판마다 무엇을 박는가</b>, <b>붙인 매매가 정말 내 것이고 이 종목인가</b>,
 * <b>매수에는 손익을 넣지 않는가</b>, <b>"주주" 배지가 보유 여부를 따르는가</b>.
 */
class PostServiceTest {

    private static final Stock APPLE = new Stock(1L, "AAPL", "Apple Inc.", "애플", "NASDAQ",
            "STOCK", "Technology", null, true, true, null, LocalDateTime.of(2026, 8, 1, 0, 0));

    private static final LocalDateTime SOLD_AT = LocalDateTime.of(2026, 8, 22, 7, 44);

    /** 저장된 인자를 그대로 붙잡아 두는 가짜 게시글 매퍼. */
    private static class Posts implements PostMapper {
        @Override public java.util.List<com.example.mijang.community.domain.PostRow> findByUser(
                Long userId, int limit, int offset) { return java.util.List.of(); }

        @Override public long countByUser(Long userId) { return 0; }

        String board;
        String symbol;
        BigDecimal priceAtWrite;
        boolean showHoldingBadge;
        BigDecimal holdingQtyAtWrite;
        Long tradeTxId;
        String tradeSide;
        BigDecimal tradePnlKrw;
        BigDecimal tradePnlRate;
        PostRow found;

        @Override public int insert(Long userId, String board, String symbol, String title,
                                    String content, BigDecimal priceAtWrite, BigDecimal fxAtWrite,
                                    boolean showHoldingBadge, BigDecimal holdingQtyAtWrite,
                                    Long tradeTxId, String tradeSide, String tradeSymbol,
                                    BigDecimal tradePrice, LocalDateTime tradeAt,
                                    BigDecimal tradePnlKrw, BigDecimal tradePnlRate) {
            this.board = board;
            this.symbol = symbol;
            this.priceAtWrite = priceAtWrite;
            this.showHoldingBadge = showHoldingBadge;
            this.holdingQtyAtWrite = holdingQtyAtWrite;
            this.tradeTxId = tradeTxId;
            this.tradeSide = tradeSide;
            this.tradePnlKrw = tradePnlKrw;
            this.tradePnlRate = tradePnlRate;
            return 1;
        }

        @Override public Long findLastInsertedId() { return 7L; }
        @Override public int updateContent(Long postId, String title, String content) { return 1; }
        @Override public int updateStatus(Long postId, String status) { return 1; }
        @Override public int updateStatusIfPublished(Long postId, String status) {
            return updateStatus(postId, status);
        }
        @Override public List<PostRow> findByBoard(String b, String s, int l, int o) { return List.of(); }
        @Override public long countByBoard(String board) { return 0; }
        @Override public List<PostRow> findBySymbol(String s, String so, int l, int o) { return List.of(); }
        @Override public long countBySymbol(String symbol) { return 0; }
        @Override public PostRow findById(Long postId) { return found; }
        @Override public int increaseViewCount(Long postId) { return 1; }
        @Override public int increaseCommentCount(Long postId) { return 1; }
    }

    private static class Comments implements CommentMapper {
        @Override public java.util.List<com.example.mijang.community.dto.MyCommentResponse> findByUser(
                Long userId, int limit, int offset) { return java.util.List.of(); }

        @Override public long countByUser(Long userId) { return 0; }

        @Override public int insert(Long p, Long u, Long parent, String c) { return 1; }
        @Override public Long findLastInsertedId() { return 1L; }
        @Override public List<CommentResponse> findByPost(Long postId) { return List.of(); }
        @Override public Long findRepliableParentPostId(Long commentId) { return null; }
    }

    /** 종목이 있는지만 답한다. 나머지는 이 시험이 부르지 않는다. */
    private static class Stocks implements StockMapper {
        Stock found = APPLE;

        @Override public Stock findBySymbol(String symbol) { return found; }

        @Override public List<StockSearchResponse> searchByPrefix(String q, int limit) { return List.of(); }
        @Override public List<StockSearchResponse> findByFilter(String e, String a,
                                                                int o, int l) { return List.of(); }
        @Override public int countByFilter(String exchange, String assetClass) { return 0; }
        @Override public int upsert(String symbol, String name, String exchange,
                                    String assetClass, boolean fractionable) { return 1; }
        @Override public LocalDateTime now() { return LocalDateTime.of(2026, 8, 24, 12, 0); }
        @Override public List<StockSearchResponse> findForAdmin(Boolean a, String c, String q,
                                                                int l, int o) { return List.of(); }
        @Override public int countForAdmin(Boolean active, String assetClass, String q) { return 0; }
        @Override public int setActive(String symbol, boolean active, String reason) { return 1; }
        @Override public int deactivateNotSyncedSince(LocalDateTime threshold) { return 0; }
        @Override public List<String> findActiveSymbols() { return List.of(); }
        @Override public int updateNameKo(String symbol, String nameKo) { return 1; }
        @Override public int countWithNameKo() { return 0; }
        @Override public int updateSecurityType(String symbol, String securityType,
                                                String isin, String assetClass) { return 1; }
    }

    private static class Transactions implements TransactionMapper {
        @Override public int update(Long id, Long userId, String symbol, String side,
                                    java.math.BigDecimal quantity, java.math.BigDecimal price,
                                    java.math.BigDecimal fxRate, java.math.BigDecimal fee,
                                    java.time.LocalDateTime tradedAt, java.time.LocalDate tradeDate,
                                    String buyReason, java.math.BigDecimal targetPrice,
                                    String sentiment) { return 1; }

        Transaction found;
        final List<Transaction> ledger = new ArrayList<>();

        @Override public Transaction findById(Long id, Long userId) { return found; }
        @Override public List<Transaction> findForRecalc(Long userId, String symbol) { return ledger; }

        @Override public int insert(Long u, Long p, String s, String side, BigDecimal q,
                                    BigDecimal price, BigDecimal fx, BigDecimal fee,
                                    LocalDateTime at, LocalDate d, String reason,
                                    BigDecimal target, String sentiment) { return 1; }
        @Override public Long findLastInsertedId() { return 1L; }
        @Override public List<TransactionResponse> findByUser(Long u, String s,
                                                              int l, int o) { return List.of(); }
        @Override public List<TransactionResponse> findForExport(
                Long u, String s, String side, LocalDate from, LocalDate to) { return List.of(); }
        @Override public long countByUser(Long userId, String symbol) { return 0; }
        @Override public int softDelete(Long id, Long userId) { return 1; }
        @Override public List<String> findSymbolsByUser(Long userId) { return List.of(); }
    }

    private static class Holdings implements HoldingMapper {
        BigDecimal quantity;

        @Override public BigDecimal findQuantity(Long userId, String symbol) { return quantity; }

        @Override public List<HoldingResponse> findByUser(Long u, BigDecimal fx) { return List.of(); }
        @Override public int upsert(Long u, Long p, String s, BigDecimal q, BigDecimal ap,
                                    BigDecimal afx, BigDecimal fee, BigDecimal r) { return 1; }
        @Override public BigDecimal sumMarketValueKrw(Long u, BigDecimal fx) { return null; }
        @Override public List<SymbolPnl> findForPnl(Long u, String s) { return List.of(); }
        @Override public List<SymbolPnl> findForPnlAsOf(Long u, String s, LocalDate d) { return List.of(); }
    }

    private final Posts posts = new Posts();
    private final Stocks stocks = new Stocks();
    private final Transactions transactions = new Transactions();
    private final Holdings holdings = new Holdings();

    /**
     * 시세·환율은 값 하나만 필요해서 그 메서드만 덮어쓴다.
     *
     * <p>둘 다 구체 클래스라 생성자에 null 을 넣는다 — 덮어쓴 메서드 말고는 부르지 않는다.
     */
    private final QuoteService quotes = new QuoteService(null, null, null, null, new FixedSettings()) {
        @Override public Optional<QuoteResponse> quote(String symbol) {
            return Optional.of(new QuoteResponse(symbol, new BigDecimal("196.40"),
                    Instant.parse("2026-08-24T03:00:00Z"), true, false));
        }
    };

    private final FxRateService fx = new FxRateService(null, null, null, new FixedSettings()) {
        @Override public BigDecimal rateOf(LocalDate date) { return new BigDecimal("1380.0000"); }
        @Override public Optional<FxRateResponse> latest() { return Optional.empty(); }
    };

    /* 반응은 이 테스트의 관심사가 아니라 빈 가짜를 준다 */
    private static class Reactions implements com.example.mijang.community.mapper.ReactionMapper {
        @Override public int delete(Long postId, Long userId, String type) { return 0; }
        @Override public int insert(Long postId, Long userId, String type) { return 1; }
        @Override public int syncLikeCount(Long postId) { return 1; }
        @Override public java.util.List<String> findTypes(Long postId, Long userId) {
            return java.util.List.of();
        }
    }

    /* 운영 설정의 글쓰기 제한을 끈다. 이 시험의 관심사가 아니고, 켜 두면 가입일이
       없는 가짜 사용자 때문에 전부 막힌다 */
    private final PostService service = new PostService(posts, new Comments(), new Reactions(),
            stocks, transactions, holdings, quotes, fx, new TradingClock(),
            new FixedSettings().with(AdminSettingKey.COMMUNITY_WRITE_DELAY_DAYS, "0"), null);

    private static PostForm form() {
        PostForm form = new PostForm();
        form.setTitle("인도 생산 확대 뉴스 어떻게 보시나요");
        form.setContent("환율 때문에 원화 수익률은 생각보다 별로네요");
        return form;
    }

    private static Transaction sell(BigDecimal quantity, BigDecimal price) {
        return new Transaction(9L, 1L, 1L, "AAPL", "SELL", quantity, price,
                new BigDecimal("1400.0000"), BigDecimal.ZERO, SOLD_AT,
                LocalDate.of(2026, 8, 22), null, null, null);
    }

    private static Transaction buy(BigDecimal quantity, BigDecimal price, Long id) {
        return new Transaction(id, 1L, 1L, "AAPL", "BUY", quantity, price,
                new BigDecimal("1300.0000"), BigDecimal.ZERO,
                LocalDateTime.of(2026, 5, 2, 1, 0), LocalDate.of(2026, 5, 1),
                null, null, null);
    }

    @Nested
    @DisplayName("게시판 구분")
    class Boards {

        @Test
        @DisplayName("자유 글에는 종목도 작성 시점 주가도 붙지 않는다")
        void generalPostCarriesNoStock() {
            service.create(1L, BoardType.FREE, null, form());

            assertThat(posts.board).isEqualTo("FREE");
            assertThat(posts.symbol).isNull();
            assertThat(posts.priceAtWrite).isNull();
            assertThat(posts.showHoldingBadge).isFalse();
        }

        @Test
        @DisplayName("종목별 글에는 작성 시점 주가를 서버가 구해서 박는다")
        void stockPostSnapshotsPrice() {
            service.create(1L, BoardType.STOCK, "aapl", form());

            assertThat(posts.board).isEqualTo("STOCK");
            assertThat(posts.symbol).isEqualTo("AAPL");     // 소문자로 와도 정규화된다
            assertThat(posts.priceAtWrite).isEqualByComparingTo("196.40");
        }

        @Test
        @DisplayName("없는 종목의 게시판에는 쓸 수 없다")
        void unknownSymbolIsRejected() {
            stocks.found = null;

            assertThatThrownBy(() -> service.create(1L, BoardType.STOCK, "ZZZZ", form()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("주주 배지")
    class Badge {

        @Test
        @DisplayName("켰고 보유 중이면 배지가 붙는다")
        void badgeWhenHeld() {
            holdings.quantity = new BigDecimal("12");
            PostForm form = form();
            form.setShowHoldingBadge(true);

            service.create(1L, BoardType.STOCK, "AAPL", form);

            assertThat(posts.showHoldingBadge).isTrue();
        }

        @Test
        @DisplayName("켰어도 전량 매도했으면 배지가 붙지 않는다")
        void noBadgeWhenSoldOut() {
            holdings.quantity = BigDecimal.ZERO;
            PostForm form = form();
            form.setShowHoldingBadge(true);

            service.create(1L, BoardType.STOCK, "AAPL", form);

            assertThat(posts.showHoldingBadge).isFalse();
        }

        @Test
        @DisplayName("끄면 보유 수량도 남기지 않는다")
        void offKeepsNothing() {
            holdings.quantity = new BigDecimal("12");

            service.create(1L, BoardType.STOCK, "AAPL", form());

            assertThat(posts.showHoldingBadge).isFalse();
            assertThat(posts.holdingQtyAtWrite).isNull();
        }
    }

    @Nested
    @DisplayName("매매 카드")
    class Trade {

        @Test
        @DisplayName("매도는 실현손익과 수익률을 등록 시점에 박는다")
        void sellCarriesRealizedPnl() {
            // 10주를 $100 · 1,300원에 사고 5주를 $120 · 1,400원에 팔았다
            transactions.ledger.add(buy(new BigDecimal("10"), new BigDecimal("100"), 1L));
            Transaction sold = sell(new BigDecimal("5"), new BigDecimal("120"));
            transactions.ledger.add(sold);
            transactions.found = sold;

            PostForm form = form();
            form.setTradeTxId(9L);
            service.create(1L, BoardType.STOCK, "AAPL", form);

            // 5 × (120 × 1,400 − 100 × 1,300) = 190,000원, 원가 5 × 100 × 1,300 = 650,000원
            assertThat(posts.tradeSide).isEqualTo("SELL");
            assertThat(posts.tradePnlKrw).isEqualByComparingTo("190000");
            assertThat(posts.tradePnlRate).isEqualByComparingTo("0.2923");
        }

        @Test
        @DisplayName("매수에는 손익을 넣지 않는다 — 산 순간에는 확정된 손익이 없다")
        void buyCarriesNoPnl() {
            Transaction bought = buy(new BigDecimal("10"), new BigDecimal("100"), 9L);
            transactions.ledger.add(bought);
            transactions.found = bought;

            PostForm form = form();
            form.setTradeTxId(9L);
            service.create(1L, BoardType.STOCK, "AAPL", form);

            assertThat(posts.tradeSide).isEqualTo("BUY");
            assertThat(posts.tradePnlKrw).isNull();
            assertThat(posts.tradePnlRate).isNull();
        }

        @Test
        @DisplayName("남의 기록은 붙일 수 없다")
        void foreignTradeIsRejected() {
            transactions.found = null;      // 매퍼가 user_id 로 걸러 없는 것으로 본다
            PostForm form = form();
            form.setTradeTxId(9L);

            assertThatThrownBy(() -> service.create(1L, BoardType.STOCK, "AAPL", form))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("다른 종목의 매매는 붙일 수 없다 — 읽는 사람이 이 종목 수익률로 읽는다")
        void otherSymbolTradeIsRejected() {
            transactions.found = new Transaction(9L, 1L, 1L, "NVDA", "SELL",
                    new BigDecimal("5"), new BigDecimal("120"), new BigDecimal("1400.0000"),
                    BigDecimal.ZERO, SOLD_AT, LocalDate.of(2026, 8, 22), null, null, null);
            PostForm form = form();
            form.setTradeTxId(9L);

            assertThatThrownBy(() -> service.create(1L, BoardType.STOCK, "AAPL", form))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("고르지 않으면 카드가 붙지 않는다")
        void noTradeByDefault() {
            service.create(1L, BoardType.STOCK, "AAPL", form());

            assertThat(posts.tradeTxId).isNull();
            assertThat(posts.tradeSide).isNull();
        }
    }
}
