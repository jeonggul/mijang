/* 관리자 통계 탭. 페이지뷰를 추정하지 않고 서버가 집계한 값만 표시한다. */
(function () {
  var table = document.getElementById("admin-popular-stocks");
  if (!table) return;

  function number(value) {
    return Number(value || 0).toLocaleString("ko-KR");
  }

  function put(id, value) {
    var element = document.getElementById(id);
    if (element) element.textContent = value;
  }

  function notice(message) {
    table.replaceChildren();
    var td = table.insertRow().insertCell();
    td.colSpan = 5;
    td.className = "sub";
    td.style.cssText = "padding:22px;text-align:center";
    td.textContent = message;
  }

  function comparison(stats) {
    if (stats.newUserChangeRate == null) {
      return "이전 기간 " + number(stats.previousNewUserCount) + "명";
    }
    var rate = Number(stats.newUserChangeRate);
    return "이전 기간 대비 " + (rate > 0 ? "+" : "") + rate.toFixed(1) + "%";
  }

  function drawStocks(stocks) {
    if (!stocks || stocks.length === 0) { notice("관심·보유 종목이 없습니다"); return; }
    table.replaceChildren();
    stocks.forEach(function (stock, index) {
      var row = table.insertRow();
      [index + 1, stock.symbol, stock.nameKo || stock.name,
       number(stock.watcherCount), number(stock.holderCount)].forEach(function (value, cellIndex) {
        var td = row.insertCell();
        if (cellIndex === 2) td.className = "txt";
        if (cellIndex >= 3) td.className = "r num";
        td.textContent = value;
      });
    });
  }

  async function fetchStats(period) {
    var response = await fetch("/api/admin/stats?period=" + encodeURIComponent(period));
    if (response.status === 401) { location.href = "/login"; return null; }
    var body = await response.json().catch(function () { return null; });
    if (!response.ok || !body || !body.success) {
      return null;
    }
    return body.data;
  }

  async function load(period) {
    var stats = await fetchStats(period);
    if (!stats) { notice("통계를 불러오지 못했습니다"); return; }
    var counts = stats.counts;
    put("admin-stats-period", stats.fromDate + " ~ " + stats.toDate + " · 한국시간 기준");
    put("stats-new-users", number(counts.newUserCount));
    put("stats-new-users-note", comparison(stats));
    put("stats-active-users", number(counts.activeUserCount));
    put("stats-transactions", number(counts.transactionCount));
    put("stats-judgment-rate", stats.judgmentRate == null
      ? "—" : Number(stats.judgmentRate).toFixed(1) + "%");
    put("stats-judgment-note", number(counts.judgmentCount) + "건에 판단 기록");
    put("stats-judgments", number(counts.judgmentCount));
    put("stats-posts", number(counts.postCount));
    put("stats-comments", number(counts.commentCount));
    put("stats-watches", number(counts.watchCount));
    put("ad-today-transactions", number(stats.todayTransactionCount));
    drawStocks(stats.popularStocks);
  }

  document.querySelector("[data-stats-period]").addEventListener("mj:change", function (event) {
    load(event.detail);
  });

  load("1M");
})();
