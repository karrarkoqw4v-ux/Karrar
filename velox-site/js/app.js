/* Velox site — واجهة الموقع. تعمل مع server/server.py، وإذا لم يوجد خادم تعمل بوضع تجريبي. */
(function () {
  'use strict';
  var API = window.VELOX_API || '/api';
  var GOOGLE_CLIENT_ID = window.VELOX_GOOGLE_CLIENT_ID || '';
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var page = document.body.dataset.page;
  var isPublic = document.body.dataset.public === '1';

  var store = {
    get: function (k) { try { return JSON.parse(localStorage.getItem('velox_' + k)); } catch (e) { return null; } },
    set: function (k, v) { try { localStorage.setItem('velox_' + k, JSON.stringify(v)); } catch (e) {} },
    del: function (k) { try { localStorage.removeItem('velox_' + k); } catch (e) {} }
  };

  function toast(msg, err) {
    var t = document.createElement('div');
    t.className = 'toast' + (err ? ' err' : '');
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function () { t.style.transition = 'opacity .4s'; t.style.opacity = '0'; }, 2600);
    setTimeout(function () { t.remove(); }, 3100);
  }

  function api(path, method, body) {
    return fetch(API + path, {
      method: method || 'GET', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (d) { return { ok: r.ok, status: r.status, data: d }; });
    }).catch(function () { return { ok: false, offline: true, status: 0, data: {} }; });
  }

  var user = store.get('user');
  function bind() {
    $$('[data-bind]').forEach(function (el) {
      var k = el.dataset.bind;
      if (k === 'linkCode') { el.textContent = store.get('linkCode') || 'VX-00000'; return; }
      if (user && user[k] != null) el.textContent = k === 'points' ? Number(user[k]).toLocaleString('en') : user[k];
    });
  }

  function demoUser(id, name) {
    return { uid: String(id || '8010837412'), name: name || 'مستخدم', points: 234, balance: 0, orders: 0, admin: false, demo: true };
  }

  function setBusy(btn, busy) {
    if (!btn) return;
    if (busy) { btn.dataset.html = btn.innerHTML; btn.innerHTML = '<span class="spin"></span> جاري المعالجة...'; btn.style.pointerEvents = 'none'; }
    else { btn.innerHTML = btn.dataset.html || btn.innerHTML; btn.style.pointerEvents = ''; }
  }
  function fail(el, msg) {
    toast(msg, true);
    var f = el && el.closest('.in');
    if (f) { f.classList.add('shake'); setTimeout(function () { f.classList.remove('shake'); }, 600); }
  }
  function val(id) { var e = document.getElementById(id); return e ? e.value.trim() : ''; }

  /* الصفحات الداخلية تحتاج تسجيل دخول */
  function guard() {
    if (isPublic) { bind(); return; }
    api('/me').then(function (r) {
      if (r.ok) { user = r.data.user; store.set('user', user); }
      else if (r.offline) { if (!user) { location.replace('login.html'); return; } }
      else if (r.status === 401) { store.del('user'); location.replace('login.html'); return; }
      if (/^admin/.test(page) && user && !user.admin && !user.demo) { location.replace('home.html'); return; }
      bind();
    });
  }

  function doLogin(btn) {
    var id = val('id'), pw = val('pw');
    if (!/^\d{5,15}$/.test(id)) return fail($('#id'), 'اكتب الأيدي (أرقام فقط)');
    if (pw.length < 6) return fail($('#pw'), 'كلمة السر 6 أحرف على الأقل');
    setBusy(btn, true);
    api('/login', 'POST', { id: id, password: pw }).then(function (r) {
      setBusy(btn, false);
      if (r.ok) { user = r.data.user; store.set('user', user); location.href = 'home.html'; }
      else if (r.offline) { user = demoUser(id); store.set('user', user); toast('وضع تجريبي: الخادم غير متصل'); setTimeout(function () { location.href = 'home.html'; }, 700); }
      else if (r.status === 403 && r.data.link_code) { store.set('linkCode', r.data.link_code); location.href = 'link-bot.html'; }
      else fail($('#pw'), r.data.message || 'الأيدي أو كلمة السر غير صحيحة');
    });
  }

  function doRegister(btn) {
    var name = val('n'), id = val('i'), pw = val('p'), mail = val('m');
    if (name.length < 2) return fail($('#n'), 'اكتب اسمك');
    if (!/^\d{5,15}$/.test(id)) return fail($('#i'), 'الأيدي أرقام فقط (من 5 إلى 15)');
    if (pw.length < 6) return fail($('#p'), 'كلمة السر 6 أحرف على الأقل');
    if (mail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(mail)) return fail($('#m'), 'البريد الإلكتروني غير صحيح');
    setBusy(btn, true);
    api('/register', 'POST', { name: name, id: id, password: pw, email: mail }).then(function (r) {
      setBusy(btn, false);
      if (r.ok) { store.set('linkCode', r.data.link_code); store.set('pending', { id: id, name: name }); location.href = 'link-bot.html'; }
      else if (r.offline) { store.set('linkCode', 'VX-' + Math.floor(10000 + Math.random() * 89999)); store.set('pending', { id: id, name: name }); location.href = 'link-bot.html'; }
      else fail($('#i'), r.data.message || 'تعذّر إنشاء الحساب');
    });
  }

  function doLinkCheck(btn) {
    var code = store.get('linkCode'); var pend = store.get('pending') || {};
    setBusy(btn, true);
    api('/link/check', 'POST', { code: code }).then(function (r) {
      setBusy(btn, false);
      if (r.ok && r.data.linked) { user = r.data.user; store.set('user', user); location.href = 'welcome.html'; }
      else if (r.offline) { user = demoUser(pend.id, pend.name); store.set('user', user); location.href = 'welcome.html'; }
      else toast('لم يصل الكود إلى البوت بعد. أرسله ثم حاول مرة ثانية.', true);
    });
  }

  function doLogout() {
    api('/logout', 'POST').then(function () { store.del('user'); location.href = 'login.html'; });
  }

  function doGoogle() {
    if (!GOOGLE_CLIENT_ID) { toast('أضف VELOX_GOOGLE_CLIENT_ID في js/config.js لتفعيل Google', true); return; }
    function start() {
      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: function (resp) {
          api('/google', 'POST', { credential: resp.credential }).then(function (r) {
            if (r.ok) { user = r.data.user; store.set('user', user); location.href = 'home.html'; }
            else toast(r.data.message || 'تعذّر الدخول بحساب Google', true);
          });
        }
      });
      window.google.accounts.id.prompt();
    }
    if (window.google && window.google.accounts) return start();
    var s = document.createElement('script');
    s.src = 'https://accounts.google.com/gsi/client'; s.async = true; s.onload = start;
    document.head.appendChild(s);
  }

  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-submit],[data-toggle-pw],[data-logout],[data-google],[data-copy-code]');
    if (!t) return;
    if (t.matches('[data-submit="login"]')) doLogin(t);
    else if (t.matches('[data-submit="register"]')) doRegister(t);
    else if (t.matches('[data-submit="linkcheck"]')) doLinkCheck(t);
    else if (t.matches('[data-toggle-pw]')) { var i = $('#pw'); if (i) i.type = i.type === 'password' ? 'text' : 'password'; }
    else if (t.matches('[data-logout]')) doLogout();
    else if (t.matches('[data-google]')) doGoogle();
    else if (t.matches('[data-copy-code]')) {
      var c = store.get('linkCode') || '';
      (navigator.clipboard ? navigator.clipboard.writeText(c) : Promise.reject()).then(function () { toast('تم نسخ الكود'); }, function () { toast(c); });
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && (page === 'login' || page === 'register')) { var b = $('[data-submit]'); if (b) b.click(); }
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('[role="button"]')) { e.preventDefault(); e.target.click(); }
  });

  guard();
})();
