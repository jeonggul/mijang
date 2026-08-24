(function () {
  var list = document.getElementById("retro-list");
  if (!list) return;
  var records = [], holdings = new Map(), sort = "SYM";
  function api(url) { return fetch(url).then(function (res) { if (res.status === 401) { location.href = "/login"; return null; } return res.json(); }).then(function (body) { return body && body.success ? body.data : null; }); }
  function usd(value) { return value == null ? "—" : "$" + Number(value).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function pct(value) { return value == null ? "—" : (value >= 0 ? "+" : "−") + Math.abs(value).toFixed(1) + "%"; }
  function sentiment(value) { return { CONFIDENT: "확신", NEUTRAL: "중립", ANXIOUS: "불안", FOMO: "조급" }[value] || "미기록"; }
  function eligible(tx) { return tx.side === "BUY" && (tx.buyReason || tx.targetPrice != null || tx.sentiment); }
  function renderNav() {
    var nav = document.getElementById("retro-nav"); nav.replaceChildren();
    var symbols = Array.from(new Set(records.map(function (tx) { return tx.symbol; }))).sort();
    if (symbols.length === 0) { var e = document.createElement("li"); e.className = "sub"; e.textContent = "회고할 기록이 없습니다"; nav.appendChild(e); return; }
    symbols.forEach(function (symbol) { var first = records.find(function (tx) { return tx.symbol === symbol; }); var li = document.createElement("li"), a = document.createElement("a"), tk = document.createElement("span"), count = document.createElement("span"); a.href = "#retro-" + first.id; tk.className = "tk"; tk.textContent = symbol; count.className = "pc"; count.textContent = records.filter(function (tx) { return tx.symbol === symbol; }).length + "건"; a.append(tk, count); li.appendChild(a); nav.appendChild(li); });
  }
  function card(tx) {
    var holding = holdings.get(tx.symbol), current = holding && holding.currentPrice != null ? Number(holding.currentPrice) : null;
    var change = current == null ? null : (current / Number(tx.price) - 1) * 100;
    var days = Math.max(0, Math.floor((Date.now() - new Date(tx.tradeDate + "T00:00:00Z").getTime()) / 86400000));
    var article = document.createElement("article"); article.className = "retro-card"; article.id = "retro-" + tx.id;
    article.innerHTML = '<header><b class="tk"></b><span class="nm"></span><span class="meta"></span><span class="spacer"></span><span class="badge badge-line"></span></header><div class="body"><div class="judge"><p class="lab">당시 판단</p><p class="txt"></p><p class="tags"></p></div><div class="result"><p class="lab">이후 결과</p><p class="move"><span class="from num"></span><span class="arrow">→</span><span class="to num"></span><span class="pc"></span></p><p class="note days"></p><p class="note goal"></p><div class="progress"><i></i></div></div></div>';
    article.querySelector(".tk").textContent = tx.symbol; article.querySelector(".nm").textContent = tx.name || "";
    article.querySelector("header .meta").textContent = tx.tradeDate + " 매수 · " + Number(tx.quantity).toLocaleString("ko-KR", { maximumFractionDigits: 6 }) + "주 @ " + usd(tx.price);
    article.querySelector("header .badge").textContent = holding && Number(holding.quantity) > 0 ? "보유 중" : "매도 완료";
    article.querySelector(".judge .txt").textContent = tx.buyReason || "판단 메모 없음";
    var tags = article.querySelector(".tags");
    if (tx.targetPrice != null) { var target = document.createElement("span"); target.className = "badge badge-line"; target.textContent = "목표가 " + usd(tx.targetPrice); tags.appendChild(target); }
    var mood = document.createElement("span"); mood.className = "badge"; mood.textContent = "심리 " + sentiment(tx.sentiment); tags.appendChild(mood);
    article.querySelector(".from").textContent = usd(tx.price); article.querySelector(".to").textContent = usd(current);
    var pc = article.querySelector(".pc"); pc.textContent = pct(change); if (change != null) pc.classList.add(change >= 0 ? "rise" : "fall");
    article.querySelector(".days").textContent = "매수 후 " + days.toLocaleString("ko-KR") + "일 경과";
    var goal = article.querySelector(".goal"), progress = article.querySelector(".progress i");
    if (tx.targetPrice == null || current == null) { goal.textContent = "목표가 또는 현재가가 없어 진행률을 계산하지 않습니다"; progress.style.width = "0%"; }
    else { var remaining = Number(tx.targetPrice) - current; goal.textContent = remaining > 0 ? "목표가까지 " + usd(remaining) + " 남음" : "목표가 도달"; var span = Number(tx.targetPrice) - Number(tx.price); progress.style.width = (span <= 0 ? 0 : Math.max(0, Math.min(100, (current - Number(tx.price)) / span * 100))) + "%"; }
    return article;
  }
  function render() {
    var ordered = records.slice().sort(sort === "TIME" ? function (a, b) { return b.tradeDate.localeCompare(a.tradeDate); } : function (a, b) { return a.symbol.localeCompare(b.symbol) || b.tradeDate.localeCompare(a.tradeDate); });
    list.replaceChildren();
    if (ordered.length === 0) { var empty = document.createElement("section"); empty.className = "card sub"; empty.style.cssText = "padding:28px;text-align:center"; empty.textContent = "판단 메모·목표가·심리를 남긴 매수 기록이 없습니다"; list.appendChild(empty); return; }
    ordered.forEach(function (tx) { list.appendChild(card(tx)); }); renderNav();
  }
  Promise.all([api("/api/transactions?page=0&size=200"), api("/api/portfolio/holdings")]).then(function (values) {
    records = ((values[0] && values[0].content) || []).filter(eligible); (values[1] || []).forEach(function (h) { holdings.set(h.symbol, h); }); render();
  });
  document.querySelector("[data-retro-sort]").addEventListener("mj:change", function (event) { sort = event.detail; render(); });
})();
