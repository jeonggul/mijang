#!/usr/bin/env bash
#
# 외부 API 키 점검 — application-secret.properties 의 값으로 각 벤더를 실제 호출한다.
#
# 무료 티어는 문서와 실제 응답이 다른 경우가 있다. 미장에서도 Finnhub 일봉이 문서상 무료였지만
# 실제로는 403 이었고 그것 때문에 시세 벤더를 바꿨다. 그래서 문서가 아니라 응답으로 확인한다.
#
# 사용법:  ./scripts/check-api-keys.sh
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRET_FILE="$SCRIPT_DIR/../src/main/resources/application-secret.properties"

if [[ ! -f "$SECRET_FILE" ]]; then
  echo "설정 파일이 없다: $SECRET_FILE"
  echo "application-secret.properties.example 을 복사해서 만들 것."
  exit 1
fi

# properties 에서 값 하나 읽기. 주석(#)과 앞뒤 공백은 버린다.
prop() {
  grep -E "^[[:space:]]*$1[[:space:]]*=" "$SECRET_FILE" \
    | tail -1 \
    | cut -d= -f2- \
    | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

ALPACA_KEY="$(prop 'mijang\.external\.alpaca\.api-key')"
ALPACA_SECRET="$(prop 'mijang\.external\.alpaca\.api-secret')"
FINNHUB_KEY="$(prop 'mijang\.external\.finnhub\.api-key')"
EXIM_KEY="$(prop 'mijang\.external\.koreaexim\.api-key')"
SEC_UA="$(prop 'mijang\.external\.sec\.user-agent')"

PASS=0
FAIL=0
SKIP=0

# check <이름> <기대 설명> <curl 인자...>
check() {
  local name="$1"; shift
  local note="$1"; shift
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$@")
  if [[ "$code" == "200" ]]; then
    printf '  \033[32m✓\033[0m %-34s HTTP %s  %s\n' "$name" "$code" "$note"
    PASS=$((PASS + 1))
  else
    printf '  \033[31m✗\033[0m %-34s HTTP %s  %s\n' "$name" "$code" "$note"
    FAIL=$((FAIL + 1))
  fi
}

skip() {
  printf '  \033[33m-\033[0m %-34s %s\n' "$1" "$2"
  SKIP=$((SKIP + 1))
}

echo
echo "SEC EDGAR  (키 없음 · User-Agent 필수 · 초당 10회)"
if [[ -z "$SEC_UA" || "$SEC_UA" != *"@"* || "$SEC_UA" == *"example.com"* ]]; then
  skip "SEC" "User-Agent 미설정 — 실제 이메일을 넣을 것"
else
  check "티커→CIK 매핑" "company_tickers.json" \
    -H "User-Agent: $SEC_UA" "https://www.sec.gov/files/company_tickers.json"
  check "공시 목록 (AAPL)" "submissions" \
    -H "User-Agent: $SEC_UA" "https://data.sec.gov/submissions/CIK0000320193.json"
  check "재무 항목 (AAPL 매출)" "companyconcept" \
    -H "User-Agent: $SEC_UA" \
    "https://data.sec.gov/api/xbrl/companyconcept/CIK0000320193/us-gaap/RevenueFromContractWithCustomerExcludingAssessedTax.json"
fi

echo
echo "Alpaca  (무료 Basic · 분당 200회 · 실시간 IEX)"
if [[ -z "$ALPACA_KEY" || -z "$ALPACA_SECRET" ]]; then
  skip "Alpaca" "키 미설정"
else
  AUTH=(-H "APCA-API-KEY-ID: $ALPACA_KEY" -H "APCA-API-SECRET-KEY: $ALPACA_SECRET")
  check "종목 마스터" "trading /v2/assets" \
    "${AUTH[@]}" "https://paper-api.alpaca.markets/v2/assets?status=active&asset_class=us_equity"
  check "휴장일 캘린더" "trading /v2/calendar" \
    "${AUTH[@]}" "https://paper-api.alpaca.markets/v2/calendar?start=2026-08-01&end=2026-08-31"
  check "일봉 (정산 기준)" "data /v2/stocks/bars" \
    "${AUTH[@]}" "https://data.alpaca.markets/v2/stocks/bars?symbols=AAPL&timeframe=1Day&start=2026-07-01&limit=5"
  check "스냅샷" "data /v2/stocks/snapshots" \
    "${AUTH[@]}" "https://data.alpaca.markets/v2/stocks/snapshots?symbols=AAPL,MSFT"
  check "배당·액면분할" "data corporate-actions" \
    "${AUTH[@]}" "https://data.alpaca.markets/v1/corporate-actions?symbols=AAPL&types=cash_dividend&start=2025-01-01&end=2026-08-01"
fi

echo
echo "Finnhub  (무료 · 분당 60회)"
if [[ -z "$FINNHUB_KEY" ]]; then
  skip "Finnhub" "키 미설정"
else
  check "종목 뉴스" "/company-news" \
    "https://finnhub.io/api/v1/company-news?symbol=AAPL&from=2026-08-01&to=2026-08-11&token=$FINNHUB_KEY"
  check "기업 정보" "/stock/profile2" \
    "https://finnhub.io/api/v1/stock/profile2?symbol=AAPL&token=$FINNHUB_KEY"
  check "투자 지표" "/stock/metric" \
    "https://finnhub.io/api/v1/stock/metric?symbol=AAPL&metric=all&token=$FINNHUB_KEY"
  check "실적 일정" "/calendar/earnings" \
    "https://finnhub.io/api/v1/calendar/earnings?from=2026-08-01&to=2026-09-01&token=$FINNHUB_KEY"
fi

echo
echo "BLS · 연준  (키 없음 · 경제 캘린더)"
if [[ -z "$SEC_UA" || "$SEC_UA" != *"@"* || "$SEC_UA" == *"example.com"* ]]; then
  skip "BLS" "User-Agent 미설정 — SEC 와 같은 값을 쓴다"
else
  check "지표 발표 일정" "bls.ics" \
    -H "User-Agent: $SEC_UA" "https://www.bls.gov/schedule/news_release/bls.ics"
  check "FOMC 일정 (원본 확인용)" "연준 페이지 — 파싱 아님, 갱신 대조용" \
    -H "User-Agent: $SEC_UA" "https://www.federalreserve.gov/monetarypolicy/fomccalendars.htm"
fi

echo
echo "한국수출입은행  (일 1,000회 · 영업일 11시 고시)"
if [[ -z "$EXIM_KEY" ]]; then
  skip "수출입은행" "인증키 미설정 — 신청 후 승인까지 영업일 소요"
else
  check "환율 고시" "AP01" \
    "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON?authkey=$EXIM_KEY&data=AP01"
fi

echo
echo "── 성공 $PASS · 실패 $FAIL · 미설정 $SKIP"
echo
if [[ $FAIL -gt 0 ]]; then
  echo "실패 항목은 응답 본문을 직접 확인할 것:"
  echo "  curl -sS '<위 URL>' | head -c 500"
fi
exit 0
