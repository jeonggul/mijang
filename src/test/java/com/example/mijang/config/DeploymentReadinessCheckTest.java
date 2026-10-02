package com.example.mijang.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배포용 설정 점검.
 *
 * <p>로그를 읽어 확인하지는 않는다 — 여기서 지키려는 것은 <b>무엇을 걸러 내는가</b> 하나다.
 * 판정 규칙이 조용히 느슨해지면(예: localhost 만 보고 127.0.0.1 을 놓치면) 개발 기본값이
 * 그대로 배포로 넘어가는데, 그때는 아무 로그도 안 뜬다.
 */
class DeploymentReadinessCheckTest {

    @Test
    @DisplayName("내 컴퓨터를 가리키는 주소를 전부 잡는다")
    void 로컬주소를잡는다() {
        var check = new DeploymentReadinessCheck(null, null, null);

        assertThat(check.localAddress("http://localhost:8080")).isTrue();
        assertThat(check.localAddress("http://127.0.0.1:8080")).isTrue();
        assertThat(check.localAddress("http://[::1]:8080")).isTrue();
        assertThat(check.localAddress("HTTP://LOCALHOST:8080")).isTrue();
    }

    @Test
    @DisplayName("바깥에서 열리는 주소는 통과시킨다")
    void 실제도메인은통과() {
        var check = new DeploymentReadinessCheck(null, null, null);

        assertThat(check.localAddress("https://mijang.app")).isFalse();
        assertThat(check.localAddress("https://www.mijang.app/reset")).isFalse();
    }

    @Test
    @DisplayName("주소가 비어 있으면 경고하지 않는다 — 다른 곳에서 걸린다")
    void 빈값() {
        var check = new DeploymentReadinessCheck(null, null, null);

        assertThat(check.localAddress(null)).isFalse();
        assertThat(check.localAddress("")).isFalse();
    }
}
