/* Bawsala site - behaviour. Three motion systems: (1) scroll reveal, (2) ambient aurora + parallax, (3) interactive. No dependencies. */
(function () {
  "use strict";
  var d = document, root = d.documentElement;
  var reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  var fine = matchMedia("(pointer: fine)").matches;
  var $ = function (s, c) { return (c || d).querySelector(s); };
  var $$ = function (s, c) { return [].slice.call((c || d).querySelectorAll(s)); };

  /* theme */
  var saved = null;
  try { saved = localStorage.getItem("theme"); } catch (e) {}
  root.setAttribute("data-theme", saved || (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark"));
  d.addEventListener("click", function (e) {
    var t = e.target.closest("[data-theme-toggle]");
    if (!t) return;
    var n = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", n);
    try { localStorage.setItem("theme", n); } catch (x) {}
  });

  /* values from config.js */
  var S = window.SITE || {};
  $$("[data-site]").forEach(function (el) {
    var v = S;
    el.getAttribute("data-site").split(".").forEach(function (k) { v = v && v[k]; });
    if (v == null) return;
    if (el.tagName === "A") el.href = (el.hasAttribute("data-mail") ? "mailto:" : "") + v;
    else el.textContent = v;
  });
  $$("[data-year]").forEach(function (el) { el.textContent = S.year || new Date().getFullYear(); });

  /* header */
  var hdr = $(".hdr"), nav = $(".nav"), burger = $(".burger");
  function onScroll() { if (hdr) hdr.classList.toggle("stuck", scrollY > 12); }
  onScroll();
  addEventListener("scroll", onScroll, { passive: true });
  if (burger) burger.addEventListener("click", function () {
    var o = nav.classList.toggle("open");
    burger.setAttribute("aria-expanded", o);
  });
  $$(".nav a").forEach(function (a) { a.addEventListener("click", function () { nav.classList.remove("open"); }); });

  /* SYSTEM 1: scroll reveal with stagger */
  var items = $$(".rv");
  if ("IntersectionObserver" in window && !reduce) {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("shown"); io.unobserve(e.target); } });
    }, { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });
    items.forEach(function (el) {
      var idx = el.parentElement ? [].indexOf.call(el.parentElement.children, el) : 0;
      el.style.setProperty("--i", Math.min(idx, 6));
      io.observe(el);
    });
  } else items.forEach(function (el) { el.classList.add("shown"); });

  /* SYSTEM 2: ambient - parallax phones + pointer-follow aurora */
  var par = $$("[data-par]");
  if (par.length && !reduce) {
    var ticking = false;
    var loop = function () {
      par.forEach(function (el) {
        var s = parseFloat(el.getAttribute("data-par")) || 0.1, r = el.getBoundingClientRect();
        el.style.translate = "0 " + (-(r.top + r.height / 2 - innerHeight / 2) * s).toFixed(1) + "px";
      });
      ticking = false;
    };
    addEventListener("scroll", function () { if (!ticking) { ticking = true; requestAnimationFrame(loop); } }, { passive: true });
    loop();
  }
  var au = $(".aurora");
  if (au && !reduce && fine) addEventListener("pointermove", function (e) {
    au.style.transform = "translate(" + ((e.clientX / innerWidth - 0.5) * 40) + "px," + ((e.clientY / innerHeight - 0.5) * 40) + "px)";
  }, { passive: true });

  /* SYSTEM 3: interactive - spotlight cards, 3D tilt, magnetic buttons, counters, typing word */
  $$(".card").forEach(function (c) {
    c.addEventListener("pointermove", function (e) {
      var r = c.getBoundingClientRect();
      c.style.setProperty("--mx", (e.clientX - r.left) + "px");
      c.style.setProperty("--my", (e.clientY - r.top) + "px");
    });
  });
  if (!reduce && fine) {
    $$("[data-tilt]").forEach(function (el) {
      el.classList.add("tilt");
      el.addEventListener("pointermove", function (e) {
        var r = el.getBoundingClientRect(), x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
        el.style.transform = "perspective(900px) rotateY(" + (x * 10).toFixed(2) + "deg) rotateX(" + (-y * 10).toFixed(2) + "deg)";
      });
      el.addEventListener("pointerleave", function () { el.style.transform = ""; });
    });
    $$(".btn-primary,.btn-ghost").forEach(function (b) {
      b.addEventListener("pointermove", function (e) {
        var r = b.getBoundingClientRect();
        b.style.transform = "translate(" + ((e.clientX - r.left - r.width / 2) * 0.18).toFixed(1) + "px," + ((e.clientY - r.top - r.height / 2) * 0.28).toFixed(1) + "px)";
      });
      b.addEventListener("pointerleave", function () { b.style.transform = ""; });
    });
  }
  $$("[data-count]").forEach(function (el) {
    var to = parseFloat(el.getAttribute("data-count")), suf = el.getAttribute("data-suf") || "", done = false;
    function run() {
      if (done) return; done = true;
      if (reduce) { el.textContent = to + suf; return; }
      var t0 = performance.now();
      (function f(t) {
        var p = Math.min(1, (t - t0) / 1400);
        el.textContent = Math.round(to * (1 - Math.pow(1 - p, 4))) + suf;
        if (p < 1) requestAnimationFrame(f);
      })(t0);
    }
    if ("IntersectionObserver" in window) new IntersectionObserver(function (es, o) { if (es[0].isIntersecting) { run(); o.disconnect(); } }, { threshold: 0.5 }).observe(el);
    else run();
  });
  var tw = $("[data-words]");
  if (tw) {
    var words = tw.getAttribute("data-words").split("|"), wi = 0, ci = 0, del = false;
    if (reduce) tw.textContent = words[0];
    else {
      tw.classList.add("caret");
      (function tick() {
        var w = words[wi];
        ci += del ? -1 : 1;
        tw.textContent = w.slice(0, ci);
        var wait = del ? 45 : 95;
        if (!del && ci === w.length) { wait = 1700; del = true; }
        else if (del && ci === 0) { del = false; wi = (wi + 1) % words.length; wait = 300; }
        setTimeout(tick, wait);
      })();
    }
  }

  /* tabs */
  $$("[role=tablist]").forEach(function (tl) {
    var tabs = $$("[role=tab]", tl);
    var panels = tabs.map(function (t) { return d.getElementById(t.getAttribute("aria-controls")); });
    function sel(i, anim) {
      tabs.forEach(function (t, k) {
        t.setAttribute("aria-selected", k === i); t.tabIndex = k === i ? 0 : -1;
        if (!panels[k]) return;
        panels[k].hidden = k !== i;
        if (k === i && anim && panels[k].animate) panels[k].animate([{ opacity: 0, transform: "translateY(18px)" }, { opacity: 1, transform: "none" }], { duration: 480, easing: "cubic-bezier(.22,1,.36,1)" });
      });
    }
    tabs.forEach(function (t, i) {
      t.addEventListener("click", function () { sel(i, true); });
      t.addEventListener("keydown", function (e) {
        var n = i + (e.key === "ArrowLeft" ? 1 : e.key === "ArrowRight" ? -1 : 0);
        if (n !== i && n >= 0 && n < tabs.length) { sel(n, true); tabs[n].focus(); }
      });
    });
    if (!reduce) {
      var auto = setInterval(function () {
        var c = tabs.findIndex(function (t) { return t.getAttribute("aria-selected") === "true"; });
        sel((c + 1) % tabs.length, true);
      }, 7000);
      tl.parentElement.addEventListener("pointerdown", function () { clearInterval(auto); }, { once: true });
    }
    sel(0, false);
  });

  /* table of contents highlight */
  var toc = $$(".toc a");
  if (toc.length && "IntersectionObserver" in window) {
    var o2 = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (e.isIntersecting) toc.forEach(function (a) { a.classList.toggle("on", a.getAttribute("href") === "#" + e.target.id); });
      });
    }, { rootMargin: "-30% 0px -60% 0px" });
    toc.map(function (a) { return d.getElementById(a.getAttribute("href").slice(1)); }).filter(Boolean).forEach(function (s) { o2.observe(s); });
  }

  /* copy buttons */
  $$("[data-copy]").forEach(function (b) {
    b.addEventListener("click", function () {
      var v = b.getAttribute("data-copy"), old = b.textContent;
      if (!navigator.clipboard) return;
      navigator.clipboard.writeText(v).then(function () { b.textContent = "تم النسخ ✓"; setTimeout(function () { b.textContent = old; }, 1800); }, function () {});
    });
  });
  /* developer download gate: the APK link is stored ENCRYPTED (AES-GCM, key = PBKDF2(code)); it is only revealed in the browser after the right code. */
  var gate = $("#gate");
  if (gate) {
    var msg = $("#gmsg"), dl = $("#gdl"), input = $("#gcode"), fails = 0, busy = false;
    var b64 = function (s) { return Uint8Array.from(atob(s), function (c) { return c.charCodeAt(0); }); };
    var show = function (url) { dl.href = url; dl.hidden = false; gate.querySelector(".row").hidden = true; gate.querySelector("label").hidden = true; msg.className = "gmsg ok"; msg.textContent = "تم فتح التحميل ✓"; };
    try { var kept = sessionStorage.getItem("apk"); if (kept) show(kept); } catch (e) {}
    gate.addEventListener("submit", function (e) {
      e.preventDefault();
      if (busy) return;
      var L = (window.SITE && SITE.app && SITE.app.apkLocked), code = input.value.trim();
      if (!L || !window.crypto || !crypto.subtle) { msg.className = "gmsg err"; msg.textContent = "المتصفح لا يدعم التحقق. افتح الموقع عبر https."; return; }
      if (!code) { msg.className = "gmsg err"; msg.textContent = "أدخل الرمز."; return; }
      busy = true; msg.className = "gmsg"; msg.textContent = "جارٍ التحقق…";
      crypto.subtle.importKey("raw", new TextEncoder().encode(code), "PBKDF2", false, ["deriveKey"]).then(function (k) {
        return crypto.subtle.deriveKey({ name: "PBKDF2", salt: b64(L.salt), iterations: L.iter, hash: "SHA-256" }, k, { name: "AES-GCM", length: 256 }, false, ["decrypt"]);
      }).then(function (key) {
        return crypto.subtle.decrypt({ name: "AES-GCM", iv: b64(L.iv), additionalData: new TextEncoder().encode("bawsala-apk") }, key, b64(L.ct));
      }).then(function (buf) {
        var url = new TextDecoder().decode(buf);
        try { sessionStorage.setItem("apk", url); } catch (x) {}
        input.value = ""; show(url); busy = false;
      }, function () {
        fails++; input.value = "";
        msg.className = "gmsg err"; msg.textContent = "الرمز غير صحيح." + (fails > 2 ? " انتظر قليلاً قبل المحاولة التالية." : "");
        setTimeout(function () { busy = false; }, Math.min(8000, 800 * fails * fails));
      });
    });
  }
})();
