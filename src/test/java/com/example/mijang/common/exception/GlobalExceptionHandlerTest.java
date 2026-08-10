package com.example.mijang.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/** [[미장-API명세서]] 1.1 실패 봉투. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BusinessException 은 ErrorCode 의 상태·코드·메시지를 그대로 싣는다")
    void businessExceptionCarriesErrorCode() {
        var response = handler.handleBusiness(
                new BusinessException(ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.data()).isNull();
        assertThat(body.error().code()).isEqualTo("TX_QUANTITY_EXCEEDS_HOLDING");
        assertThat(body.error().message()).isEqualTo("보유 수량보다 많이 매도할 수 없습니다");
        assertThat(body.error().field()).isNull();
    }

    @Test
    @DisplayName("field 를 준 경우에만 error.field 가 채워진다")
    void fieldIsCarriedWhenGiven() {
        var response = handler.handleBusiness(
                new BusinessException(ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING, "quantity"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().field()).isEqualTo("quantity");
    }

    @Test
    @DisplayName("Spring 이 이미 상태를 정한 오류는 500 으로 뭉개지 않는다")
    void springErrorResponseKeepsItsStatus() {
        var response = handler.handleUnexpected(
                new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_INVALID_REQUEST");
    }

    @Test
    @DisplayName("예상 못 한 예외는 500 이고 내부 사정을 밖으로 흘리지 않는다")
    void unexpectedExceptionHidesInternals() {
        var response = handler.handleUnexpected(
                new IllegalStateException("jdbc connection pool exhausted at com.zaxxer.hikari"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message())
                .isEqualTo(ErrorCode.COMMON_INTERNAL_ERROR.message())
                .doesNotContain("jdbc", "Hikari", "com.");
    }

    @Test
    @DisplayName("스텁 서비스의 UnsupportedOperationException 도 봉투를 유지한다")
    void stubServiceExceptionStillReturnsEnvelope() {
        var response = handler.handleUnexpected(
                new UnsupportedOperationException("TODO AUTH-001: 아직 구현 전"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().message()).doesNotContain("TODO");
    }
}
