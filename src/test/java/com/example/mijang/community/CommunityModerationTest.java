package com.example.mijang.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.community.domain.PostRow;
import com.example.mijang.community.dto.ReportForm;
import com.example.mijang.community.mapper.PostMapper;
import com.example.mijang.community.mapper.ReactionMapper;
import com.example.mijang.community.mapper.ReportMapper;
import com.example.mijang.community.service.PostService;
import com.example.mijang.community.service.ReportService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 반응·신고·수정·삭제 규칙.
 *
 * <p>여기서 보려는 것은 셋이다 — <b>토글이 상태를 먼저 읽지 않는가</b>(지워 보고 없으면
 * 넣는다), <b>남의 글은 못 건드리는가</b>, <b>같은 신고가 두 번 쌓이지 않는가</b>.
 * 저장 규칙은 {@link PostServiceTest}, 관리자 처리는 실서버로 확인했다.
 */
class CommunityModerationTest {

    private static PostRow post(Long authorId) {
        return new PostRow(10L, "FREE", null, "제목", "본문", authorId, "정하", false,
                null, null, null, null, null, null, null, 0, 0, 0,
                LocalDateTime.of(2026, 8, 26, 12, 0));
    }

    /** 반응 표 흉내. 지금 켜져 있는 반응을 셋으로 든다. */
    private static class Reactions implements ReactionMapper {
        final java.util.Set<String> active = new java.util.HashSet<>();
        int syncCalls;

        @Override public int delete(Long postId, Long userId, String type) {
            return active.remove(type) ? 1 : 0;
        }
        @Override public int insert(Long postId, Long userId, String type) {
            active.add(type);
            return 1;
        }
        @Override public int syncLikeCount(Long postId) { syncCalls++; return 1; }
        @Override public List<String> findTypes(Long postId, Long userId) {
            return List.copyOf(active);
        }
    }

    /** 글 한 건과 상태 변경만 붙잡는 가짜. */
    private static class Posts implements PostMapper {
        PostRow found = post(1L);
        String updatedTitle;
        String updatedStatus;

        @Override public PostRow findById(Long postId) { return found; }
        @Override public int updateContent(Long postId, String title, String content) {
            updatedTitle = title;
            return 1;
        }
        @Override public int updateStatus(Long postId, String status) {
            updatedStatus = status;
            return 1;
        }
        @Override public int insert(Long u, String b, String s, String t, String c,
                java.math.BigDecimal p, java.math.BigDecimal f, boolean sb,
                java.math.BigDecimal h, Long tx, String ts, String tsym,
                java.math.BigDecimal tp, LocalDateTime ta,
                java.math.BigDecimal pn, java.math.BigDecimal pr) { return 1; }
        @Override public Long findLastInsertedId() { return 10L; }
        @Override public List<PostRow> findByBoard(String b, String s, int l, int o) { return List.of(); }
        @Override public long countByBoard(String board) { return 0; }
        @Override public List<PostRow> findBySymbol(String s, String so, int l, int o) { return List.of(); }
        @Override public long countBySymbol(String symbol) { return 0; }
        @Override public int increaseViewCount(Long postId) { return 1; }
        @Override public int increaseCommentCount(Long postId) { return 1; }
    }

    /** 신고 표 흉내. (신고자·대상) 짝을 기억한다. */
    private static class Reports implements ReportMapper {
        final List<String> saved = new ArrayList<>();

        @Override public int countByReporterAndTarget(Long userId, String type, Long id) {
            return saved.contains(userId + "|" + type + "|" + id) ? 1 : 0;
        }
        @Override public int insert(Long userId, String type, Long id, String reason, String detail) {
            saved.add(userId + "|" + type + "|" + id);
            return 1;
        }
        @Override public Long findLastInsertedId() { return 1L; }
    }

    private Posts posts;
    private Reactions reactions;

    private PostService service() {
        posts = new Posts();
        reactions = new Reactions();
        /* 조회·저장 경로는 이 테스트의 관심사가 아니라 null 로 둔다. 부르면 터져서 오히려 잡힌다 */
        return new PostService(posts, null, reactions, null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("반응 토글")
    class 토글 {

        @Test
        @DisplayName("없으면 켜지고 다시 누르면 꺼진다")
        void 왕복() {
            PostService s = service();

            assertThat(s.toggleReaction(2L, 10L, "LIKE").active()).isTrue();
            assertThat(s.toggleReaction(2L, 10L, "LIKE").active()).isFalse();
        }

        /* HOT 정렬이 posts.like_count 를 읽는다. 반응과 함께 맞춰 두지 않으면 정렬이 낡는다 */
        @Test
        @DisplayName("좋아요는 켤 때도 끌 때도 카운트를 다시 센다")
        void 재집계() {
            PostService s = service();

            s.toggleReaction(2L, 10L, "LIKE");
            s.toggleReaction(2L, 10L, "LIKE");

            assertThat(reactions.syncCalls).isEqualTo(2);
        }

        @Test
        @DisplayName("스크랩은 좋아요 수를 건드리지 않는다")
        void 스크랩() {
            PostService s = service();

            s.toggleReaction(2L, 10L, "SCRAP");

            assertThat(reactions.syncCalls).isZero();
        }

        @Test
        @DisplayName("없는 글이면 404")
        void 없는글() {
            PostService s = service();
            posts.found = null;

            assertThatThrownBy(() -> s.toggleReaction(2L, 10L, "LIKE"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("수정·삭제 — 남의 글은 못 건드린다")
    class 소유권 {

        @Test
        @DisplayName("본인 글은 수정된다")
        void 본인수정() {
            PostService s = service();

            s.update(1L, 10L, "새 제목", "새 본문");

            assertThat(posts.updatedTitle).isEqualTo("새 제목");
        }

        @Test
        @DisplayName("남의 글 수정은 403")
        void 남의글수정() {
            PostService s = service();

            assertThatThrownBy(() -> s.update(2L, 10L, "탈취", "x"))
                    .isInstanceOf(BusinessException.class);
            assertThat(posts.updatedTitle).isNull();
        }

        /* 지우지 않는다(2.6). 댓글과 신고가 이 글을 참조한다 */
        @Test
        @DisplayName("삭제는 status 만 바꾼다")
        void 본인삭제() {
            PostService s = service();

            s.delete(1L, 10L);

            assertThat(posts.updatedStatus).isEqualTo("DELETED");
        }

        @Test
        @DisplayName("남의 글 삭제는 403")
        void 남의글삭제() {
            PostService s = service();

            assertThatThrownBy(() -> s.delete(2L, 10L))
                    .isInstanceOf(BusinessException.class);
            assertThat(posts.updatedStatus).isNull();
        }
    }

    @Nested
    @DisplayName("신고")
    class 신고 {

        private ReportForm form() {
            ReportForm f = new ReportForm();
            f.setTargetType("POST");
            f.setTargetId(10L);
            f.setReason("SPAM");
            return f;
        }

        @Test
        @DisplayName("접수되면 id 가 돌아온다")
        void 접수() {
            Posts posts = new Posts();
            ReportService s = new ReportService(new Reports(), posts);

            assertThat(s.create(2L, form())).isEqualTo(1L);
        }

        /* 하나면 관리자가 보기에 충분하다. 여러 개면 눌러 대는 것과 여럿의 신고가 안 갈린다 */
        @Test
        @DisplayName("같은 대상을 두 번 신고하면 409")
        void 중복() {
            ReportService s = new ReportService(new Reports(), new Posts());
            s.create(2L, form());

            assertThatThrownBy(() -> s.create(2L, form()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("없는 글 신고는 404")
        void 없는대상() {
            Posts posts = new Posts();
            posts.found = null;
            ReportService s = new ReportService(new Reports(), posts);

            assertThatThrownBy(() -> s.create(2L, form()))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
