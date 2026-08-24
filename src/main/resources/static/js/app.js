/* ==========================================================================
   미장 — SR-003 대시보드
   목 데이터를 걷어내고 실제 API 를 부른다. 그리는 코드는 그대로다.
   ========================================================================== */

/** 화면이 쓰는 값. 응답이 오기 전까지는 비어 있다. */
let breakdown = null;
let holdings = [];
let watchlist = [];
let latestFx = null;
let displayCurrency = localStorage.getItem("mijang-base-currency") === "USD" ? "USD" : "KRW";

/**
 * 응답 봉투를 벗긴다.
 * 401 이면 로그인으로 보낸다 — 대시보드는 로그인 전용 화면이다.
 */
async function api(url) {
  const res = await fetch(url);
  if (res.status === 401) { location.href = "/login"; return null; }
  const body = await res.json();
  return body.success ? body.data : null;
}

/**
 * 손익 응답을 화면이 기대하는 모양으로 바꾼다.
 *
 * 렌더 함수를 고치지 않으려고 여기서 맞춘다(5.7.2).
 * 환율을 못 구하면 서버가 data 를 null 로 주므로 그때는 null 을 그대로 넘긴다.
 */
function toBreakdown(pnl) {
  if (!pnl) return null;
  return {
    asOf: pnl.asOf,
    totalValue: { krw: Number(pnl.totalValueKrw), usd: Number(pnl.totalValueUsd) },
    pricePnl: { krw: Number(pnl.pricePnlKrw), usd: Number(pnl.pricePnlUsd) },
    /* 달러 기준에는 환차손익이 없다. 환율 변화는 원화로 환산할 때만 생긴다 */
    fxPnl: { krw: Number(pnl.fxPnlKrw), usd: 0 },
    totalPnl: { krw: Number(pnl.totalPnlKrw), usd: Number(pnl.totalPnlUsd), returnRate: Number(pnl.returnRate) },
    appliedFxRate: Number(pnl.appliedFxRate),
    fxSubstituted: pnl.fxSubstituted,
    state: pnl.state,
    skippedSymbols: pnl.skippedSymbols,
  };
}

/**
 * 보유 목록을 화면 모양으로 바꾼다.
 *
 * 종목별 주가·환율 분해는 이 응답에 없다. 전체 분해만 있으면 대시보드가 성립하고,
 * 종목별까지 담으려면 종목 수만큼 계산이 늘어난다. 없는 값은 null 로 두고
 * 화면이 — 로 그린다. 0 으로 채우면 계산된 값처럼 읽힌다.
 */
function toHolding(h) {
  return {
    symbol: h.symbol,
    name: h.name,
    quantity: Number(h.quantity),
    avgPrice: Number(h.avgPrice),
    currentPrice: h.currentPrice == null ? null : Number(h.currentPrice),
    marketValueKrw: h.marketValueKrw == null ? null : Number(h.marketValueKrw),
    evalPnlKrw: h.evalPnlKrw == null ? null : Number(h.evalPnlKrw),
    avgFxRate: h.avgFxRate == null ? null : Number(h.avgFxRate),
    pricePnlKrw: null,
    fxPnlKrw: null,
    /* 수익률은 받은 값으로 구할 수 있다 — 평가손익 ÷ 매입원가(2.8).
       주가·환율 분해와 달리 서버가 따로 주지 않아도 되는 표시용 파생값이다 */
    returnRate: returnRateOf(h),
    dayChangeRate: null,
  };
}

/** 평가손익 ÷ 매입원가. 원가를 못 구하면 null 이다 — 0 으로 나누지 않는다(2.8) */
function returnRateOf(h) {
  if (h.evalPnlKrw == null || h.avgPrice == null || h.avgFxRate == null) return null;
  const cost = Number(h.quantity) * Number(h.avgPrice) * Number(h.avgFxRate);
  return cost === 0 ? null : Number(h.evalPnlKrw) / cost;
}

/**
 * 화면에 필요한 것을 한 번에 받아 그린다.
 *
 * 세 요청을 나란히 보낸다. 순서대로 기다리면 가장 느린 것의 합이 되고,
 * 서로 필요로 하지 않으므로 기다릴 이유가 없다.
 */
async function loadDashboard() {
  const [pnl, hold, watch, fx] = await Promise.all([
    api("/api/portfolio/pnl"),
    api("/api/portfolio/holdings"),
    api("/api/watchlists"),
    api("/api/fx/rates"),
  ]);

  breakdown = toBreakdown(pnl);
  latestFx = fx;
  holdings = (hold || []).filter(h => Number(h.quantity) > 0).map(toHolding);
  watchlist = (watch || []).map(w => ({
    symbol: w.symbol,
    price: w.currentPrice == null ? null : Number(w.currentPrice),
    /* 화면은 비율(0.0082)을 받아 퍼센트로 만든다. API 는 퍼센트(0.82)를 준다 */
    changeRate: w.dayChangeRate == null ? null : Number(w.dayChangeRate) / 100,
  }));
  loadHoldingNews();

  /* 보유가 없으면 손익 분해와 보유 표를 통째로 감추고 다음 행동을 제시한다.
     빈 표에 0원과 0% 를 채워 두면 "계산해 봤더니 0" 으로 읽힌다.
     별도 페이지(dashboard-empty)를 두지 않는다 — 한 주소가 두 상태를 다 처리한다 */
  var blank = holdings.length === 0;
  toggle("dash-empty", blank);
  toggle("dash-breakdown", !blank);
  toggle("dash-holdings", !blank);
  if (blank) { renderSidebarEmpty(); renderWatchlist(); renderFxCard(); return; }

  /* 환율을 못 구하면 손익이 성립하지 않는다(2.6). 안내만 띄우고 나머지는 그린다 */
  renderBreakdown();
  renderHoldings();
  renderWatchlist();
  setupCurrency();
  renderFxCard();
}

/**
 * 환율 카드. <b>손익 계산에 실제로 쓴 값</b>을 보여준다.
 *
 * 다른 값을 띄우면 같은 화면의 환차손익과 어긋나 보인다. 주말·휴일이라 직전 영업일
 * 값으로 대체됐으면 그 사실도 밝힌다 — 오늘 값인 줄 알면 안 된다.
 */
function renderFxCard() {
  const rate = document.getElementById("fx-rate");
  const asOf = document.getElementById("fx-asof");
  const note = document.getElementById("fx-note");
  const updated = document.getElementById("fx-updated");
  if (!rate) return;
  if (updated) updated.textContent = fxUpdatedAt(latestFx);
  if (!breakdown) { rate.textContent = DASH; asOf.textContent = DASH; return; }

  rate.textContent = breakdown.appliedFxRate.toLocaleString("ko-KR",
    { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  asOf.textContent = breakdown.asOf;
  note.textContent = breakdown.fxSubstituted ? "직전 영업일 값" : "";
}

function fxUpdatedAt(fx) {
  const value = fx && (fx.lastUpdatedAt || fx.quotedAt);
  if (!value) return "확인 불가";
  const quotedAt = new Date(value);
  if (Number.isNaN(quotedAt.getTime())) return "확인 불가";
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(quotedAt);
}

loadDashboard();


/* ── 포맷 ─────────────────────────────────────────────────────
   원화는 `원` 접미. `₩` 접두는 쓰지 않는다. */
/* 값이 없으면 — 로 그린다. 0 으로 두면 "계산해 봤더니 0" 으로 읽히고,
   그대로 toLocaleString 을 부르면 null 에서 터진다 */
const DASH = "—";
const krw  = n => n == null ? DASH : Math.round(n).toLocaleString("ko-KR") + "원";
const usd  = n => n == null ? DASH : "$" + n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct  = (r, d = 1) => r == null ? DASH : (r >= 0 ? "+" : "−") + (Math.abs(r) * 100).toFixed(d) + "%";
const pct2 = r => pct(r, 2);
/** hidden 속성 하나로 켜고 끈다. style 을 직접 만지면 원래 display 를 잃는다. */
function toggle(id, on) {
  var el = document.getElementById(id);
  if (el) el.hidden = !on;
}

/** 보유가 없을 때의 사이드바. 종목 수만 0 이고 손익은 — 다 — 계산된 0 이 아니다. */
function renderSidebarEmpty() {
  document.getElementById("stat-count").textContent = "0";
  ["stat-price", "stat-fx"].forEach(function (id) {
    var el = document.getElementById(id);
    if (el) { el.textContent = DASH; el.className = "v"; }
  });
  var nav = document.getElementById("holdings-nav");
  if (nav) {
    nav.replaceChildren();
    var li = document.createElement("li");
    var note = document.createElement("span");
    note.className = "note";
    note.textContent = "아직 보유 종목이 없습니다";
    li.appendChild(note); nav.appendChild(li);
  }
}

/** 부호를 항상 붙인다. 색만으로 손익을 구분하지 않는다. */
const sKrw = n => n == null ? DASH : (n >= 0 ? "+" : "−") + Math.abs(Math.round(n)).toLocaleString("ko-KR");
const signedMoney = (n, currency = displayCurrency) => {
  if (n == null) return DASH;
  const sign = n > 0 ? "+" : n < 0 ? "−" : "";
  const value = Math.abs(Number(n));
  return currency === "USD"
    ? sign + "$" + value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : sign + Math.round(value).toLocaleString("ko-KR") + "원";
};
/** 값이 없으면 손익 색을 붙이지 않는다. 없는 값에 빨강·파랑이 붙으면 방향이 있는 것처럼 보인다 */
const dir  = n => n == null ? "" : (n >= 0 ? "rise" : "fall");

/* ── 손익 요인 분해 ────────────────────────────────────────── */
function renderBreakdown() {
  const chip = document.getElementById("state-chip");

  /* 환율을 못 구하면 손익 자체가 성립하지 않는다(2.6). 숫자를 0 으로 채우는 대신
     자리를 비우고 왜 비었는지 알린다 */
  if (!breakdown) {
    ["total-krw", "total-usd", "total-pnl"].forEach(id => {
      document.getElementById(id).textContent = DASH;
    });
    ["factor-price", "factor-fx"].forEach(id => {
      const f = document.getElementById(id);
      f.querySelector(".v").textContent = DASH;
      f.querySelector(".v").className = "v";
      f.querySelector(".bar").style.width = "0%";
    });
    chip.hidden = false;
    chip.textContent = "환율 없음";
    return;
  }

  const { pricePnl, fxPnl, totalPnl, totalValue } = breakdown;

  document.getElementById("total-krw").textContent = displayCurrency === "USD"
    ? usd(totalValue.usd) : krw(totalValue.krw);
  document.getElementById("total-usd").textContent = displayCurrency === "USD"
    ? krw(totalValue.krw) : usd(totalValue.usd);

  /* 상쇄 여부는 서버가 정한다(2.3). 화면이 부호를 다시 비교하면 기준이 갈린다 */
  chip.textContent = "상쇄";
  chip.hidden = displayCurrency === "USD" || breakdown.state !== "OFFSET";

  // 두 요인 중 큰 쪽을 100%로 잡고 상대 길이를 계산
  const priceValue = displayCurrency === "USD" ? pricePnl.usd : pricePnl.krw;
  const fxValue = displayCurrency === "USD" ? fxPnl.usd : fxPnl.krw;
  const max = Math.max(Math.abs(priceValue), Math.abs(fxValue)) || 1;
  const set = (id, value) => {
    const f = document.getElementById(id);
    const bar = f.querySelector(".bar");
    if (value == null || value === 0) {
      f.dataset.dir = "right";
      f.querySelector(".v").textContent = signedMoney(value);
      f.querySelector(".v").className = "v";
      f.querySelector(".right").appendChild(bar);
      bar.style.width = "0%";
      return;
    }
    const positive = value >= 0;
    f.dataset.dir = positive ? "right" : "left";
    f.querySelector(".v").textContent = signedMoney(value);
    f.querySelector(".v").className = "v " + dir(value);
    // 막대는 방향에 맞는 트랙으로 옮긴다
    f.querySelector(positive ? ".right" : ".left").appendChild(bar);
    bar.style.width = (Math.abs(value) / max * 100) + "%";
    bar.style.background = `var(--${positive ? "rise" : "fall"})`;
  };
  set("factor-price", priceValue);
  set("factor-fx", fxValue);

  const amt = document.getElementById("total-pnl");
  const totalValuePnl = displayCurrency === "USD" ? totalPnl.usd : totalPnl.krw;
  amt.innerHTML = `<b class="${dir(totalValuePnl)}">${signedMoney(totalValuePnl)}</b>` +
                  `<i class="${dir(totalValuePnl)}">${pct2(totalPnl.returnRate)}</i>`;
}

/* ── 사이드바 · 표 ─────────────────────────────────────────── */
function renderHoldings() {
  /* 사이드바의 주가·환차손익은 전 종목 합계다. 보유 목록을 더해서 만들지 않는다 —
     종목별 분해는 이 응답에 없어서 더하면 전부 0 이 된다. 그 합계는 손익 분해가
     이미 갖고 있다(2.4 — 종목별로 계산해서 합친 값이 곧 전체다) */
  const priceSum = breakdown ? breakdown.pricePnl[displayCurrency.toLowerCase()] : null;
  const fxSum    = breakdown ? breakdown.fxPnl[displayCurrency.toLowerCase()] : null;

  document.getElementById("stat-count").textContent = holdings.length;
  const sp = document.getElementById("stat-price");
  sp.textContent = signedMoney(priceSum); sp.className = ("v " + dir(priceSum)).trim();
  const sf = document.getElementById("stat-fx");
  sf.textContent = signedMoney(fxSum); sf.className = ("v " + dir(fxSum)).trim();

  document.getElementById("holdings-nav").innerHTML = holdings.map(h => `
    <li><button type="button">
      <span class="tk">${h.symbol}</span>
      <span class="pc ${dir(h.returnRate)}">${pct(h.returnRate)}</span>
    </button></li>`).join("");

  document.getElementById("holdings-body").innerHTML = holdings.map(h => `
    <tr>
      <td><div class="stack"><span>${h.symbol}</span><span class="name">${h.name}</span></div></td>
      <td><div class="stack"><span>${h.quantity.toLocaleString("ko-KR", { maximumFractionDigits: 6 })}</span><span class="name">${usd(h.avgPrice)}</span></div></td>
      <td><div class="stack"><span>${usd(h.currentPrice)}</span>
          <span class="name ${dir(h.dayChangeRate)}">${pct2(h.dayChangeRate)}</span></div></td>
      <td><div class="stack"><span>${displayCurrency === "USD"
        ? usd(h.currentPrice == null ? null : h.quantity * h.currentPrice)
        : krw(h.marketValueKrw)}</span>
          <span class="name ${dir(h.returnRate)}">${pct(h.returnRate)}</span></div></td>
      <td class="${dir(h.pricePnlKrw)}">${sKrw(h.pricePnlKrw)}</td>
      <td class="${dir(h.fxPnlKrw)}">${sKrw(h.fxPnlKrw)}</td>
    </tr>`).join("");

  document.getElementById("holdings-count").textContent = holdings.length;
}

function renderWatchlist() {
  document.getElementById("watch-count").textContent = watchlist.length;
  document.getElementById("watchlist").innerHTML = watchlist.map(w => `
    <li>
      <span class="tk">${w.symbol}</span>
      <span class="px">${usd(w.price)}</span>
      <span class="pc ${dir(w.changeRate)}">${pct2(w.changeRate)}</span>
    </li>`).join("");
}

/* 보유 종목 가운데 최대 다섯 종목의 뉴스를 모아 최신 세 건만 보여준다.
   Finnhub 분당 한도를 지키기 위해 보유 종목 수만큼 무제한 호출하지 않는다. */
async function loadHoldingNews() {
  const box = document.getElementById("holding-news");
  if (!box) return;

  box.setAttribute("aria-busy", "true");
  if (holdings.length === 0) {
    renderHoldingNewsEmpty(box, "보유 종목이 없습니다");
    return;
  }

  const results = await Promise.all(holdings.slice(0, 5).map(h =>
    api("/api/stocks/" + encodeURIComponent(h.symbol) + "/news")
      .then(items => ({ symbol: h.symbol, items }))
      .catch(() => ({ symbol: h.symbol, items: null }))
  ));
  const loaded = results.some(result => Array.isArray(result.items));
  const seen = new Set();
  const items = results.flatMap(result => (result.items || []).map(item => ({
    symbol: result.symbol,
    headline: item.headline,
    publishedAt: item.publishedAt,
    url: item.url,
  }))).filter(item => {
    const key = item.url || item.headline;
    if (!item.headline || !safeExternalUrl(item.url) || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime())
    .slice(0, 3);

  box.replaceChildren();
  box.setAttribute("aria-busy", "false");
  if (items.length === 0) {
    renderHoldingNewsEmpty(box, loaded ? "최근 보유 종목 뉴스가 없습니다" : "뉴스를 불러오지 못했습니다");
    return;
  }

  items.forEach(item => {
    const li = document.createElement("li");
    const meta = document.createElement("p");
    meta.className = "meta";
    const ticker = document.createElement("span");
    ticker.className = "tk";
    ticker.textContent = item.symbol;
    const time = document.createElement("time");
    time.dateTime = item.publishedAt || "";
    time.textContent = relativeNewsTime(item.publishedAt);
    meta.append(ticker, time);

    const headline = document.createElement("p");
    headline.className = "headline";
    const link = document.createElement("a");
    link.href = safeExternalUrl(item.url);
    link.target = "_blank";
    link.rel = "noreferrer noopener";
    link.textContent = item.headline;
    headline.appendChild(link);
    li.append(meta, headline);
    box.appendChild(li);
  });
}

function renderHoldingNewsEmpty(box, message) {
  box.replaceChildren();
  box.setAttribute("aria-busy", "false");
  const li = document.createElement("li");
  li.className = "news-empty";
  li.textContent = message;
  box.appendChild(li);
}

function safeExternalUrl(value) {
  if (!value) return null;
  try {
    const url = new URL(value, location.origin);
    return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
  } catch (e) {
    return null;
  }
}

function relativeNewsTime(value) {
  const published = new Date(value).getTime();
  if (!published) return "";
  const minutes = Math.max(0, Math.floor((Date.now() - published) / 60000));
  if (minutes < 60) return minutes + "분 전";
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + "시간 전";
  const days = Math.floor(hours / 24);
  return days < 7 ? days + "일 전" : new Date(value).toLocaleDateString("ko-KR");
}

/* ── 통화 전환 ────────────────────────────────────────────────
   원화·달러는 표시 기준일 뿐 계산 기준이 아니다. */
function setupCurrency() {
  document.querySelectorAll("[data-currency] button").forEach(btn => {
    btn.setAttribute("aria-pressed", String(btn.dataset.cur === displayCurrency));
    btn.addEventListener("click", () => {
      applyCurrency(btn.dataset.cur);
    });
  });
}

function applyCurrency(currency) {
  displayCurrency = currency === "USD" ? "USD" : "KRW";
  document.querySelectorAll("[data-currency] button").forEach(btn => {
    btn.setAttribute("aria-pressed", String(btn.dataset.cur === displayCurrency));
  });
  if (breakdown) renderBreakdown();
  if (holdings.length > 0) renderHoldings();
}

document.addEventListener("mijang:currency-change", function (event) {
  applyCurrency(event.detail);
});

/* 즉시 호출하지 않는다. 응답이 오기 전에 그리면 breakdown 이 null 이라 터진다 —
   그리는 순서는 loadDashboard 가 쥔다 */
