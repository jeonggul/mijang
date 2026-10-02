package com.example.mijang.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.mapper.AdminSettingMapper;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 운영 설정.
 *
 * <p>DB 도 스프링도 부르지 않는다. 여기서 보려는 것은 셋이다 —
 * <b>아무 키·값이나 저장되지 않는가</b>, <b>없거나 깨진 값에도 기본값으로 답하는가</b>,
 * <b>바꾸면 다음 읽기에 반영되는가</b>.
 */
class AdminSettingServiceTest {

    /** 표 대신 메모리에 담아 두는 가짜 매퍼. 몇 번 읽었는지도 센다. */
    private static class Settings implements AdminSettingMapper {
        final Map<String, String> rows = new LinkedHashMap<>();
        int reads;

        @Override public List<Map<String, Object>> findAll() {
            reads++;
            List<Map<String, Object>> out = new ArrayList<>();
            rows.forEach((k, v) -> out.add(Map.of("settingKey", k, "settingValue", v)));
            return out;
        }

        @Override public int upsert(String key, String value, Long adminId) {
            rows.put(key, value);
            return 1;
        }
    }

    @Nested
    @DisplayName("저장 — 알려진 키와 받을 수 있는 값만")
    class 저장 {

        @Test
        @DisplayName("모르는 키는 거절한다")
        void 모르는키() {
            AdminSettingService s = new AdminSettingService(new Settings());

            assertThatThrownBy(() -> s.update(1L, "nope.whatever", "true"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("참거짓 자리에 숫자가 오면 거절한다")
        void 잘못된참거짓() {
            AdminSettingService s = new AdminSettingService(new Settings());

            assertThatThrownBy(() -> s.update(1L, "signup.enabled", "5"))
                    .isInstanceOf(BusinessException.class);
        }

        /* 허용 목록 밖 숫자를 받으면 화면에 없는 값이 표에 남는다 */
        @Test
        @DisplayName("허용 목록에 없는 숫자는 거절한다")
        void 목록밖숫자() {
            AdminSettingService s = new AdminSettingService(new Settings());

            assertThatThrownBy(() -> s.update(1L, "news.refresh.minutes", "45"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("대소문자와 공백은 다듬어 저장한다")
        void 다듬기() {
            Settings rows = new Settings();

            new AdminSettingService(rows).update(1L, "signup.enabled", "  TRUE  ");

            assertThat(rows.rows).containsEntry("signup.enabled", "true");
        }
    }

    @Nested
    @DisplayName("읽기 — 없거나 깨져도 기본값으로 답한다")
    class 읽기 {

        @Test
        @DisplayName("표가 비어 있으면 전부 기본값이다")
        void 빈표() {
            AdminSettingService s = new AdminSettingService(new Settings());

            assertThat(s.isOn(AdminSettingKey.SIGNUP_ENABLED)).isTrue();
            assertThat(s.isOn(AdminSettingKey.MAINTENANCE_ENABLED)).isFalse();
            assertThat(s.number(AdminSettingKey.NEWS_REFRESH_MINUTES)).isEqualTo(60);
        }

        /* 손으로 표를 고쳐 숫자 자리에 글자가 들어가도 서비스가 멈추면 안 된다 */
        @Test
        @DisplayName("숫자 자리가 깨져 있어도 기본값으로 답한다")
        void 깨진값() {
            Settings rows = new Settings();
            rows.rows.put("community.autohide.reports", "여덟");

            assertThat(new AdminSettingService(rows).number(AdminSettingKey.COMMUNITY_AUTOHIDE_REPORTS))
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("모르는 키가 표에 있어도 무시한다")
        void 표에모르는키() {
            Settings rows = new Settings();
            rows.rows.put("legacy.something", "true");

            assertThat(new AdminSettingService(rows).all())
                    .doesNotContainKey("legacy.something")
                    .hasSize(AdminSettingKey.values().length);
        }
    }

    @Nested
    @DisplayName("캐시")
    class 캐시 {

        @Test
        @DisplayName("두 번 읽어도 표는 한 번만 본다")
        void 재사용() {
            Settings rows = new Settings();
            AdminSettingService s = new AdminSettingService(rows);

            s.isOn(AdminSettingKey.SIGNUP_ENABLED);
            s.isOn(AdminSettingKey.SIGNUP_ENABLED);

            assertThat(rows.reads).isEqualTo(1);
        }

        /* 바꾸고도 옛 값이 남으면 관리자가 껐는데 계속 켜져 있는 것으로 보인다 */
        @Test
        @DisplayName("바꾸면 다음 읽기에 반영된다")
        void 변경반영() {
            Settings rows = new Settings();
            AdminSettingService s = new AdminSettingService(rows);
            assertThat(s.isOn(AdminSettingKey.MAINTENANCE_ENABLED)).isFalse();

            s.update(1L, "maintenance.enabled", "true");

            assertThat(s.isOn(AdminSettingKey.MAINTENANCE_ENABLED)).isTrue();
        }
    }
}
