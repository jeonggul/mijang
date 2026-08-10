/* ==========================================================================
   미장 — SR-003 대시보드 프로토타입
   API 명세서의 응답 형태를 그대로 흉내 낸 데이터로 화면을 그린다.
   ========================================================================== */

/** GET /api/profit/breakdown 응답 형태 */
const breakdown = {
  asOf: "2025-08-05",
  totalValue: { krw: 12847300, usd: 9120.44 },
  pricePnl: { krw: 822400 },
  fxPnl: { krw: -318900 },
  totalPnl: { krw: 503500, returnRate: 0.0408 },
  appliedFxRate: 1408.40,
  fxSubstituted: false,
};

/** GET /api/holdings 응답 형태 */
const holdings = [
  { symbol: "AAPL", name: "Apple Inc.",          quantity: 12,  avgPrice: 178.20, currentPrice: 196.40, marketValueKrw: 3318700, pricePnlKrw:  306900, fxPnlKrw: -98400, returnRate:  0.067 , dayChangeRate: 0.0124 },
  { symbol: "NVDA", name: "NVIDIA Corp.",        quantity:  8,  avgPrice: 102.40, currentPrice: 138.90, marketValueKrw: 2965300, pricePnlKrw:  512800, fxPnlKrw: -73200, returnRate:  0.174 , dayChangeRate: 0.0210 },
  { symbol: "MSFT", name: "Microsoft Corp.",     quantity:  6,  avgPrice: 398.10, currentPrice: 412.60, marketValueKrw: 3486900, pricePnlKrw:  122400, fxPnlKrw: -51600, returnRate:  0.021 , dayChangeRate: 0.0042 },
  { symbol: "TSLA", name: "Tesla Inc.",          quantity:  9,  avgPrice: 248.30, currentPrice: 221.70, marketValueKrw: 1595800, pricePnlKrw: -141400, fxPnlKrw: -62100, returnRate: -0.113 , dayChangeRate: -0.0185 },
  { symbol: "SCHD", name: "Schwab US Dividend",  quantity: 22,  avgPrice:  79.40, currentPrice:  80.10, marketValueKrw: 1480600, pricePnlKrw:   21700, fxPnlKrw: -33600, returnRate: -0.008 , dayChangeRate: 0.0015 },
];

const watchlist = [
  { symbol: "GOOGL", price: 174.32, changeRate:  0.0082 },
  { symbol: "AMD",   price: 118.05, changeRate: -0.0214 },
  { symbol: "COST",  price: 892.10, changeRate:  0.0034 },
  { symbol: "JEPI",  price:  56.88, changeRate: -0.0011 },
];

/* ── 포맷 ─────────────────────────────────────────────────────
   원화는 `원` 접미. `₩` 접두는 쓰지 않는다. */
const krw  = n => Math.round(n).toLocaleString("ko-KR") + "원";
const usd  = n => "$" + n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct  = (r, d = 1) => (r >= 0 ? "+" : "−") + (Math.abs(r) * 100).toFixed(d) + "%";
const pct2 = r => pct(r, 2);
/** 부호를 항상 붙인다. 색만으로 손익을 구분하지 않는다. */
const sKrw = n => (n >= 0 ? "+" : "−") + Math.abs(Math.round(n)).toLocaleString("ko-KR");
const dir  = n => (n >= 0 ? "rise" : "fall");

/* ── 손익 요인 분해 ────────────────────────────────────────── */
function renderBreakdown() {
  const { pricePnl, fxPnl, totalPnl, totalValue } = breakdown;

  document.getElementById("total-krw").textContent = krw(totalValue.krw);
  document.getElementById("total-usd").textContent = usd(totalValue.usd);

  // 상쇄 = 두 요인의 부호가 다름. 이 서비스가 존재하는 이유다.
  const offset = Math.sign(pricePnl.krw) !== Math.sign(fxPnl.krw)
              && pricePnl.krw !== 0 && fxPnl.krw !== 0;
  document.getElementById("state-chip").hidden = !offset;

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
  const priceSum = holdings.reduce((s, h) => s + h.pricePnlKrw, 0);
  const fxSum    = holdings.reduce((s, h) => s + h.fxPnlKrw, 0);

  document.getElementById("stat-count").textContent = holdings.length;
  const sp = document.getElementById("stat-price");
  sp.textContent = sKrw(priceSum); sp.className = "v " + dir(priceSum);
  const sf = document.getElementById("stat-fx");
  sf.textContent = sKrw(fxSum); sf.className = "v " + dir(fxSum);

  document.getElementById("holdings-nav").innerHTML = holdings.map(h => `
    <li><button type="button">
      <span class="tk">${h.symbol}</span>
      <span class="pc ${dir(h.returnRate)}">${pct(h.returnRate)}</span>
    </button></li>`).join("");

  document.getElementById("holdings-body").innerHTML = holdings.map(h => `
    <tr>
      <td><div class="stack"><span>${h.symbol}</span><span class="name">${h.name}</span></div></td>
      <td><div class="stack"><span>${h.quantity.toFixed(1)}</span><span class="name">${usd(h.avgPrice)}</span></div></td>
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

renderBreakdown();
renderHoldings();
renderWatchlist();
setupCurrency();
