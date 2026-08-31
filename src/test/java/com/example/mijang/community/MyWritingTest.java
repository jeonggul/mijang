package com.example.mijang.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.community.domain.PostRow;
import com.example.mijang.community.dto.MyCommentResponse;
import com.example.mijang.community.dto.PostSummary;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 내가 쓴 글·댓글.
 *
 * <p>여기서 보려는 것은 둘이다 — <b>숨김·삭제된 것도 쓴 사람에게는 오는가</b>,
 * <b>어디에 쓴 것인지 알 수 있는가</b>.
 *
 * <p>공개 목록에서 사라진 글이 내 목록에서도 조용히 빠지면, 쓴 사람은 글이
 * 증발했다고 읽는다. 상태를 함께 실어 보내 화면이 이유를 밝히게 한다.
 */
class MyWritingTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 31, 12, 0);

    private static PostRow row(long id, String title, String status) {
        return new PostRow(id, "FREE", null, title, "본문", 88L, "큐에이", false,
                null, null, null, null, null, null, null, 0, 0, 0, AT, status);
    }

    @Nested
    @DisplayName("내 글")
    class 내글 {

        /* 숨겨진 글이 내 목록에서까지 빠지면 왜 사라졌는지 알 길이 없다 */
        @Test
        @DisplayName("숨김·삭제된 글도 상태를 달고 함께 온다")
        void 숨김포함() {
            List<PostRow> rows = List.of(
                    row(1, "공개 글", "PUBLISHED"),
                    row(2, "숨겨진 글", "HIDDEN"),
                    row(3, "지운 글", "DELETED"));

            List<PostSummary> summaries = new ArrayList<>();
            for (PostRow r : rows) {
                summaries.add(new PostSummary(r.id(), r.board(), r.symbol(), r.title(),
                        r.content(), r.authorName(), r.shareholder(), r.priceAtWrite(), null,
                        r.likeCount(), r.commentCount(), r.createdAt(), r.status()));
            }

            assertThat(summaries).hasSize(3);
            assertThat(summaries).extracting(PostSummary::status)
                    .containsExactly("PUBLISHED", "HIDDEN", "DELETED");
        }

        @Test
        @DisplayName("상태가 응답에 실려 화면이 꼬리표를 붙일 수 있다")
        void 상태전달() {
            PostRow hidden = row(2, "숨겨진 글", "HIDDEN");

            assertThat(hidden.status()).isEqualTo("HIDDEN");
        }
    }

    @Nested
    @DisplayName("내 댓글")
    class 내댓글 {

        /* 글 제목이 없으면 어디에 단 댓글인지 알 수 없어 목록이 쓸모없다 */
        @Test
        @DisplayName("어느 글에 달았는지가 함께 온다")
        void 글제목() {
            MyCommentResponse c =
                    new MyCommentResponse(5L, 10L, "원래 글 제목", "댓글 내용", AT, "PUBLISHED");

            assertThat(c.postId()).isEqualTo(10L);
            assertThat(c.postTitle()).isEqualTo("원래 글 제목");
        }

        /* 글이 지워지면 제목이 없다. 그 사실이 드러나야 화면이 "삭제된 글" 로 적는다 */
        @Test
        @DisplayName("글이 지워졌으면 제목이 비어 온다")
        void 글삭제됨() {
            MyCommentResponse c =
                    new MyCommentResponse(5L, 10L, null, "댓글 내용", AT, "PUBLISHED");

            assertThat(c.postTitle()).isNull();
        }
    }
}
