# 미장 (MIJANG)

미국주식 포트폴리오 관리·손익 분해 학습 프로젝트.
국내 증권사를 통해 미국주식에 투자하는 상황을 가정하고, 시세 조회·매매 기록·손익 요인 분해(주가/환율)·일별 스냅샷을 다룬다.

## 기술 스택

- Java 17, Spring Boot, Thymeleaf
- MyBatis, MySQL 8
- Gradle

## 빌드 · 실행

```bash
./gradlew bootRun
```

> 로컬 환경 설정(DB·외부 API 키 등)은 별도 문서로 관리한다.
> 비밀정보는 `application-secret.properties`(git 미추적)로 분리하며, 저장소에는 값이 빈 `application-secret.properties.example`만 포함된다.

---

*본 저장소는 개인 학습 및 포트폴리오 목적이며, 투자 자문이나 금융 서비스 제공을 목적으로 하지 않는다.*
