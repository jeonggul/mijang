package com.example.mijang.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.community.policy.CommunityPolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 커뮤니티 작성 규칙.
 *
 * <p>운영 설정이 켜 둔 두 가지를 본다 — 가입 직후 글쓰기 제한과 금칙어.
 * 둘 다 <b>막지 말아야 할 때 막지 않는지</b>가 더 중요하다. 잘못 막으면 사용자는
 * 이유를 모른 채 글을 못 쓴다.
 */
class CommunityPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 12, 0);

    @Nested
    @DisplayName("가입 직후 글쓰기 제한")
    class 글쓰기제한 {

        @Test
        @DisplayName("제한이 0 이면 막지 않는다")
        void 제한없음() {
            assertThat(CommunityPolicy.tooEarlyToWrite(NOW.minusMinutes(1), 0, NOW)).isFalse();
        }

        /* 가입 시각을 모르는데 막으면 사용자는 영문을 모른 채 글을 못 쓴다 */
        @Test
        @DisplayName("가입 시각을 모르면 막지 않는다")
        void 가입시각모름() {
            assertThat(CommunityPolicy.tooEarlyToWrite(null, 3, NOW)).isFalse();
        }

        @Test
        @DisplayName("하루가 안 지났으면 막는다")
        void 하루전() {
            assertThat(CommunityPolicy.tooEarlyToWrite(NOW.minusHours(23), 1, NOW)).isTrue();
        }

        @Test
        @DisplayName("하루가 지났으면 통과한다")
        void 하루후() {
            assertThat(CommunityPolicy.tooEarlyToWrite(NOW.minusHours(25), 1, NOW)).isFalse();
        }

        @Test
        @DisplayName("경계 — 정확히 제한 일수를 채우면 통과한다")
        void 경계() {
            assertThat(CommunityPolicy.tooEarlyToWrite(NOW.minusDays(3), 3, NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("금칙어")
    class 금칙어 {

        @Test
        @DisplayName("평범한 글은 통과한다")
        void 정상() {
            assertThat(CommunityPolicy.containsBannedWord(
                    "실적 발표 어떻게 보시나요", "환율이 부담이라 분할로 접근 중입니다")).isFalse();
        }

        @Test
        @DisplayName("리딩방은 잡는다")
        void 리딩방() {
            assertThat(CommunityPolicy.containsBannedWord("단타 리딩방 들어오세요", null)).isTrue();
        }

        /* 한 칸만 벌려도 통과하면 사전이 무의미하다 */
        @Test
        @DisplayName("띄어쓰기로 피해 가도 잡는다")
        void 띄어쓰기우회() {
            assertThat(CommunityPolicy.containsBannedWord("리 딩 방 안내", null)).isTrue();
        }

        @Test
        @DisplayName("본문에만 있어도 잡는다")
        void 본문() {
            assertThat(CommunityPolicy.containsBannedWord("좋은 종목", "수익보장 해드립니다")).isTrue();
        }

        @Test
        @DisplayName("null 은 건너뛴다")
        void 널() {
            assertThat(CommunityPolicy.containsBannedWord(null, null)).isFalse();
        }
    }
}
