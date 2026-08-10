package com.example.mijang.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 오류 코드 체계가 [[미장-API명세서]] 1.6 과 어긋나지 않는지 확인한다.
 *
 * <p>코드 체계는 나중에 바꾸면 전 파트를 고쳐야 하는 종류라 P0 에서 잠근다.
 */
class ErrorCodeTest {

    @Test
    @DisplayName("코드 문자열이 겹치지 않는다")
    void codesAreUnique() {
        var codes = Arrays.stream(ErrorCode.values()).map(ErrorCode::code).collect(Collectors.toSet());

        assertThat(codes).hasSize(ErrorCode.values().length);
    }

    @Test
    @DisplayName("enum 이름과 코드 문자열이 같다 — 로그에서 코드만 보고 상수를 찾을 수 있게")
    void nameMatchesCode() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.code()).isEqualTo(ec.name());
        }
    }

    @Test
    @DisplayName("코드는 명세서 1.6 의 UPPER_SNAKE_CASE 표기를 따른다")
    void codesAreUpperSnakeCase() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.code()).matches("[A-Z]+(_[A-Z0-9]+)*");
        }
    }

    @Test
    @DisplayName("모든 코드가 4xx·5xx 다 — 성공 상태를 오류로 두지 않는다")
    void everyCodeIsAnErrorStatus() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.status().isError())
                    .as("%s 의 상태 %s", ec.name(), ec.status())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("명세서 1.6 에 명시된 코드와 HTTP 상태가 일치한다")
    void matchesSpecTable() {
        assertThat(ErrorCode.AUTH_INVALID_CREDENTIALS.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_TOKEN_EXPIRED.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 형식은 맞지만 규칙 위반이라 400 이 아니라 422 다
        assertThat(ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING.status().value()).isEqualTo(422);
        assertThat(ErrorCode.TX_STOCK_INACTIVE.status().value()).isEqualTo(422);
        // 미래 거래일은 값 자체가 틀린 것이므로 400
        assertThat(ErrorCode.TX_TRADE_DATE_FUTURE.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.DIVIDEND_ALREADY_CONFIRMED.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.VENDOR_RATE_LIMIT.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.VENDOR_UNAVAILABLE.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("FX_NOT_AVAILABLE 은 오류 코드가 아니다 — 명세서가 '오류 아님'으로 못박았다")
    void fxNotAvailableIsNotAnErrorCode() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::code))
                .doesNotContain("FX_NOT_AVAILABLE");
    }

    @Test
    @DisplayName("사용자에게 보일 메시지가 비어 있지 않다")
    void everyCodeHasUserFacingMessage() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.message()).as("%s", ec.name()).isNotBlank();
        }
    }
}
