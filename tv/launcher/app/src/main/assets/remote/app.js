/* Sistema remote. Served by the TV itself; talks to it over your Wi-Fi. */
(function () {
  "use strict";

  var TOKEN_KEY = "sistema.token";
  var token = null;
  try { token = localStorage.getItem(TOKEN_KEY); } catch (e) {}
  var data = null;
  var refreshTimer = null;

  function $(s) { return document.querySelector(s); }
  function $$(s) { return Array.prototype.slice.call(document.querySelectorAll(s)); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  // ------------------------------------------------------------ api

  function api(path, body) {
    var opts = { method: body === undefined ? "GET" : "POST", headers: {} };
    if (token) opts.headers["X-Sistema-Token"] = token;
    if (body !== undefined) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(body);
    }
    return fetch(path, opts).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (r.status === 401) { showPair(); throw new Error("pair"); }
        if (!r.ok) throw new Error(j.error || "The TV didn't answer.");
        return j;
      });
    }, function () {
      throw new Error("Can't reach the TV. Is this phone on the same Wi-Fi?");
    });
  }

  // Remote presses go out in order, one at a time.
  var chain = Promise.resolve();
  function send(path, body) {
    chain = chain.then(function () { return api(path, body); }).catch(function (e) {
      if (e.message !== "pair") toast(e.message);
    });
    return chain;
  }
  function key(name, long) { return send("/api/key", { key: name, long: !!long }); }

  var toastTimer = null;
  function toast(msg) {
    var t = $("#toast");
    t.textContent = msg;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.classList.remove("show"); }, 2600);
  }

  // ------------------------------------------------------------ pairing

  function showPair() {
    $("#pair").hidden = false;
    $("#main").hidden = true;
    $("#tabbar").hidden = true;
  }
  function showMain() {
    $("#pair").hidden = true;
    $("#main").hidden = false;
    $("#tabbar").hidden = false;
  }

  $("#pair-start").addEventListener("click", function () {
    $("#pair-msg").textContent = "";
    api("/api/pair/start", {}).then(function () {
      $("#pair-entry").hidden = false;
      $("#pair-start").textContent = "Show the code again";
      $("#pair-code").focus();
    }, function (e) { $("#pair-msg").textContent = e.message; });
  });

  function tryPair() {
    var code = $("#pair-code").value.replace(/\D/g, "");
    if (code.length !== 4) { $("#pair-msg").textContent = "Enter the four digits from the TV."; return; }
    api("/api/pair", { code: code }).then(function (j) {
      token = j.token;
      try { localStorage.setItem(TOKEN_KEY, token); } catch (e) {}
      $("#pair-code").value = "";
      showMain();
      load();
    }, function (e) { $("#pair-msg").textContent = e.message; });
  }
  $("#pair-go").addEventListener("click", tryPair);
  $("#pair-code").addEventListener("input", function () {
    if (this.value.replace(/\D/g, "").length === 4) tryPair();
  });

  // ------------------------------------------------------------ tabs

  $$(".tabbar button").forEach(function (b) {
    b.addEventListener("click", function () {
      $$(".tabbar button").forEach(function (x) { x.classList.toggle("on", x === b); });
      $$(".tab").forEach(function (t) { t.hidden = t.id !== "tab-" + b.dataset.tab; });
      window.scrollTo(0, 0);
      load();
    });
  });

  // ------------------------------------------------------------ touchpad

  var pad = $("#pad");
  var dot = $("#dot");
  var STEP = 42;
  var touch = null;

  function lit(dir) {
    var c = pad.querySelector(".chev." + dir);
    if (!c) return;
    c.classList.add("lit");
    setTimeout(function () { c.classList.remove("lit"); }, 160);
  }
  function moveDot(x, y) {
    var r = pad.getBoundingClientRect();
    dot.style.left = (x - r.left) + "px";
    dot.style.top = (y - r.top) + "px";
  }

  pad.addEventListener("pointerdown", function (e) {
    e.preventDefault();
    pad.setPointerCapture && pad.setPointerCapture(e.pointerId);
    touch = { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY, t: Date.now(), moved: false, long: false };
    pad.classList.add("touching");
    moveDot(e.clientX, e.clientY);
    touch.timer = setTimeout(function () {
      if (touch && !touch.moved) {
        touch.long = true;
        pad.classList.add("pressed");
        key("ok", true);
      }
    }, 550);
  });
  pad.addEventListener("pointermove", function (e) {
    if (!touch) return;
    moveDot(e.clientX, e.clientY);
    var dx = e.clientX - touch.x, dy = e.clientY - touch.y;
    if (Math.abs(dx) < STEP && Math.abs(dy) < STEP) return;
    var dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "right" : "left") : (dy > 0 ? "down" : "up");
    touch.moved = true;
    clearTimeout(touch.timer);
    touch.x = e.clientX;
    touch.y = e.clientY;
    lit(dir);
    key(dir);
  });
  function endTouch(e) {
    if (!touch) return;
    clearTimeout(touch.timer);
    pad.classList.remove("touching", "pressed");
    var t = touch;
    touch = null;
    if (t.moved || t.long || e.type === "pointercancel") return;
    var r = pad.getBoundingClientRect();
    var fx = (t.sx - r.left) / r.width, fy = (t.sy - r.top) / r.height;
    // Tapping near an edge clicks that direction, like the Apple TV remote.
    var dir = "ok";
    if (fy < 0.18) dir = "up";
    else if (fy > 0.84) dir = "down";
    else if (fx < 0.18) dir = "left";
    else if (fx > 0.82) dir = "right";
    if (dir === "ok") {
      pad.classList.add("pressed");
      setTimeout(function () { pad.classList.remove("pressed"); }, 140);
    } else {
      lit(dir);
    }
    key(dir);
  }
  pad.addEventListener("pointerup", endTouch);
  pad.addEventListener("pointercancel", endTouch);

  // ------------------------------------------------------------ keys

  $$("[data-key]").forEach(function (b) {
    var repeat = null;
    function press() { key(b.dataset.key); }
    if (b.dataset.repeat) {
      // Hold volume to keep going.
      b.addEventListener("pointerdown", function (e) {
        e.preventDefault();
        press();
        var n = 0;
        repeat = setInterval(function () { if (++n > 2) press(); }, 160);
      });
      ["pointerup", "pointercancel", "pointerleave"].forEach(function (ev) {
        b.addEventListener(ev, function () { clearInterval(repeat); });
      });
    } else {
      b.addEventListener("click", press);
    }
  });

  $("#power").addEventListener("click", function () {
    if (confirm("Turn the TV off?")) key("sleep");
  });

  // ------------------------------------------------------------ keyboard

  var kbPrev = "";
  $("#kb-open").addEventListener("click", function () {
    $("#kb").hidden = false;
    kbPrev = "";
    var i = $("#kb-input");
    i.value = "";
    i.focus();
  });
  function closeKb() { $("#kb").hidden = true; $("#kb-input").blur(); }
  $("#kb-done").addEventListener("click", closeKb);
  $("#kb").addEventListener("click", function (e) { if (e.target === this) closeKb(); });
  $("#kb-del").addEventListener("click", function () {
    key("delete");
    kbPrev = kbPrev.slice(0, -1);
    $("#kb-input").value = kbPrev;
  });
  $("#kb-enter").addEventListener("click", function () { key("enter"); });
  $("#kb-input").addEventListener("keydown", function (e) {
    if (e.key === "Enter") { e.preventDefault(); key("enter"); }
  });
  // Mirror the phone field onto the TV: send what was added, delete what was removed.
  $("#kb-input").addEventListener("input", function () {
    var now = this.value;
    if (now.indexOf(kbPrev) === 0) {
      var added = now.slice(kbPrev.length);
      if (added) send("/api/text", { text: added });
    } else {
      var common = 0;
      while (common < now.length && common < kbPrev.length && now[common] === kbPrev[common]) common++;
      for (var i = 0; i < kbPrev.length - common; i++) key("delete");
      var rest = now.slice(common);
      if (rest) send("/api/text", { text: rest });
    }
    kbPrev = now;
  });

  // ------------------------------------------------------------ render

  function greeting(name) {
    var h = new Date().getHours();
    var part = h < 5 ? "Late night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 21 ? "Good evening" : "Good night";
    return part + ", " + name;
  }

  function iconUrl(pkg, banner) {
    return "/api/icon?pkg=" + encodeURIComponent(pkg) + (banner ? "&kind=banner" : "") + "&t=" + encodeURIComponent(token || "");
  }

  function render() {
    if (!data) return;
    var d = data;

    var hello = $("#hello");
    hello.textContent = greeting(d.name);
    hello.appendChild(el("em", null, "."));

    var st = $("#status");
    st.className = "status " + (d.adb === "ok" ? "ok" : d.adb === "waiting" || d.adb === "connecting" ? "wait" : "");
    st.querySelector("b").textContent = d.adb === "ok" ? "Connected" : d.adb === "waiting" ? "Allow on TV" : d.adb === "connecting" ? "Connecting" : "Limited";

    var now = $("#now");
    now.textContent = "";
    if (d.foreground) {
      now.appendChild(document.createTextNode("On the TV  "));
      now.appendChild(el("b", null, d.foreground.label));
    } else if (d.adb === "waiting") {
      now.textContent = "Press Allow on the TV once to unlock full control.";
    }

    // Inputs
    var box = $("#inputs");
    box.textContent = "";
    d.inputs.forEach(function (inp) {
      var c = el("button", "chip", inp.title);
      if (inp.device) c.appendChild(el("small", null, inp.device));
      c.addEventListener("click", function () { send("/api/input", { id: inp.id }); toast("Switching to " + inp.title); });
      box.appendChild(c);
    });

    renderApps(d);
    renderYours(d);
  }

  function setFavorites(list) {
    return send("/api/favorites", { favorites: list }).then(load);
  }

  function renderApps(d) {
    var grid = $("#fav-grid");
    grid.textContent = "";
    var favPkgs = d.favorites.map(function (f) { return f.pkg; });
    d.favorites.forEach(function (f) {
      var t = el("button", "tile");
      if (f.installed) {
        t.style.backgroundImage = "url('" + iconUrl(f.pkg, true) + "')";
        t.appendChild(el("span", "name", f.label));
        t.addEventListener("click", function () { send("/api/launch", { pkg: f.pkg }); toast("Opening " + f.label); });
      } else {
        t.appendChild(el("span", "missing", f.label));
        t.addEventListener("click", function () { toast(f.label + " isn't installed. Its tile on the TV opens Google Play."); });
      }
      grid.appendChild(t);
    });

    var list = $("#app-list");
    list.textContent = "";
    d.apps.filter(function (a) { return favPkgs.indexOf(a.pkg) < 0; }).forEach(function (a) {
      var row = el("div", "app");
      var img = el("img");
      img.loading = "lazy";
      img.alt = "";
      img.src = iconUrl(a.pkg, false);
      var open = el("button", "label", a.label);
      open.addEventListener("click", function () { send("/api/launch", { pkg: a.pkg }); toast("Opening " + a.label); });
      var star = el("button", "star");
      star.setAttribute("aria-label", "Pin " + a.label);
      star.innerHTML = '<svg viewBox="0 0 24 24"><path d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"/></svg>';
      star.addEventListener("click", function () {
        setFavorites(favPkgs.concat([a.pkg]));
        toast(a.label + " pinned to your home screen");
      });
      row.appendChild(img);
      row.appendChild(open);
      row.appendChild(star);
      list.appendChild(row);
    });
  }

  function renderYours(d) {
    var name = $("#name");
    if (document.activeElement !== name) name.value = d.name;
    $("#greet-preview").textContent = "Hello " + (name.value || d.name);
    $("#greet-preview").appendChild(el("em", null, "."));

    $("#city-now").textContent = d.city + "  ·  " + (d.units === "c" ? "Celsius" : "Fahrenheit");
    $$("#units button").forEach(function (b) { b.classList.toggle("on", b.dataset.units === d.units); });

    var order = $("#fav-order");
    order.textContent = "";
    var pkgs = d.favorites.map(function (f) { return f.pkg; });
    d.favorites.forEach(function (f, i) {
      var item = el("div", "item");
      var img = el("img");
      img.alt = "";
      if (f.installed) img.src = iconUrl(f.pkg, true);
      item.appendChild(img);
      item.appendChild(el("span", null, f.label));
      function btn(svg, label, disabled, fn) {
        var b = el("button");
        b.innerHTML = svg;
        b.setAttribute("aria-label", label);
        b.disabled = disabled;
        b.addEventListener("click", fn);
        item.appendChild(b);
      }
      btn('<svg viewBox="0 0 24 24"><path d="m6 15 6-6 6 6"/></svg>', "Move up", i === 0, function () {
        var p = pkgs.slice(); p.splice(i - 1, 0, p.splice(i, 1)[0]); setFavorites(p);
      });
      btn('<svg viewBox="0 0 24 24"><path d="m6 9 6 6 6-6"/></svg>', "Move down", i === pkgs.length - 1, function () {
        var p = pkgs.slice(); p.splice(i + 1, 0, p.splice(i, 1)[0]); setFavorites(p);
      });
      btn('<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg>', "Remove", false, function () {
        var p = pkgs.slice(); p.splice(i, 1); setFavorites(p);
        toast(f.label + " removed from favorites");
      });
      order.appendChild(item);
    });

    var qn = $("#quote-now");
    qn.textContent = "“" + d.quote.text + "”";
    qn.appendChild(el("small", null, d.quote.author || d.name));

    var ql = $("#q-list");
    ql.textContent = "";
    d.customQuotes.forEach(function (q, i) {
      var row = el("div", "q");
      var p = el("p", null, "“" + q.text + "”");
      p.appendChild(el("small", null, q.author || d.name));
      var x = el("button");
      x.setAttribute("aria-label", "Remove quote");
      x.innerHTML = '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg>';
      x.addEventListener("click", function () { send("/api/quotes", { remove: i }).then(load); });
      row.appendChild(p);
      row.appendChild(x);
      ql.appendChild(row);
    });

    var sleepNote = "Off";
    if (d.sleepAt > 0) {
      var at = new Date(Date.now() + (d.sleepAt - d.now));
      sleepNote = "TV turns off at " + at.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
    }
    $("#sleep-now").textContent = sleepNote;

    $("#addr").textContent = d.address ? "TV at " + d.address : "";
    var note = "";
    if (d.adb === "ok") note = "Full control is on. Keys, typing and app launches work inside every app.";
    else if (d.adb === "waiting") note = "The TV is asking to allow debugging for Sistema. Tick Always allow, then press Allow.";
    else if (/refused/i.test(d.adbError || "")) note = "Full control is off. On the TV, turn on Network debugging in Developer options.";
    else note = "Connecting to the TV for full control. Volume, apps and inputs from the home screen still work.";
    $("#adb-note").textContent = note;
  }

  // ------------------------------------------------------------ make it yours

  var nameTimer = null;
  $("#name").addEventListener("input", function () {
    var v = this.value.trim();
    $("#greet-preview").textContent = "Hello " + (v || (data && data.name) || "");
    $("#greet-preview").appendChild(el("em", null, "."));
    clearTimeout(nameTimer);
    if (!v) return;
    nameTimer = setTimeout(function () { send("/api/settings", { name: v }); }, 600);
  });

  function searchCity() {
    var q = $("#city").value.trim();
    var out = $("#city-results");
    if (q.length < 2) return;
    out.textContent = "";
    fetch("https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name=" + encodeURIComponent(q))
      .then(function (r) { return r.json(); })
      .then(function (j) {
        out.textContent = "";
        (j.results || []).forEach(function (c) {
          var b = el("button", null, c.name);
          var where = [c.admin1, c.country].filter(Boolean).join(", ");
          if (where) b.appendChild(el("small", null, where));
          b.addEventListener("click", function () {
            send("/api/settings", { city: c.name, lat: String(c.latitude), lon: String(c.longitude) }).then(load);
            out.textContent = "";
            $("#city").value = "";
            $("#city").blur();
            toast("Weather is now " + c.name);
          });
          out.appendChild(b);
        });
        if (!out.children.length) out.appendChild(el("p", "hint", "No match. Try the city name only."));
      })
      .catch(function () { toast("City search needs internet on this phone."); });
  }
  $("#city-go").addEventListener("click", searchCity);
  $("#city").addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); searchCity(); } });

  $$("#units button").forEach(function (b) {
    b.addEventListener("click", function () { send("/api/settings", { units: b.dataset.units }).then(load); });
  });

  $("#quote-next").addEventListener("click", function () {
    send("/api/quote/next", {}).then(load);
    toast("New quote on the TV");
  });

  $("#q-add").addEventListener("click", function () {
    var text = $("#q-text").value.trim();
    var author = $("#q-author").value.trim();
    if (text.length < 2) { toast("Write the quote first."); return; }
    send("/api/quotes", { add: { text: text, author: author } }).then(function () {
      $("#q-text").value = "";
      $("#q-author").value = "";
      toast("It's on your TV now");
      load();
    });
  });

  $$("#sleep button").forEach(function (b) {
    b.addEventListener("click", function () {
      var m = Number(b.dataset.min);
      send("/api/sleep", { minutes: m }).then(load);
      $$("#sleep button").forEach(function (x) { x.classList.toggle("on", x === b); });
      toast(m ? "TV turns off in " + (m >= 60 ? (m / 60) + (m === 60 ? " hour" : " hours") : m + " minutes") : "Sleep timer off");
    });
  });

  $("#forget").addEventListener("click", function () {
    if (!confirm("Forget this phone? You'll need a new code from the TV to pair again.")) return;
    api("/api/forget", {}).catch(function () {}).then(function () {
      token = null;
      try { localStorage.removeItem(TOKEN_KEY); } catch (e) {}
      showPair();
    });
  });

  // ------------------------------------------------------------ lifecycle

  function load() {
    if (!token) { showPair(); return Promise.resolve(); }
    return api("/api/state").then(function (j) {
      data = j;
      showMain();
      render();
    }, function (e) {
      if (e.message !== "pair") toast(e.message);
    });
  }

  function schedule() {
    clearInterval(refreshTimer);
    refreshTimer = setInterval(function () { if (!document.hidden) load(); }, 15000);
  }

  document.addEventListener("visibilitychange", function () { if (!document.hidden) load(); });

  if (token) {
    api("/api/health").then(function (h) {
      if (h.paired) { showMain(); load(); } else { showPair(); }
    }, function (e) { showPair(); $("#pair-msg").textContent = e.message; });
  } else {
    showPair();
  }
  schedule();
})();
