package com.example.mijang.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.community.domain.PostRow;
import com.example.mijang.community.dto.CommentResponse;
import com.example.mijang.community.dto.MyCommentResponse;
import com.example.mijang.community.mapper.CommentMapper;
import com.example.mijang.community.mapper.PostMapper;
import com.example.mijang.community.mapper.ReactionMapper;
import com.example.mijang.community.service.PostService;
import com.example.mijang.support.FixedSettings;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 글 상세가 누구에게 보이는가.
 *
 * <p>2026-09-03 점검 5.3 — 내가 쓴 글 목록({@code findByUser})은 숨김·삭제된 글도 주는데
 * 상세({@code findById})는 {@code PUBLISHED} 만 줘서, <b>목록에는 보이는데 열면 404</b> 였다.
 * "목록에서 사라진 이유를 쓴 사람은 알 수 있어야 한다" 는 의도가 반쯤만 이뤄져 있었다.
 *
 * <p>여기서 지키는 것은 셋이다 — <b>본인은 내려간 글도 연다</b>,
 * <b>남에게는 여전히 없는 글이다</b>, <b>내려간 글의 조회수는 오르지 않는다</b>.
 */
class PostVisibilityTest {

    private static final Long AUTHOR = 1L;
    private static final Long STRANGER = 2L;

    @Test
    @DisplayName("본인이 열면 숨겨진 글도 상태와 함께 열린다")
    void 본인은숨김글을연다() {
        var posts = new Posts(post("HIDDEN"));

        var detail = service(posts).detail(AUTHOR, 10L);

        assertThat(detail.status()).isEqualTo("HIDDEN");
        assertThat(detail.mine()).isTrue();
        assertThat(detail.title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("본인이 열면 삭제한 글도 열린다")
    void 본인은삭제글을연다() {
        var posts = new Posts(post("DELETED"));

        assertThat(service(posts).detail(AUTHOR, 10L).status()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("남이 열면 내려간 글은 없는 글이다")
    void 남에게는안보인다() {
        var posts = new Posts(post("HIDDEN"));

        assertThatThrownBy(() -> service(posts).detail(STRANGER, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("비로그인이 열면 내려간 글은 없는 글이다")
    void 비로그인에게는안보인다() {
        var posts = new Posts(post("HIDDEN"));

        assertThatThrownBy(() -> service(posts).detail(null, 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("내려간 글은 조회수가 오르지 않는다 — 남에게 안 보이니 오를 이유가 없다")
    void 내려간글은조회수가안오른다() {
        var posts = new Posts(post("HIDDEN"));

        var detail = service(posts).detail(AUTHOR, 10L);

        assertThat(posts.viewIncrements).isZero();
        assertThat(detail.viewCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("공개된 글은 조회수가 오르고, 방금 올린 1 이 화면에 함께 나간다")
    void 공개글은조회수가오른다() {
        var posts = new Posts(post("PUBLISHED"));

        var detail = service(posts).detail(STRANGER, 10L);

        assertThat(posts.viewIncrements).isEqualTo(1);
        assertThat(detail.viewCount()).isEqualTo(4);   // 읽은 값 3 + 방금 올린 1
    }

    @Test
    @DisplayName("없는 글은 조회수를 올리지 않는다")
    void 없는글은조회수를안올린다() {
        var posts = new Posts(null);

        assertThatThrownBy(() -> service(posts).detail(AUTHOR, 10L))
                .isInstanceOf(BusinessException.class);
        assertThat(posts.viewIncrements).isZero();
    }

    private static PostRow post(String status) {
        return new PostRow(10L, "FREE", null, "제목", "본문", AUTHOR, "정하", false,
                null, null, null, null, null, null, null, 0, 0, 3,
                LocalDateTime.of(2026, 8, 26, 12, 0), status);
    }

    /* 상세 경로만 도는 시험이라 나머지 협력자는 부르지 않는다.
       null 로 두면 실수로 부를 때 그 자리에서 터져 오히려 잡힌다 */
    private static PostService service(Posts posts) {
        return new PostService(posts, new Comments(), new Reactions(),
                null, null, null, null, null, null, new TradingClock(),
                new FixedSettings().with(AdminSettingKey.COMMUNITY_WRITE_DELAY_DAYS, "0"), null);
    }

    /** 상태를 XML 과 같게 흉내 낸다 — findById 는 PUBLISHED 만, findAnyById 는 전부. */
    private static class Posts implements PostMapper {

        private final PostRow row;
        int viewIncrements;

        Posts(PostRow row) {
            this.row = row;
        }

        @Override public PostRow findById(Long postId) {
            return row != null && "PUBLISHED".equals(row.status()) ? row : null;
        }

        @Override public PostRow findAnyById(Long postId) {
            return row;
        }

        @Override public int increaseViewCount(Long postId) {
            viewIncrements++;
            return 1;
        }

        @Override public int insert(Long u, String b, String s, String t, String c,
                BigDecimal p, BigDecimal f, boolean sb, BigDecimal h, Long tx, String ts,
                String tsym, BigDecimal tp, LocalDateTime ta, BigDecimal pnl,
                BigDecimal rate) { return 0; }
        @Override public Long findLastInsertedId() { return null; }
        @Override public List<PostRow> findByBoard(String b, String s, int l, int o) { return List.of(); }
        @Override public long countByBoard(String board) { return 0; }
        @Override public List<PostRow> findBySymbol(String s, String so, int l, int o) { return List.of(); }
        @Override public long countBySymbol(String symbol) { return 0; }
        @Override public List<PostRow> findByUser(Long userId, int limit, int offset) { return List.of(); }
        @Override public long countByUser(Long userId) { return 0; }
        @Override public int updateContent(Long postId, String title, String content) { return 0; }
        @Override public int updateStatus(Long postId, String status) { return 0; }
        @Override public int updateStatusIfPublished(Long postId, String status) { return 0; }
        @Override public int increaseCommentCount(Long postId) { return 0; }
    }

    /** 댓글은 이 시험의 관심사가 아니다. 빈 목록만 준다. */
    private static class Comments implements CommentMapper {
        @Override public List<CommentResponse> findByPost(Long postId) { return List.of(); }
        @Override public int insert(Long postId, Long userId, Long parentId, String content) { return 0; }
        @Override public Long findLastInsertedId() { return null; }
        @Override public Long findRepliableParentPostId(Long commentId) { return null; }
        @Override public List<MyCommentResponse> findByUser(Long u, int l, int o) { return List.of(); }
        @Override public long countByUser(Long userId) { return 0; }
    }

    /** 반응도 마찬가지다. 아무것도 눌리지 않은 상태로 둔다. */
    private static class Reactions implements ReactionMapper {
        @Override public List<String> findTypes(Long postId, Long userId) { return List.of(); }
        @Override public int delete(Long postId, Long userId, String type) { return 0; }
        @Override public int insert(Long postId, Long userId, String type) { return 0; }
        @Override public int syncLikeCount(Long postId) { return 0; }
    }
}
