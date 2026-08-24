-- 동적 설정·FAQ·비밀번호 변경일 지원.
-- 기존 DB에 한 번 적용한다. 시간은 UTC다.

ALTER TABLE users
  ADD COLUMN password_changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      COMMENT '비밀번호를 마지막으로 변경·재설정한 시각' AFTER password_version;

CREATE TABLE faqs (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  question     VARCHAR(300)    NOT NULL,
  answer       TEXT            NOT NULL,
  sort_order   INT             NOT NULL DEFAULT 0,
  is_published TINYINT(1)      NOT NULL DEFAULT 1,
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_faqs_published_sort (is_published, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO faqs (question, answer, sort_order) VALUES
('미장에서 실제로 주식을 살 수 있나요?', '아니요. 미장은 조회와 기록만 하는 서비스입니다. 체결은 증권사에서 진행하고 그 내역을 미장에 기록합니다.', 10),
('증권사 계좌를 연결할 수 있나요?', '현재는 계좌 연동을 지원하지 않습니다. 매수 사유와 목표가를 포함해 체결 내역을 직접 기록합니다.', 20),
('환차손익이 무엇인가요?', '원달러 환율 변화로 생기는 원화 기준 손익입니다. 미장은 이를 주가 요인과 분리해 보여줍니다.', 30),
('실시간 시세가 다른 앱과 조금 달라요', '실시간 표시는 IEX 기준이라 전체 시장 최종가와 차이가 날 수 있습니다. 손익 계산은 저장된 일봉 종가를 사용합니다.', 40),
('주말에 환율이 안 바뀌어요', '비영업일에는 직전 확정 환율을 사용하며 화면에 대체 여부를 표시합니다.', 50),
('과거 거래를 뒤늦게 입력하면 어떻게 되나요?', '그 거래일 이후 보유 현황과 스냅샷을 다시 계산해야 합니다. 재계산이 필요한 범위는 거래 기록 응답으로 안내합니다.', 60);
