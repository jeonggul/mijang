<h1 align="center">미장 · MIJANG</h1>

<p align="center"><strong>미국 주식의 매매 기록과 당시의 판단을 함께 남기는 투자 회고 서비스</strong></p>
<p align="center">Java 17 · Spring Boot 4 · MyBatis · MySQL · Thymeleaf</p>

## 프로젝트 소개

미장은 매수·매도 내역과 투자 판단을 기록하고, 원화 손익을 **주가의 영향과 환율의 영향으로 나누어** 확인하는 웹 서비스입니다.

주식을 살 때의 이유와 실제 결과를 따로 관리하면 시간이 지난 뒤 판단을 돌아보기 어렵습니다. 미장은 거래 기록, 보유 현황, 종목 정보와 회고를 한곳에 모아 “얼마를 벌었는가”와 “왜 그런 결과가 나왔는가”를 함께 확인할 수 있도록 만들었습니다. 사용자가 이미 체결한 거래를 직접 기록하는 방식입니다.

| 항목 | 내용 |
| --- | --- |
| 개발 형태 | 개인 프로젝트 |
| 개발 기간 | 2026.08–2026.09 |
| 개발자 | 이정하 |
| 담당 범위 | 기획·DB 설계·백엔드·화면 구현·외부 API 연동·테스트·AWS 배포 |

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 매매 기록 | 매수·매도 등록, 수정·삭제, 소수점 수량, 수수료와 거래일 환율 기록 |
| 투자 판단 기록 | 매매 이유, 목표가, 당시 심리를 남기고 회고 화면에서 확인 |
| 포트폴리오 | 거래 이력을 기준으로 보유 수량, 평균 매입 단가, 실현손익 계산 |
| 손익 분석 | 원화 평가손익을 주가손익·환차손익으로 분리하고 일별 스냅샷으로 추이 확인 |
| 종목 탐색 | 종목 검색, 관심 종목, 차트, 기업 정보, 뉴스·공시 조회 |
| 실시간 시세 | 외부 WebSocket 시세 수신과 SSE를 통한 브라우저 갱신 |
| 배당·세금 | 배당 내역·예상 배당 조회와 양도소득세 추정 |
| 커뮤니티 | 게시글·댓글과 거래 스냅샷을 통한 투자 기록 공유 |
| 회원·운영 | 회원가입·로그인, 소셜 로그인, 비밀번호 재설정, 마이페이지·관리자 기능 |

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 4.0.7, Spring MVC, Spring Security |
| 인증 | JWT, HttpOnly 쿠키, OAuth2 |
| Database | MySQL 8, MyBatis 4.0.1 |
| Frontend | Thymeleaf, JavaScript, HTML, CSS |
| 실시간 | 외부 WebSocket, Server-Sent Events(SSE) |
| 외부 데이터 | Alpaca, Finnhub, SEC EDGAR, Open Exchange Rates |
| 테스트·빌드 | JUnit, Spring Boot Test, Gradle |
| 배포 | AWS EC2, Nginx, HTTPS |

## 구조

```text
브라우저 → Controller → Service → Mapper → MySQL
                          └→ 외부 API 클라이언트

Alpaca WebSocket → 구독 관리·시세 캐시 → SSE → 브라우저
```

컨트롤러는 요청과 인증 정보를 전달하고, 서비스는 업무 규칙과 트랜잭션을 처리합니다. SQL은 MyBatis 매퍼로 분리했습니다. 현재 시세 캐시와 구독 관리는 단일 인스턴스의 메모리에서 동작합니다.

## 핵심 구현

### 1. 거래 원장으로부터 보유 현황 재계산

`transactions`를 원본으로 두고, `holdings`는 거래 이력에서 다시 만들 수 있는 파생 데이터로 관리합니다. 과거 거래를 추가하거나 수정·삭제하면 해당 종목의 거래를 시간순으로 다시 계산합니다.

최종 보유 수량만 확인하면 과거의 초과 매도를 놓칠 수 있습니다. 예를 들어 매수 5주 → 매도 8주 → 매수 10주는 최종 수량이 7주이지만, 중간의 매도는 성립하지 않습니다. 계산 과정의 **최소 보유 수량**을 추적해 이런 거래를 거절하고, 거래 변경과 보유 재계산을 하나의 트랜잭션으로 되돌립니다.

관련 코드: [TransactionService](src/main/java/com/example/mijang/portfolio/service/TransactionService.java) · [HoldingCalculator](src/main/java/com/example/mijang/portfolio/service/HoldingCalculator.java)

### 2. 주가손익과 환차손익 분리

달러 가격 변동과 환율 변동이 원화 손익에 미친 영향을 각각 계산합니다.

```text
주가손익 = 수량 × (현재가 − 평균매입단가) × 현재환율
환차손익 = 수량 × 평균매입단가 × (현재환율 − 평균매수환율)
평가손익 = 주가손익 + 환차손익
```

평균매입단가는 이동평균으로, 평균매수환율은 매수 금액을 기준으로 가중평균하여 계산합니다. 금액 연산에는 `BigDecimal`을 사용하고 소수점 자릿수와 반올림 방식을 명시했습니다. 손익 계산과 스냅샷의 기준 데이터는 화면 표시용 실시간 시세와 구분합니다.

관련 코드: [ProfitLossCalculator](src/main/java/com/example/mijang/portfolio/service/ProfitLossCalculator.java)

### 3. 원본 거래를 보존하는 주식 분할 처리

분할 조정된 시세와 분할 전 거래를 그대로 비교하면 평가금액이 왜곡됩니다. 이를 방지하기 위해 원본 거래는 유지하고, 보유 현황을 계산할 때 분할 이전 거래의 수량과 단가를 환산합니다.

4:1 분할이라면 수량은 4배, 단가는 1/4로 바꾸어 체결 금액을 유지합니다. 분할 기준일 당일 거래의 중복 보정을 막는 경계 조건도 테스트합니다.

### 4. WebSocket 수신과 SSE 전달 분리

서버는 Alpaca WebSocket에서 시세를 받고, 브라우저에는 SSE로 변경 내용을 전달합니다. 여러 사용자가 같은 종목을 조회해도 외부 구독은 공유하고, 참조 횟수로 구독 수요를 관리합니다. 외부 공급자의 종목 구독 한도를 고려하면서 화면별 시세 갱신을 연결했습니다.

관련 코드: [SubscriptionPoolManager](src/main/java/com/example/mijang/market/pool/SubscriptionPoolManager.java)

### 5. 인증과 배포 환경 연결

JWT를 HttpOnly 쿠키로 전달하고, 인증 과정에서 계정 상태와 비밀번호 버전을 확인합니다. 비밀번호 변경 이후에는 기존 인증 정보로 접근을 이어갈 수 없도록 구성했습니다.

AWS EC2·Nginx 환경에서는 애플리케이션과 DB 스키마의 차이로 발생한 로그인 오류를 수정했습니다. HTTPS 프록시 뒤의 OAuth 콜백 주소는 전달 헤더 처리와 등록된 콜백 설정을 맞추어 해결했습니다.

## 디렉터리 구조

```text
src/main/java/com/example/mijang/
├── portfolio/   # 거래 원장, 보유 현황, 손익·회고
├── stock/       # 종목 정보·일봉·주식 분할
├── market/      # 시세 수신·구독 관리·SSE
├── fx/          # 환율 수집·확정
├── dividend/    # 배당
├── user/        # 회원·소셜 로그인
├── security/    # JWT 인증·권한
├── community/   # 게시글·댓글
├── admin/       # 운영 관리
└── web/         # 화면 라우팅
src/main/resources/
├── mapper/      # MyBatis SQL
├── templates/   # Thymeleaf 화면
└── static/      # JavaScript·CSS·이미지
```

## 실행 방법

### 사전 준비

- JDK 17, MySQL 8
- DB 스키마와 날짜별 마이그레이션 SQL
- 사용 기능에 맞는 외부 API 키 및 OAuth·메일 설정

**DB 생성 SQL은 현재 애플리케이션 저장소 밖의 별도 문서 폴더에서 관리합니다.** 저장소를 복제하는 것만으로 DB가 생성되지는 않습니다. 개발 문서의 `docs/db/schema.sql`과 `docs/db/migrations/`를 별도로 준비하고, DB에 이미 적용된 변경을 확인해 필요한 마이그레이션을 날짜 순으로 반영해야 합니다.

```bash
git clone https://github.com/jeonggul/mijang.git
cd mijang
cp src/main/resources/application-secret.properties.example \
   src/main/resources/application-secret.properties
```

`application-secret.properties`에 DB 접속 정보, JWT 서명 키, 사용할 외부 API 키를 설정합니다. 환율 연동에는 Open Exchange Rates의 **`mijang.fx.app-id`**가 필요합니다. 예제 파일의 과거 환율 공급자 항목과 구분해 설정합니다. 실제 키와 비밀번호는 커밋하지 않습니다.

초기 로컬 확인 시에는 자동 외부 데이터 수집을 끄고 실행할 수 있습니다.

```bash
./gradlew bootRun --args='--mijang.batch.enabled=false'
```

기본 주소: `http://localhost:8080`. DB와 외부 연동 데이터가 준비되지 않은 화면은 빈 상태로 표시될 수 있습니다.

### 테스트

```bash
./gradlew test
```

주요 검증 대상은 손익 분해 합계, 이동평균 계산, 거래 수정, 주식 분할 기준일, 인증·권한 처리입니다. 테스트 종류에 따라 DB 등 실행 환경이 필요합니다.

- [ProfitLossCalculatorTest](src/test/java/com/example/mijang/portfolio/ProfitLossCalculatorTest.java)
- [HoldingCalculatorTest](src/test/java/com/example/mijang/portfolio/HoldingCalculatorTest.java)
- [TransactionUpdateTest](src/test/java/com/example/mijang/portfolio/TransactionUpdateTest.java)
- [StockSplitAdjustTest](src/test/java/com/example/mijang/portfolio/StockSplitAdjustTest.java)

## 향후 개선

- 다중 인스턴스에서 시세 수집과 구독 상태를 공유하는 구조
- DB 초기화·마이그레이션 절차를 저장소 안에서 재현할 수 있도록 정리
- 배포 후 상태 확인과 장애 진단을 위한 운영 관측 강화

---

개인 학습 및 포트폴리오 프로젝트입니다. 실제 주문을 실행하는 증권 거래 서비스가 아닙니다.
