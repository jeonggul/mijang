/* 미장 — 실시간 시세 (MARKET-01~03). 화면의 data-quote 자리를 찾아 SSE 로 오는 값으로 갈아 끼운다. */
(function () {
  "use strict";

  var source = null;      // 지금 열려 있는 SSE 연결
  var fxRate = null;      // 원화 환산에 쓸 환율. 한 번만 받아 둔다
  var watching = "";      // 지금 구독 중인 티커 목록. 바뀔 때만 다시 연결한다
  var retry = 0;          // 연속 실패 횟수. 재연결 간격을 늘리는 데 쓴다

  /** 화면에 지금 떠 있는 티커를 모은다. 중복은 하나로 친다 */
  function symbolsOnScreen() {
    var set = new Set();
    document.querySelectorAll("[data-quote], [data-quote-krw]").forEach(function (el) {
      var sym = el.dataset.quote || el.dataset.quoteKrw;
      if (sym) set.add(sym);
    });
    return Array.from(set).sort();
  }

  function usd(v) {
    return "$" + Number(v).toLocaleString("en-US",
      { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function krw(v) {
    return Math.round(Number(v)).toLocaleString("ko-KR") + "원";
  }

  /* 원화 환산에 쓸 환율. 화면에 [data-quote-krw] 가 하나라도 있을 때만 받는다 —
     쓰지도 않을 값을 모든 화면에서 부를 이유가 없다 */
  async function ensureFxRate() {
    if (fxRate !== null) return fxRate;
    if (!document.querySelector("[data-quote-krw]")) return null;
    try {
      var res = await fetch("/api/fx/rates");
      if (!res.ok) return null;
      var body = await res.json();
      if (body.success && body.data && body.data.rate != null) {
        fxRate = Number(body.data.rate);
      }
    } catch (e) {
      /* 환율을 못 받아도 달러 값은 그대로 보여야 한다 */
    }
    return fxRate;
  }

  /** 값 하나를 같은 티커의 모든 자리에 넣는다. live=false 면 마지막 종가라 흐리게 표시한다. */
  function paint(quote) {
    document.querySelectorAll('[data-quote="' + quote.symbol + '"]').forEach(function (el) {
      el.textContent = usd(quote.price);
      el.classList.toggle("stale", !quote.live);
    });
    /* 원화 환산 자리. 환율을 못 받았으면 건드리지 않는다 —
       달러 값에 1 을 곱한 숫자를 원화라고 내놓는 것보다 비어 있는 편이 낫다 */
    if (fxRate == null) return;
    document.querySelectorAll('[data-quote-krw="' + quote.symbol + '"]').forEach(function (el) {
      el.textContent = krw(quote.price * fxRate);
      el.classList.toggle("stale", !quote.live);
    });
  }

  /** 받은 값 목록을 한 묶음으로 모아 그린다 — 하나씩 그리면 30종목 동시 갱신에 버벅인다. */
  function paintAll(quotes) {
    requestAnimationFrame(function () { quotes.forEach(paint); });
  }

  /** 첫 값을 REST 로 한 번 받아 채운다 — SSE 는 다음 체결이 있어야 값을 준다. */
  async function primeOnce(symbols) {
    if (symbols.length === 0) return;
    var res = await fetch("/api/market/quotes?symbols=" + symbols.join(","));
    if (!res.ok) return;
    var body = await res.json();
    if (body.success) paintAll(body.data);
  }

  /** SSE 를 연다. 재연결은 직접 닫고 간격을 늘려 가며 한다 — 서버 장애 때 몰려드는 것을 막는다. */
  function connect(symbols) {
    if (source) source.close();
    if (symbols.length === 0) { source = null; watching = ""; return; }

    watching = symbols.join(",");
    source = new EventSource("/api/market/stream?symbols=" + watching);

    source.addEventListener("open", function () { retry = 0; });

    source.addEventListener("quote", function (e) {
      paint(JSON.parse(e.data));
    });

    source.addEventListener("error", function () {
      source.close();
      source = null;
      /* 1초 → 2초 → 4초 … 최대 30초. 서버가 돌아오면 첫 시도에 붙는다 */
      var wait = Math.min(30000, 1000 * Math.pow(2, retry++));
      setTimeout(function () { start(); }, wait);
    });
  }

  /** 화면의 티커를 서버에 알리고 연결한다. 무료 요금제는 동시 30종목까지라(2.2) 넘치면 나머지는 REST 로만 채워진다. */
  async function start() {
    var symbols = symbolsOnScreen();
    if (symbols.join(",") === watching && source) return;   // 바뀐 게 없으면 그대로 둔다

    await ensureFxRate();
    await primeOnce(symbols);

    /* 로그인 전용 경로라 비로그인이면 부르지 않는다(2026-09-03 점검 5.2).
       로그인 상태의 실패만 알린다 — 401 인지 서버 장애인지 구분해야 한다 */
    if (await window.mijangSignedIn()) {
      try {
        var res = await fetch("/api/market/subscriptions", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(symbols),
        });
        if (!res.ok && res.status !== 401 && res.status !== 403) {
          console.warn("[시세] 구독 갱신 실패", res.status);
        }
      } catch (e) {
        console.warn("[시세] 구독 갱신을 보내지 못했다", e);
      }
    }

    connect(symbols);
  }

  /* 다른 탭으로 갔을 때는 연결을 끊는다.
     안 보는 화면 때문에 30종목 한도를 쓰고 있을 이유가 없다 */
  document.addEventListener("visibilitychange", function () {
    if (document.hidden) {
      if (source) { source.close(); source = null; watching = ""; }
    } else {
      start();
    }
  });

  /* 표가 다시 그려지면 티커 목록이 바뀐다. 그때 다시 맞춘다.
     화면 코드가 시세를 신경 쓰지 않아도 되도록 여기서 알아서 따라간다 */
  var pending;
  new MutationObserver(function () {
    clearTimeout(pending);
    pending = setTimeout(start, 300);
  }).observe(document.body, { childList: true, subtree: true });

  start();
})();
