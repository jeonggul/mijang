/* ==========================================================================
   미장 — SR-003 대시보드
   목 데이터를 걷어내고 실제 API 를 부른다. 그리는 코드는 그대로다.
   ========================================================================== */

/** 화면이 쓰는 값. 응답이 오기 전까지는 비어 있다. */
let breakdown = null;
let holdings = [];
let watchlist = [];

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
    pricePnl: { krw: Number(pnl.pricePnlKrw) },
    fxPnl: { krw: Number(pnl.fxPnlKrw) },
    totalPnl: { krw: Number(pnl.totalPnlKrw), returnRate: Number(pnl.returnRate) },
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
  const [pnl, hold, watch] = await Promise.all([
    api("/api/portfolio/pnl"),
    api("/api/portfolio/holdings"),
    api("/api/watchlists"),
  ]);

  breakdown = toBreakdown(pnl);
  holdings = (hold || []).filter(h => Number(h.quantity) > 0).map(toHolding);
  watchlist = (watch || []).map(w => ({
    symbol: w.symbol,
    price: w.currentPrice == null ? null : Number(w.currentPrice),
    /* 화면은 비율(0.0082)을 받아 퍼센트로 만든다. API 는 퍼센트(0.82)를 준다 */
    changeRate: w.dayChangeRate == null ? null : Number(w.dayChangeRate) / 100,
  }));

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
  noteSkipped();
}

/**
 * 계산에서 빠진 종목을 밝힌다(2.5).
 *
 * 조용히 빼면 사용자는 합계가 왜 작은지 알 수 없다. 일봉이 아직 없는 종목이라
 * 시간이 지나면 저절로 들어온다는 것까지 말해 준다.
 */
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
  if (!rate) return;
  if (!breakdown) { rate.textContent = DASH; asOf.textContent = DASH; return; }

  rate.textContent = breakdown.appliedFxRate.toLocaleString("ko-KR",
    { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  asOf.textContent = breakdown.asOf;
  note.textContent = breakdown.fxSubstituted ? "직전 영업일 값" : "";
}

function noteSkipped() {
  const note = document.getElementById("cur-note");
  if (!note || !breakdown || !breakdown.skippedSymbols) return;
  note.textContent =
    `시세를 아직 못 구한 ${breakdown.skippedSymbols}종목은 손익 계산에서 빠져 있습니다`;
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

  document.getElementById("total-krw").textContent = krw(totalValue.krw);
  document.getElementById("total-usd").textContent = usd(totalValue.usd);

  /* 상쇄 여부는 서버가 정한다(2.3). 화면이 부호를 다시 비교하면 기준이 갈린다 */
  chip.textContent = "상쇄";
  chip.hidden = breakdown.state !== "OFFSET";

  // 두 요인 중 큰 쪽을 100%로 잡고 상대 길이를 계산
  const max = Math.max(Math.abs(pricePnl.krw), Math.abs(fxPnl.krw)) || 1;
  const set = (id, value) => {
    const f = document.getElementById(id);
    const bar = f.querySelector(".bar");
    const positive = value >= 0;
    f.dataset.dir = positive ? "right" : "left";
    f.querySelector(".v").textContent = sKrw(value) + "원";
    f.querySelector(".v").className = "v " + dir(value);
    // 막대는 방향에 맞는 트랙으로 옮긴다
    f.querySelector(positive ? ".right" : ".left").appendChild(bar);
    bar.style.width = (Math.abs(value) / max * 100) + "%";
    bar.style.background = `var(--${positive ? "rise" : "fall"})`;
  };
  set("factor-price", pricePnl.krw);
  set("factor-fx", fxPnl.krw);

  const amt = document.getElementById("total-pnl");
  amt.innerHTML = `<b class="${dir(totalPnl.krw)}">${sKrw(totalPnl.krw)}원</b>` +
                  `<i class="${dir(totalPnl.krw)}">${pct2(totalPnl.returnRate)}</i>`;
}

/* ── 사이드바 · 표 ─────────────────────────────────────────── */
function renderHoldings() {
  /* 사이드바의 주가·환차손익은 전 종목 합계다. 보유 목록을 더해서 만들지 않는다 —
     종목별 분해는 이 응답에 없어서 더하면 전부 0 이 된다. 그 합계는 손익 분해가
     이미 갖고 있다(2.4 — 종목별로 계산해서 합친 값이 곧 전체다) */
  const priceSum = breakdown ? breakdown.pricePnl.krw : null;
  const fxSum    = breakdown ? breakdown.fxPnl.krw : null;

  document.getElementById("stat-count").textContent = holdings.length;
  const sp = document.getElementById("stat-price");
  sp.textContent = sKrw(priceSum); sp.className = ("v " + dir(priceSum)).trim();
  const sf = document.getElementById("stat-fx");
  sf.textContent = sKrw(fxSum); sf.className = ("v " + dir(fxSum)).trim();

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
      <td><div class="stack"><span>${krw(h.marketValueKrw)}</span>
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

/* ── 통화 전환 ────────────────────────────────────────────────
   원화·달러는 표시 기준일 뿐 계산 기준이 아니다. */
function setupCurrency() {
  if (!breakdown) return;      // 환율을 못 구하면 바꿔 보여 줄 값이 없다
  const rate = breakdown.appliedFxRate;
  document.querySelectorAll("[data-currency] button").forEach(btn => {
    btn.addEventListener("click", () => {
      const group = btn.closest("[data-currency]");
      group.querySelectorAll("button").forEach(b =>
        b.setAttribute("aria-pressed", String(b === btn)));
      const isUsd = btn.dataset.cur === "USD";
      document.getElementById("total-krw").textContent =
        isUsd ? usd(breakdown.totalValue.usd) : krw(breakdown.totalValue.krw);
      document.getElementById("total-usd").textContent =
        isUsd ? krw(breakdown.totalValue.krw) : usd(breakdown.totalValue.usd);
      document.querySelectorAll("#holdings-body .stack > span:first-child").forEach(() => {});
      document.getElementById("cur-note").textContent = isUsd
        ? "환차손익은 원화 기준 개념이라 달러 표시에서는 참고값입니다"
        : "예수금·현금 자산은 포함되지 않습니다";
      void rate;
    });
  });
}

/* 즉시 호출하지 않는다. 응답이 오기 전에 그리면 breakdown 이 null 이라 터진다 —
   그리는 순서는 loadDashboard 가 쥔다 */
