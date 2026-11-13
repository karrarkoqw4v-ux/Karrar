/* Velox App — تطبيق مستقل يعمل بالكامل داخل الهاتف (بدون خادم). البيانات في local-db.js */
(function () {
  'use strict';
  var IS_APP = /VeloxApp/.test(navigator.userAgent);
  if (IS_APP) document.documentElement.classList.add('is-app');
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var page = document.body.dataset.page;
  var isPublic = document.body.dataset.public === '1';
  var qs = new URLSearchParams(location.search);

  var store = {
    get: function (k) { try { return JSON.parse(localStorage.getItem('vxa_' + k)); } catch (e) { return null; } },
    set: function (k, v) { try { localStorage.setItem('vxa_' + k, JSON.stringify(v)); } catch (e) {} },
    del: function (k) { try { localStorage.removeItem('vxa_' + k); } catch (e) {} }
  };
  var user = null, info = {};

  /* ───────── أدوات ───────── */
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function ltr(x) { return '<bdi dir="ltr">' + esc(x) + '</bdi>'; }
  function num(n) { return Number(n || 0).toLocaleString('en'); }
  function val(id) { var e = document.getElementById(id); return e ? e.value.trim() : ''; }
  function emoji(n, size) { return '<img class="em" src="assets/emoji/' + n + '.webp" alt=""' + (size ? ' style="width:' + size + 'px;height:' + size + 'px"' : '') + '>'; }
  function toast(msg, err) {
    var t = document.createElement('div');
    t.className = 'toast' + (err ? ' err' : ''); t.textContent = msg; document.body.appendChild(t);
    setTimeout(function () { t.style.transition = 'opacity .4s'; t.style.opacity = '0'; }, 2800);
    setTimeout(function () { t.remove(); }, 3300);
  }
  function api(path, method, body) { return window.VeloxLocal.request(method || 'GET', path, body); }
  function setBusy(btn, busy) {
    if (!btn) return;
    if (busy) { btn.dataset.html = btn.innerHTML; btn.innerHTML = '<span class="spin"></span> جاري المعالجة...'; btn.style.pointerEvents = 'none'; }
    else { btn.innerHTML = btn.dataset.html || btn.innerHTML; btn.style.pointerEvents = ''; }
  }
  function fail(el, msg) {
    toast(msg, true);
    var f = el && el.closest && el.closest('.in');
    if (f) { f.classList.add('shake'); setTimeout(function () { f.classList.remove('shake'); }, 600); }
  }
  function serverDown() { toast('حدث خطأ، حاول مرة ثانية', true); }
  function statusClass(s) { s = String(s || ''); return /مكتمل/.test(s) ? 'g' : /مرفوض/.test(s) ? 'r' : 'y'; }
  function setBind(k, v) { $$('[data-bind="' + k + '"]').forEach(function (el) { el.textContent = v; }); }
  function showEmpty(n) { var e = $('[data-empty]'); if (e) e.hidden = !!n; var l = $('#list'); if (l && l.classList.contains('glass')) l.hidden = !n; }
  function trow(i, title, sub, right) {
    return '<div class="tr px" data-i="' + i + '"><div class="fl"><b dir="auto">' + esc(title) + '</b><div class="xs mut" dir="auto">' + esc(sub || '') + '</div></div>' + (right || '') + '</div>';
  }
  function clicks(sel, fn) { var l = $(sel); if (l) l.addEventListener('click', function (e) { var t = e.target.closest('[data-i]'); if (t) fn(+t.dataset.i); }); }
  var BRANDS = { 'تيك توك': ['tk', 'tiktok'], 'انستغرام': ['ig', 'instagram'], 'فيسبوك': ['fb', 'facebook'], 'يوتيوب': ['yt', 'youtube'], 'كواي': ['kw', 'kuaishou'], 'تليجرام': ['tg', 'telegram'], 'ثريدز': ['th', 'threads'], 'واتساب': ['wa', 'whatsapp'] };
  function brandBadge(p) {
    var b = BRANDS[p];
    if (!b) return '<span class="eb">' + emoji(p === 'خدمات مميزة' ? 'e_1f48e' : 'e_2764') + '</span>';
    return '<span class="bb2 ' + b[0] + '"><img src="assets/brands/' + b[1] + '.svg" alt=""></span>';
  }

  /* نافذة سفلية للنماذج */
  function sheet(title, fields, actions) {
    var bg = document.createElement('div'); bg.className = 'sheet-bg';
    var h = '<div class="sheet"><h2 style="font-size:20px">' + esc(title) + '</h2>';
    fields.forEach(function (f) {
      h += '<div class="fld2"><label for="sf_' + f.id + '">' + esc(f.label) + '</label><div class="in">' +
        (f.type === 'textarea'
          ? '<textarea id="sf_' + f.id + '" style="all:unset;flex:1;min-height:96px;font:600 16px Tajawal,sans-serif;color:#fff;white-space:pre-wrap;padding:12px 0;direction:rtl">' + esc(f.value) + '</textarea>'
          : '<input id="sf_' + f.id + '" ' + (f.type === 'number' ? 'inputmode="numeric" ' : '') + (f.type === 'password' ? 'type="password" ' : '') + 'value="' + esc(f.value == null ? '' : f.value) + '" placeholder="' + esc(f.ph || '') + '" autocapitalize="off">') + '</div></div>';
    });
    h += '<div class="col2" style="margin-top:4px">';
    actions.forEach(function (a, i) { h += '<a class="cta ' + (a.cls || 'g') + ' sm px rp" data-act="' + i + '" role="button" tabindex="0" style="height:52px;' + (a.wide ? 'grid-column:1/-1;' : '') + '">' + esc(a.label) + '</a>'; });
    h += '</div></div>';
    bg.innerHTML = h; document.body.appendChild(bg);
    function close() { bg.remove(); }
    bg.addEventListener('click', function (e) {
      if (e.target === bg) return close();
      var b = e.target.closest('[data-act]'); if (!b) return;
      var a = actions[+b.dataset.act], vals = {};
      fields.forEach(function (f) { var el = $('#sf_' + f.id, bg); vals[f.id] = el ? el.value.trim() : ''; });
      if (a.close) return close();
      setBusy(b, true);
      Promise.resolve(a.fn(vals)).then(function (ok) { setBusy(b, false); if (ok !== false) close(); });
    });
    return close;
  }
  function ok(r, msg) { if (r.ok) { if (msg) toast(msg); return true; } if (r.offline) serverDown(); else toast((r.data && r.data.message) || 'فشلت العملية', true); return false; }

  /* ───────── البداية ───────── */
  function bindUser() {
    if (!user) return;
    setBind('name', user.name); setBind('username', user.username); setBind('uid', user.uid); setBind('email', user.email || '—');
    setBind('balance', num(user.balance)); setBind('spent', num(user.spent)); setBind('orders', num(user.orders));
    setBind('created_at', String(user.created_at || '').slice(0, 10)); setBind('daily_points', num(user.daily_points));
    setBind('initial', (user.name || 'م').trim().charAt(0));
    $$('[data-admin-only]').forEach(function (el) { el.hidden = !user.admin; });
  }
  function bindInfo() {
    setBind('app_name', info.name || 'Velox'); setBind('contact_text', info.contact_text || '');
    $$('[data-support]').forEach(function (a) { if (info.support_link) { a.href = info.support_link; a.hidden = false; } });
    $$('[data-channel],[data-channel2]').forEach(function (a) { if (info.channel_link) { a.href = info.channel_link; a.hidden = false; } });
    var st = $('#supTile'); if (st && !info.channel_link) st.style.gridColumn = '1 / -1';
    var nc = $('#noContact'); if (nc) nc.hidden = !!(info.support_link || info.channel_link);
  }

  function start() {
    api('/app/info').then(function (r) {
      if (r.offline) { if (!isPublic || page === 'index') serverDown(); if (!isPublic) return; }
      else { info = r.data; bindInfo(); }
      var tok = localStorage.getItem('vxa_session');
      if (isPublic) {
        if (tok && (page === 'index' || page === 'login' || page === 'register')) {
          api('/me').then(function (m) { if (m.ok) location.replace('home.html'); });
        }
        return;
      }
      if (!tok) { location.replace('login.html'); return; }
      api('/me').then(function (m) {
        if (m.offline) return serverDown();
        if (!m.ok) { localStorage.removeItem('vxa_session'); if (m.status === 403) alert(m.data.message || 'الحساب محظور'); location.replace('login.html'); return; }
        user = m.data.user; bindUser();
        if (/^admin/.test(page) && !user.admin) { location.replace('home.html'); return; }
        if (loaders[page]) loaders[page]();
      });
    });
  }

  /* ───────── صفحات المستخدم ───────── */
  var loaders = {
    rashq: function () {
      api('/catalog?kind=rashq').then(function (r) {
        var c = r.data.categories || {};
        $$('[data-count]').forEach(function (el) { var n = c[el.dataset.count]; if (n) { el.textContent = n; el.hidden = false; } });
      });
    },
    'rashq-services': function () {
      var p = qs.get('p') || 'تيك توك';
      $('.ttl').firstChild.textContent = p;
      $('#plats').innerHTML = Object.keys(BRANDS).map(function (n) { return '<a href="rashq-services.html?p=' + encodeURIComponent(n) + '"' + (n === p ? ' class="on"' : '') + '>' + brandBadge(n) + n + '</a>'; }).join('');
      api('/services?kind=rashq&category=' + encodeURIComponent(p)).then(function (r) {
        var rows = r.data.services || []; setBind('count', num(rows.length));
        $('#list').innerHTML = rows.map(function (s) {
          return '<a class="item px rp" href="order.html?id=' + s.id + '">' + brandBadge(p) + '<div class="fl"><b dir="auto">' + esc(s.name) + '</b><div class="xs mut">من ' + num(s.min_qty) + ' إلى ' + num(s.max_qty) + (s.description ? ' · ' + esc(s.description) : '') + '</div></div>' +
            '<span class="pill y">' + emoji('e_1f48e', 14) + ' ' + ltr(num(s.price)) + ' <span class="xs" style="font-weight:500">لكل 1000</span></span></a>';
        }).join('');
        $('#list').hidden = !rows.length; showEmpty(rows.length);
      });
    },
    'game-category': function () {
      var c = qs.get('c') || ''; $('.ttl').firstChild.textContent = c;
      api('/services?kind=game&category=' + encodeURIComponent(c)).then(function (r) {
        var rows = r.data.services || [];
        $('#list').innerHTML = rows.map(function (s) {
          return '<a class="item px rp" href="order.html?id=' + s.id + '"><span class="eb">' + emoji('e_1f48e') + '</span><div class="fl"><b dir="auto">' + esc(s.name) + '</b><div class="xs mut" dir="auto">' + esc(s.quantity || '') + (s.description ? ' · ' + esc(s.description) : '') + '</div></div><span class="pill y">' + emoji('e_1f48e', 14) + ' ' + ltr(num(s.price)) + '</span></a>';
        }).join('');
        showEmpty(rows.length);
      });
    },
    order: function () {
      var box = $('#orderBox'), svc = null;
      function calc() {
        if (!svc) return;
        var price = svc.price;
        if (svc.kind === 'rashq') { var q = parseInt(val('qty'), 10) || 0; price = q ? Math.max(1, Math.round(svc.price * q / 1000)) : 0; }
        $('#oPrice').textContent = price ? num(price) : '—';
        var left = user.balance - price;
        $('#oNeedLbl').textContent = left >= 0 ? 'المتبقي بعد الطلب' : 'يلزم شحن';
        $('#oNeed').textContent = price ? num(Math.abs(left)) : '—'; $('#oNeed').style.color = left >= 0 ? '' : '#FFA3A3';
      }
      api('/services/one?id=' + encodeURIComponent(qs.get('id') || '')).then(function (r) {
        if (!r.ok) { box.innerHTML = '<div class="mut" style="text-align:center">الخدمة غير موجودة أو متوقفة</div>'; return; }
        svc = r.data.service; setBind('svc_name', svc.name);
        var back = $('.hd .back'); back.href = svc.kind === 'game' ? 'game-category.html?c=' + encodeURIComponent(svc.category) : 'rashq-services.html?p=' + encodeURIComponent(svc.category);
        box.innerHTML = '<b style="font-size:18px" dir="auto">' + esc(svc.name) + '</b>' + (svc.description ? '<div class="mut xs" dir="auto">' + esc(svc.description) + '</div>' : '') +
          (svc.kind === 'game' && svc.quantity ? '<div class="mut xs">الكمية: ' + esc(svc.quantity) + '</div>' : '') +
          '<div class="fld2"><label for="link">' + (svc.kind === 'game' ? 'معرف اللاعب أو الحساب' : 'رابط الحساب أو المنشور') + '</label><div class="in"><i class="ic i-link"></i><input id="link" autocapitalize="off" placeholder="' + (svc.kind === 'game' ? 'ID' : 'https://') + '"></div></div>' +
          (svc.kind === 'rashq' ? '<div class="fld2"><label for="qty">الكمية</label><div class="in"><i class="ic i-hash"></i><input id="qty" inputmode="numeric" placeholder="' + num(svc.min_qty) + '"></div><span class="xs mut">الحد الأدنى ' + num(svc.min_qty) + ' · الحد الأعلى ' + num(svc.max_qty) + '</span></div>' : '');
        var q = $('#qty'); if (q) q.addEventListener('input', calc);
        calc();
      });
      $('[data-order]').addEventListener('click', function () {
        if (!svc) return;
        var btn = this, link = val('link'), qty = parseInt(val('qty'), 10) || 0;
        if (!link) return fail($('#link'), svc.kind === 'game' ? 'اكتب معرف اللاعب' : 'اكتب الرابط');
        if (svc.kind === 'rashq' && !qty) return fail($('#qty'), 'اكتب الكمية');
        setBusy(btn, true);
        api('/orders', 'POST', { service_id: svc.id, link: link, quantity: qty }).then(function (r) {
          setBusy(btn, false);
          if (!ok(r)) return;
          toast('تم إنشاء الطلب #' + r.data.order_id);
          setTimeout(function () { location.href = 'track.html?id=' + r.data.order_id; }, 700);
        });
      });
    },
    orders: function () {
      api('/orders').then(function (r) {
        var all = r.data.orders || []; setBind('orders_count', num(all.length));
        function draw(k) {
          var rows = all.filter(function (o) { return !k || o.kind === k; });
          $('#list').innerHTML = rows.map(function (o) {
            return '<a class="item px rp" href="track.html?id=' + o.id + '"><span class="eb">' + emoji(o.kind === 'game' ? 'e_1f3ae' : 'e_1f680') + '</span>' +
              '<div class="fl"><b dir="auto">' + esc(o.service_name) + '</b><div class="xs mut">' + ltr('#' + o.id) + ' · ' + ltr(o.created_at) + '</div></div>' +
              '<div style="text-align:end"><span class="pill ' + statusClass(o.status) + '">' + esc(o.status) + '</span><div class="xs mut" style="margin-top:4px">' + num(o.price) + '</div></div></a>';
          }).join('');
          $('#list').hidden = !rows.length; showEmpty(rows.length);
        }
        draw('');
        $$('#tabs a').forEach(function (a) { a.addEventListener('click', function () { $$('#tabs a').forEach(function (x) { x.classList.remove('on'); }); a.classList.add('on'); draw(a.dataset.k); }); });
      });
    },
    track: function () { var id = qs.get('id'); if (id) { $('#oid').value = id; doTrack(); } },
    recharge: function () {
      api('/ledger').then(function (r) {
        var rows = r.data.items || [];
        var L = { order: 'طلب', code: 'كود', daily: 'هدية يومية', transfer: 'تحويل', admin: 'المسؤول', refund: 'استرداد' };
        $('#list').innerHTML = rows.map(function (x) { return '<div class="tr"><div class="fl"><b>' + esc(L[x.kind] || x.kind) + '</b><div class="xs mut" dir="auto">' + esc(x.note || '') + ' · ' + ltr(x.created_at) + '</div></div><b style="color:' + (x.amount >= 0 ? '#7BE5A4' : '#FFA3A3') + '">' + ltr((x.amount > 0 ? '+' : '') + num(x.amount)) + '</b></div>'; }).join('');
        $('#list').hidden = !rows.length; showEmpty(rows.length);
      });
    },
    collect: function () {
      var st = $('#dailyState'); if (!user.can_daily) st.textContent = 'استلمت هديتك اليوم. عد بعد 24 ساعة من آخر استلام.';
    },
    transfer: function () {
      api('/ledger').then(function (r) {
        var rows = (r.data.items || []).filter(function (x) { return x.kind === 'transfer'; });
        $('#list').innerHTML = rows.map(function (x) { return '<div class="tr"><div class="fl"><b dir="auto">' + esc(x.note) + '</b><div class="xs mut">' + ltr(x.created_at) + '</div></div><b style="color:' + (x.amount >= 0 ? '#7BE5A4' : '#FFA3A3') + '">' + ltr((x.amount > 0 ? '+' : '') + num(x.amount)) + '</b></div>'; }).join('');
        $('#list').hidden = !rows.length; showEmpty(rows.length);
      });
    }
  };

  /* ───────── المسؤول ───────── */
  function adm(path, body) { return api('/admin/' + path, body ? 'POST' : 'GET', body); }
  function servicesAdmin(kind) {
    var isR = kind === 'rashq', cur = '', rows = [];
    function tabs() {
      adm('services?kind=' + kind + '&category=' + encodeURIComponent(cur)).then(function (r) {
        var names = r.data.categories || []; if (!cur) cur = names[0];
        $('#tabs').innerHTML = names.map(function (n) { return '<a data-n="' + esc(n) + '"' + (n === cur ? ' class="on"' : '') + '>' + esc(n) + '</a>'; }).join('');
        draw();
      });
    }
    function draw() {
      adm('services?kind=' + kind + '&category=' + encodeURIComponent(cur)).then(function (r) {
        rows = r.data.services || [];
        $('#list').innerHTML = rows.map(function (s, i) { return trow(i, s.name + (s.active ? '' : ' (مخفية)'), isR ? 'من ' + num(s.min_qty) + ' إلى ' + num(s.max_qty) : s.quantity, '<span class="pill y">' + emoji('e_1f48e', 14) + ' ' + ltr(num(s.price)) + '</span>'); }).join('');
        $('#list').hidden = !rows.length; showEmpty(rows.length);
      });
    }
    function fields(s) {
      s = s || {};
      return isR ? [{ id: 'name', label: 'اسم الخدمة', value: s.name }, { id: 'price', label: 'السعر لكل 1000', type: 'number', value: s.price }, { id: 'min_qty', label: 'الحد الأدنى', type: 'number', value: s.min_qty }, { id: 'max_qty', label: 'الحد الأقصى', type: 'number', value: s.max_qty }, { id: 'description', label: 'الوصف', value: s.description }]
        : [{ id: 'name', label: 'اسم الخدمة', value: s.name }, { id: 'quantity', label: 'الكمية', value: s.quantity }, { id: 'price', label: 'السعر', type: 'number', value: s.price }, { id: 'description', label: 'الوصف', value: s.description }];
    }
    function save(extra) { return function (v) { v.kind = kind; v.category = cur; for (var k in extra) v[k] = extra[k]; return adm('service/save', v).then(function (r) { var o = ok(r, 'تم الحفظ'); if (o) tabs(); return o; }); }; }
    $('#tabs').addEventListener('click', function (e) { var a = e.target.closest('[data-n]'); if (a) { cur = a.dataset.n; tabs(); } });
    clicks('#list', function (i) {
      var s = rows[i];
      sheet('تعديل: ' + s.name, fields(s), [
        { label: 'حفظ', fn: save({ id: s.id, active: s.active }) },
        { label: s.active ? 'إخفاء' : 'إظهار', cls: 'dk', fn: save({ id: s.id, active: s.active ? 0 : 1 }) },
        { label: 'حذف الخدمة', cls: 'r', wide: true, fn: function () { return adm('service/delete', { id: s.id }).then(function (r) { var o = ok(r, 'تم الحذف'); if (o) tabs(); return o; }); } }]);
    });
    $('[data-add]').addEventListener('click', function () { sheet('خدمة جديدة في: ' + cur, fields(), [{ label: 'إضافة', wide: true, fn: save({ active: 1 }) }]); });
    tabs();
  }

  Object.assign(loaders, {
    admin: function () { adm('stats').then(function (r) { Object.keys(r.data).forEach(function (k) { setBind('a_' + k, num(r.data[k])); }); }); },
    'admin-games': function () { servicesAdmin('game'); },
    'admin-rashq': function () { servicesAdmin('rashq'); },
    'admin-orders': function () {
      var cur = '', rows = [], sts = [];
      function load() {
        adm('orders?status=' + encodeURIComponent(cur)).then(function (r) {
          rows = r.data.orders || []; sts = r.data.statuses || [];
          $('#tabs').innerHTML = '<a data-s=""' + (cur ? '' : ' class="on"') + '>الكل</a>' + sts.map(function (s) { return '<a data-s="' + esc(s) + '"' + (s === cur ? ' class="on"' : '') + '>' + esc(s) + '</a>'; }).join('');
          $('#list').innerHTML = rows.map(function (o, i) { return trow(i, o.service_name, '#' + o.id + ' · ' + o.user_name + ' (@' + o.username + ')', '<div style="text-align:end"><span class="pill ' + statusClass(o.status) + '">' + esc(o.status) + '</span><div class="xs mut">' + num(o.price) + '</div></div>'); }).join('');
          $('#list').hidden = !rows.length; showEmpty(rows.length);
        });
      }
      $('#tabs').addEventListener('click', function (e) { var a = e.target.closest('[data-s]'); if (a) { cur = a.dataset.s; load(); } });
      clicks('#list', function (i) {
        var o = rows[i];
        var acts = sts.filter(function (s) { return s !== o.status; }).map(function (s) { return { label: s, cls: s === 'مرفوض' ? 'r' : s === 'مكتمل' ? 'g' : 'dk', fn: function (v) { return adm('order/status', { id: o.id, status: s, note: v.note }).then(function (r) { var k = ok(r, 'تم تحديث الحالة'); if (k) load(); return k; }); } }; });
        sheet('الطلب #' + o.id + ' · ' + o.service_name, [{ id: 'link', label: 'الرابط/المعرف', value: o.link }, { id: 'note', label: 'ملاحظة للعميل (اختياري)', value: o.note }], acts.concat([{ label: 'إغلاق', close: true, cls: 'dk', wide: true }]));
      });
      load();
    },
    'admin-users': function () {
      var t, rows = [];
      function load() {
        adm('users?q=' + encodeURIComponent(val('q'))).then(function (r) {
          rows = r.data.users || []; setBind('total', num(r.data.total));
          $('#list').innerHTML = rows.map(function (u, i) { return trow(i, u.name + (u.banned ? ' (محظور)' : ''), '@' + u.username + ' · ' + u.uid, '<b>' + num(u.balance) + '</b>'); }).join('');
          $('#list').hidden = !rows.length; showEmpty(rows.length);
        });
      }
      $('#q').addEventListener('input', function () { clearTimeout(t); t = setTimeout(load, 300); });
      clicks('#list', function (i) {
        var u = rows[i];
        function op(mode) { return function (v) { return adm('user/balance', { id: u.id, amount: v.amount || 0, mode: mode }).then(function (r) { var k = ok(r, 'تم'); if (k) load(); return k; }); }; }
        var acts = [{ label: '➕ إضافة رصيد', fn: op('add') }, { label: '➖ خصم رصيد', cls: 'r', fn: op('deduct') }, { label: 'تصفير الرصيد', cls: 'r', fn: op('reset') }];
        if (!u.admin) acts.push({ label: u.banned ? 'إلغاء الحظر' : 'حظر الحساب', cls: 'dk', fn: function () { return adm('user/ban', { id: u.id, banned: !u.banned }).then(function (r) { var k = ok(r, 'تم'); if (k) load(); return k; }); } });
        sheet(u.name + ' · ' + num(u.balance), [{ id: 'amount', label: 'المبلغ', type: 'number', ph: '100' }], acts);
      });
      load();
    },
    'admin-codes': function () {
      var rows = [];
      function load() {
        adm('codes').then(function (r) {
          rows = r.data.codes || [];
          $('#list').innerHTML = rows.map(function (c, i) { return trow(i, c.code, 'استُخدم ' + c.used_count + ' من ' + (c.max_uses || '∞'), '<span class="pill y">' + emoji('e_1f48e', 14) + ' ' + ltr(num(c.points)) + '</span>'); }).join('');
          $('#list').hidden = !rows.length; showEmpty(rows.length);
        });
      }
      clicks('#list', function (i) { sheet('الكود ' + rows[i].code, [], [{ label: 'حذف الكود', cls: 'r', wide: true, fn: function () { return adm('codes/delete', { code: rows[i].code }).then(function (r) { var k = ok(r, 'تم الحذف'); if (k) load(); return k; }); } }]); });
      $('[data-add]').addEventListener('click', function () {
        sheet('كود جديد', [{ id: 'code', label: 'الكود (فارغ = تلقائي)' }, { id: 'points', label: 'النقاط', type: 'number' }, { id: 'max_uses', label: 'أقصى استخدام (0 = غير محدود)', type: 'number', value: 0 }], [
          { label: 'إنشاء', wide: true, fn: function (v) { return adm('codes/add', v).then(function (r) { var k = ok(r, 'تم إنشاء الكود ' + (r.data.code || '')); if (k) load(); return k; }); } }]);
      });
      load();
    },
    'admin-settings': function () {
      var L = { app_name: 'اسم التطبيق', currency: 'اسم العملة', daily_points: 'نقاط الهدية اليومية', registration_open: 'التسجيل مفتوح (1 = نعم، 0 = لا)', support_link: 'رابط الدعم', channel_link: 'رابط القناة الرسمية', contact_text: 'نص الشحن والتواصل' };
      adm('settings').then(function (r) {
        var s = r.data.settings || {};
        $('#form').innerHTML = Object.keys(L).map(function (k) { return '<div class="fld2"><label for="st_' + k + '">' + L[k] + '</label><div class="in"><input id="st_' + k + '" value="' + esc(s[k]) + '" autocapitalize="off"></div></div>'; }).join('');
      });
      $('[data-save-settings]').addEventListener('click', function () {
        var o = {}; Object.keys(L).forEach(function (k) { o[k] = val('st_' + k); });
        adm('settings/save', { settings: o }).then(function (r) { ok(r, 'تم حفظ الإعدادات'); });
      });
    },
    'admin-backups': function () {
      $('[data-backup]').addEventListener('click', function () {
        window.VeloxLocal.backupJson().then(function (txt) {
          var name = 'velox_backup_' + new Date().toISOString().slice(0, 10) + '.json';
          if (window.VeloxApp && window.VeloxApp.save) { window.VeloxApp.save(name, 'application/json', btoa(unescape(encodeURIComponent(txt)))); return; }
          var a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([txt], { type: 'application/json' })); a.download = name; document.body.appendChild(a); a.click(); a.remove();
        });
      });
      $('[data-restore]').addEventListener('change', function () {
        var f = this.files && this.files[0]; if (!f) return;
        var rd = new FileReader();
        rd.onload = function () { window.VeloxLocal.restoreJson(String(rd.result)).then(function () { toast('تمت الاستعادة'); setTimeout(function () { location.href = 'login.html'; }, 800); }).catch(function () { toast('ملف النسخة غير صالح', true); }); };
        rd.readAsText(f);
      });
    },
    'admin-server': function () {
      adm('storage').then(function (r) {
        var d = r.data.storage || {}, rows = [['الحسابات', d.users], ['الطلبات', d.orders], ['الخدمات', d.services], ['الأكواد', d.codes], ['حركات الرصيد', d.ledger], ['حجم البيانات', ((d.size || 0) / 1024).toFixed(1) + ' KB'], ['مكان التخزين', 'ذاكرة هذا الجهاز']];
        $('#list').innerHTML = rows.map(function (x) { return '<div class="tr"><span class="mut">' + esc(x[0]) + '</span><b style="margin-inline-start:auto">' + esc(x[1]) + '</b></div>'; }).join('');
      });
    }
  });

  /* ───────── إجراءات ───────── */
  function doTrack() {
    var id = val('oid').replace(/^#/, '');
    if (!/^\d+$/.test(id)) return fail($('#oid'), 'اكتب رقم الطلب');
    api('/track?id=' + id).then(function (r) {
      var box = $('#result');
      if (!r.ok) { box.innerHTML = '<div class="glass" style="text-align:center;color:#FFA3A3">' + esc(r.data.message || 'لم يتم العثور على الطلب') + '</div>'; return; }
      var o = r.data.order, st = statusClass(o.status), pct = st === 'g' ? 100 : st === 'r' ? 0 : (o.status === 'قيد التنفيذ' ? 55 : 15);
      box.innerHTML = '<div class="glass" style="display:flex;flex-direction:column;gap:14px"><div class="rw"><div class="fl"><h2 style="font-size:19px">الطلب ' + ltr('#' + o.id) + '</h2><div class="xs mut" dir="auto">' + esc(o.service_name) + '</div></div><span class="pill ' + st + '">' + esc(o.status) + '</span></div>' +
        '<div class="prog"><i style="width:' + pct + '%"></i></div>' +
        '<div class="rw s"><span class="mut">السعر</span><b>' + num(o.price) + '</b></div>' + (o.quantity ? '<div class="rw s"><span class="mut">الكمية</span><b>' + num(o.quantity) + '</b></div>' : '') +
        '<div class="rw s"><span class="mut">التاريخ</span><b>' + ltr(o.created_at) + '</b></div>' +
        (o.link ? '<div class="rw s"><span class="mut">الرابط</span><b style="direction:ltr;max-width:60%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + esc(o.link) + '</b></div>' : '') +
        (o.note ? '<div class="item"><span class="eb y">' + emoji('e_1f514') + '</span><span class="fl xs" dir="auto">' + esc(o.note) + '</span></div>' : '') + '</div>';
    });
  }
  function doLogin(btn) {
    var id = val('id'), pw = val('pw');
    if (!id) return fail($('#id'), 'اكتب اسم المستخدم أو البريد أو الأيدي');
    if (!pw) return fail($('#pw'), 'اكتب كلمة السر');
    setBusy(btn, true);
    api('/login', 'POST', { login: id, password: pw }).then(function (r) {
      setBusy(btn, false);
      if (r.ok) { location.href = 'home.html'; }
      else if (r.offline) serverDown(); else fail($('#pw'), r.data.message || 'بيانات الدخول غير صحيحة');
    });
  }
  function doRegister(btn) {
    var n = val('n'), u = val('u').toLowerCase(), p = val('p'), m = val('m');
    if (n.length < 2) return fail($('#n'), 'اكتب اسمك');
    if (!/^[a-z0-9_.]{3,20}$/.test(u)) return fail($('#u'), 'اسم المستخدم: 3 إلى 20 حرفاً إنجليزياً أو أرقام');
    if (p.length < 6) return fail($('#p'), 'كلمة السر 6 أحرف على الأقل');
    if (m && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(m)) return fail($('#m'), 'البريد الإلكتروني غير صحيح');
    setBusy(btn, true);
    api('/register', 'POST', { name: n, username: u, password: p, email: m }).then(function (r) {
      setBusy(btn, false);
      if (r.ok) { location.href = 'home.html'; }
      else if (r.offline) serverDown(); else fail($('#u'), r.data.message || 'تعذّر إنشاء الحساب');
    });
  }
  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-submit],[data-toggle-pw],[data-logout],[data-track],[data-redeem],[data-daily],[data-transfer],[data-chpw],[data-backup]');
    if (!t) return;
    if (t.matches('[data-submit="login"]')) doLogin(t);
    else if (t.matches('[data-submit="register"]')) doRegister(t);
    else if (t.matches('[data-toggle-pw]')) { var i = $('#pw'); if (i) i.type = i.type === 'password' ? 'text' : 'password'; }
    else if (t.matches('[data-logout]')) { localStorage.removeItem('vxa_session'); location.href = 'login.html'; }
    else if (t.matches('[data-track]')) doTrack();
    else if (t.matches('[data-redeem]')) {
      var c = val('code'); if (!c) return fail($('#code'), 'اكتب الكود'); setBusy(t, true);
      api('/redeem', 'POST', { code: c }).then(function (r) { setBusy(t, false); if (ok(r)) { toast('تمت إضافة ' + num(r.data.points) + ' نقطة'); setBind('balance', num(r.data.balance)); $('#code').value = ''; loaders.recharge(); } });
    }
    else if (t.matches('[data-daily]')) {
      setBusy(t, true);
      api('/daily', 'POST', {}).then(function (r) { setBusy(t, false); if (ok(r)) { toast('تمت إضافة ' + num(r.data.points) + ' نقطة'); setBind('balance', num(r.data.balance)); $('#dailyState').textContent = 'تم الاستلام. عد بعد 24 ساعة.'; } });
    }
    else if (t.matches('[data-transfer]')) {
      var to = val('to'), amt = parseInt(val('amt'), 10) || 0;
      if (!to) return fail($('#to'), 'اكتب اسم المستخدم أو الأيدي'); if (amt <= 0) return fail($('#amt'), 'اكتب عدد النقاط');
      setBusy(t, true);
      api('/transfer', 'POST', { to: to, amount: amt }).then(function (r) { setBusy(t, false); if (ok(r, 'تم التحويل')) { setBind('balance', num(r.data.balance)); $('#to').value = ''; $('#amt').value = ''; loaders.transfer(); } });
    }
    else if (t.matches('[data-chpw]')) {
      sheet('تغيير كلمة السر', [{ id: 'old', label: 'كلمة السر الحالية', type: 'password' }, { id: 'new', label: 'كلمة السر الجديدة', type: 'password' }], [
        { label: 'حفظ', wide: true, fn: function (v) { return api('/me/password', 'POST', v).then(function (r) { return ok(r, 'تم تغيير كلمة السر'); }); } }]);
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      if (page === 'login' || page === 'register') { var b = $('[data-submit]'); if (b) b.click(); }
      else if (page === 'track') doTrack();
    }
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('[role="button"]')) { e.preventDefault(); e.target.click(); }
  });
  document.addEventListener('error', function (e) {
    var img = e.target;
    if (img && img.tagName === 'IMG' && img.classList.contains('em')) {
      if (img.dataset.fx !== '1' && /\.webp$/.test(img.src)) { img.dataset.fx = '1'; img.src = img.src.replace(/\.webp$/, '.png'); } else img.style.visibility = 'hidden';
    }
  }, true);

  start();
})();
