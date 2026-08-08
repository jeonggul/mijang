# 미장 (MIJANG)

미국주식 포트폴리오 관리·손익 분해 학습 프로젝트.
국내 증권사를 통해 미국주식에 투자하는 상황을 가정하고, 시세 조회·매매 기록·손익 요인 분해(주가/환율)·일별 스냅샷·종목 커뮤니티를 직접 구현한다.

## 기술 스택

- **언어/런타임**: Java 17
- **백엔드**: Spring Boot, Thymeleaf (서버사이드 렌더링)
- **영속성**: MyBatis, MySQL 8
- **빌드**: Gradle
- **외부 API**: Alpaca(시세·일봉), Finnhub(뉴스), 한국수출입은행(환율), SEC EDGAR(공시)

## 로컬 실행

1. 비밀정보 파일 생성
   ```bash
   cd src/main/resources
   cp application-secret.properties.example application-secret.properties
   # application-secret.properties 에 실제 DB 비밀번호·API 키 입력
   ```
   그리고 `application.properties` 에 아래 한 줄 추가:
   ```properties
   spring.profiles.include=secret
   ```

2. MySQL 데이터베이스 준비
   ```sql
   CREATE DATABASE mijang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```

3. 실행
   ```bash
   ./gradlew bootRun
   ```

## 문서

기획서·개발명세서 등 설계 문서는 별도 관리한다.

---

*본 저장소는 개인 학습 및 포트폴리오 목적이며, 투자 자문이나 금융 서비스 제공을 목적으로 하지 않는다.*
