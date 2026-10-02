/* ==========================================================================
   미장 — 헤더 검색
   어느 화면에서든 헤더에서 바로 검색하고 자동완성으로 고른다.
   종목 상세 화면에는 검색바가 없다(그 화면이 이미 종목을 보고 있다).
   ========================================================================== */
(function () {
  "use strict";

  var box = document.getElementById("hs-input");
  var list = document.getElementById("hs-list");
  if (!box || !list) return;

  var items = [];        // 지금 떠 있는 후보
  var active = -1;       // 키보드로 짚고 있는 자리
  var timer = null;

  function close() {
    list.hidden = true;
    list.replaceChildren();
    items = [];
    active = -1;
    box.setAttribute("aria-expanded", "false");
  }

  /** 고른 종목으로 간다. 검색 결과 화면을 거치지 않는다 */
  function go(symbol) {
    location.href = "/stock?symbol=" + encodeURIComponent(symbol);
  }

  function draw() {
    list.replaceChildren();
    items.forEach(function (s, i) {
      var li = document.createElement("li");
      li.id = "hs-opt-" + i;
      li.setAttribute("role", "option");
      li.setAttribute("aria-selected", String(i === active));
      li.className = i === active ? "on" : "";

      var tk = document.createElement("span");
      tk.className = "tk";
      tk.textContent = s.symbol;

      /* 한글명이 있으면 앞에 세운다. 없는 종목(대부분 ETF)은 영문명이다 */
      var nm = document.createElement("span");
      nm.className = "nm";
      nm.textContent = s.nameKo || s.name;

      var ex = document.createElement("span");
      ex.className = "ex sub";
      ex.textContent = s.exchange || "";

      li.append(tk, nm, ex);
      /* mousedown 으로 받는다. click 은 blur 뒤에 와서 목록이 이미 닫혀 있다 */
      li.addEventListener("mousedown", function (e) { e.preventDefault(); go(s.symbol); });
      list.appendChild(li);
    });
    list.hidden = items.length === 0;
    box.setAttribute("aria-expanded", String(items.length > 0));
    box.setAttribute("aria-activedescendant", active >= 0 ? "hs-opt-" + active : "");
  }

  async function search() {
    var q = box.value.trim();
    if (!q) { close(); return; }
    try {
      var res = await fetch("/api/stocks/search?q=" + encodeURIComponent(q));
      if (!res.ok) { close(); return; }
      var body = await res.json();
      if (!body.success) { close(); return; }
      /* 헤더는 좁다. 여덟 개면 고르기에 충분하고 더 담으면 화면을 덮는다 */
      items = body.data.slice(0, 8);
      active = -1;
      draw();
    } catch (e) {
      close();          // 네트워크가 끊겨도 헤더는 멀쩡해야 한다
    }
  }

  /* 디바운스. 글자마다 보내면 타자 속도만큼 요청이 나간다 */
  box.addEventListener("input", function () {
    clearTimeout(timer);
    timer = setTimeout(search, 200);
  });

  box.addEventListener("keydown", function (e) {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (items.length === 0) return;
      e.preventDefault();
      active = (active + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length;
      draw();
    } else if (e.key === "Enter") {
      e.preventDefault();
      /* 짚은 것이 있으면 그것, 없으면 첫 후보. 아무것도 없으면 검색 화면으로 */
      if (active >= 0) go(items[active].symbol);
      else if (items.length > 0) go(items[0].symbol);
      else if (box.value.trim()) location.href = "/search?q=" + encodeURIComponent(box.value.trim());
    } else if (e.key === "Escape") {
      close();
      box.blur();
    }
  });

  /* 다른 곳을 누르면 닫는다 */
  box.addEventListener("blur", function () { setTimeout(close, 120); });
  box.addEventListener("focus", function () { if (box.value.trim()) search(); });
})();
