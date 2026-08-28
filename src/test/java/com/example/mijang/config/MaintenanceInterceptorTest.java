package com.example.mijang.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.support.FixedSettings;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 점검 모드.
 *
 * <p>여기서 보려는 것은 셋이다 — <b>꺼져 있으면 아무것도 막지 않는가</b>,
 * <b>관리자와 로그인 길은 열려 있는가</b>, 그리고
 * <b>화면 요청이 JSON 이 아니라 점검 화면으로 가는가</b>.
 *
 * <p>마지막이 이 시험의 이유다. 예외로만 던지던 때는 처리기가 JSON 봉투밖에 만들지
 * 못해 브라우저의 {@code Accept: text/html} 과 협상에 실패했고, 그 실패가 500 으로
 * 바뀌어 "페이지를 찾을 수 없습니다" 가 대신 떴다.
 */
class MaintenanceInterceptorTest {

    @AfterEach
    void 정리() {
        SecurityContextHolder.clearContext();
    }

    private static MaintenanceInterceptor 점검(boolean on) {
        return new MaintenanceInterceptor(
                new FixedSettings().with(AdminSettingKey.MAINTENANCE_ENABLED, String.valueOf(on)));
    }

    private static MockHttpServletRequest 요청(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setRequestURI(path);
        return req;
    }

    private static void 로그인(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u", "n",
                        List.of(new SimpleGrantedAuthority(role))));
    }

    @Nested
    @DisplayName("꺼져 있을 때")
    class 꺼짐 {

        @Test
        @DisplayName("아무것도 막지 않는다")
        void 통과() throws Exception {
            assertThat(점검(false).preHandle(요청("/dashboard"), new MockHttpServletResponse(), null))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("켜져 있을 때")
    class 켜짐 {

        /* 막아 두면 점검 모드를 켠 사람이 그것을 끌 수 없다 */
        @Test
        @DisplayName("관리자 화면과 로그인 길은 열려 있다")
        void 열린길() throws Exception {
            MaintenanceInterceptor i = 점검(true);
            for (String path : List.of("/admin", "/api/admin/settings", "/login",
                                       "/api/auth/login", "/css/style.css", "/maintenance")) {
                assertThat(i.preHandle(요청(path), new MockHttpServletResponse(), null))
                        .as(path)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("관리자는 어디든 통과한다")
        void 관리자() throws Exception {
            로그인("ROLE_ADMIN");

            assertThat(점검(true).preHandle(요청("/dashboard"), new MockHttpServletResponse(), null))
                    .isTrue();
        }

        @Test
        @DisplayName("API 는 503 봉투로 막는다")
        void API() {
            로그인("ROLE_USER");

            assertThatThrownBy(() ->
                    점검(true).preHandle(요청("/api/portfolio/holdings"), new MockHttpServletResponse(), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("점검");
        }

        /* JSON 봉투로 답하면 브라우저와 협상에 실패해 500 "페이지를 찾을 수 없습니다" 가 뜬다 */
        @Test
        @DisplayName("화면은 503 으로 점검 화면을 보여 준다")
        void 화면() throws Exception {
            로그인("ROLE_USER");
            MockHttpServletRequest req = 요청("/dashboard");
            MockHttpServletResponse res = new MockHttpServletResponse();

            boolean 계속 = 점검(true).preHandle(req, res, null);

            assertThat(계속).isFalse();
            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertThat(res.getForwardedUrl()).isEqualTo("/maintenance");
        }
    }
}
