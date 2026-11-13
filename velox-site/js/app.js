/* Velox site — الواجهة. كل البيانات حقيقية من server/server.py (نفس قاعدة بيانات البوت). */
(function () {
  'use strict';
  var IS_APP = /VeloxApp/.test(navigator.userAgent);
  if (IS_APP) document.documentElement.classList.add('is-app');
  var GOOGLE_CLIENT_ID = window.VELOX_GOOGLE_CLIENT_ID || '';
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var page = document.body.dataset.page;
  var isPublic = document.body.dataset.public === '1';
  var qs = new URLSearchParams(location.search);

  var store = {
    get: function (k) { try { return JSON.parse(localStorage.getItem('velox_' + k)); } catch (e) { return null; } },
    set: function (k, v) { try { localStorage.setItem('velox_' + k, JSON.stringify(v)); } catch (e) {} },
    del: function (k) { try { localStorage.removeItem('velox_' + k); } catch (e) {} }
  };
  var user = null;
  var API = String(store.get('api') || window.VELOX_API || '/api').replace(/\/$/, '');

  /* ───────── أدوات ───────── */
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function ltr(x) { return '<bdi dir="ltr">' + esc(x) + '</bdi>'; }
  function num(n) { return Number(n || 0).toLocaleString('en'); }
  function toast(msg, err) {
    var t = document.createElement('div');
    t.className = 'toast' + (err ? ' err' : '');
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function () { t.style.transition = 'opacity .4s'; t.style.opacity = '0'; }, 2800);
    setTimeout(function () { t.remove(); }, 3300);
  }
  function api(path, method, body) {
    var h = { 'Content-Type': 'application/json' };
    var tok = store.get('token');
    if (tok) h.Authorization = 'Bearer ' + tok;
    return fetch(API + path, { method: method || 'GET', headers: h, body: body ? JSON.stringify(body) : undefined })
      .then(function (r) {
        return r.json().catch(function () { return {}; }).then(function (d) {
          if (d && d.waiting && page !== 'waiting' && page !== 'bot-link') { location.replace('waiting.html'); }
          return { ok: r.ok, status: r.status, data: d };
        });
      }).catch(function () { return { ok: false, offline: true, status: 0, data: {} }; });
  }
  function emoji(n, size) { return '<img class="em" src="assets/emoji/' + n + '.webp" alt=""' + (size ? ' style="width:' + size + 'px;height:' + size + 'px"' : '') + '>'; }
  function statusClass(s) {
    s = String(s || '');
    if (/مكتمل|تم|نجح|completed/i.test(s)) return 'g';
    if (/مرفوض|ملغي|فشل|رفض|cancel|fail/i.test(s)) return 'r';
    return 'y';
  }
  function val(id) { var e = document.getElementById(id); return e ? e.value.trim() : ''; }
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
  function serverDown() { toast('تعذّر الاتصال بالخادم. تأكد أن server.py يعمل وأن VELOX_API صحيح في js/config.js', true); }

  /* ───────── ربط القيم ───────── */
  function setBind(k, v) {
    $$('[data-bind="' + k + '"]').forEach(function (el) { el.textContent = v; });
  }
  function bindUser() {
    if (!user) return;
    setBind('name', user.name); setBind('uid', user.uid); setBind('username', user.username || '');
    setBind('points', num(user.points)); setBind('spent_points', num(user.spent_points));
    setBind('referrals', num(user.referrals)); setBind('total_orders', num(user.total_orders));
    setBind('initial', (user.name || 'م').trim().charAt(0));
    $$('[data-admin-only]').forEach(function (el) { el.hidden = !user.admin; });
  }

  /* ───────── بدء الصفحة ───────── */
  function start() {
    if (IS_APP && !store.get('api') && page !== 'server' && page !== 'bot-link') { location.replace('server.html'); return; }
    if (isPublic) {
      if (page === 'link-bot') setBind('linkCode', store.get('linkCode') || 'VX-00000');
      if (loaders[page]) loaders[page]();
      return;
    }
    if (!store.get('token')) { location.replace('login.html'); return; }
    api('/me').then(function (r) {
      if (r.offline) { serverDown(); return; }
      if (r.status === 401) { store.del('token'); location.replace('login.html'); return; }
      user = r.data.user; bindUser();
      if (/^admin/.test(page) && !user.admin) { location.replace('home.html'); return; }
      var fn = loaders[page]; if (fn) fn();
    });
  }

  /* ───────── صفحات البيانات ───────── */
  var BRANDS = { 'تيك توك': ['tk', 'tiktok'], 'انستغرام': ['ig', 'instagram'], 'فيسبوك': ['fb', 'facebook'], 'يوتيوب': ['yt', 'youtube'], 'كواي': ['kw', 'kuaishou'], 'تليجرام': ['tg', 'telegram'], 'ثريدز': ['th', 'threads'], 'واتساب': ['wa', 'whatsapp'] };
  function brandBadge(p, size) {
    var b = BRANDS[p];
    if (!b) return '<span class="eb">' + emoji(p === 'خدمات مميزة' ? 'e_1f48e' : 'e_2764') + '</span>';
    return '<span class="bb2 ' + b[0] + (size ? ' ' + size : '') + '"><img src="assets/brands/' + b[1] + '.svg" alt=""></span>';
  }
  function showEmpty(list) {
    var e = $('[data-empty]'); if (e) e.hidden = !!list;
  }
  var KIND_EMOJI = { 'رشق': 'e_1f680', 'ألعاب': 'e_1f3ae', 'تمويل': 'e_1f4e2', 'خدمة': 'e_1f4e6' };

  var loaders = {
    orders: function () {
      api('/orders').then(function (r) {
        var all = r.data.orders || [];
        setBind('orders_count', num(all.length));
        function draw(k) {
          var rows = all.filter(function (o) { return !k || o.kind === k; });
          $('#list').innerHTML = rows.map(function (o) {
            return '<a class="item px rp" href="track.html?id=' + esc(o.id) + '"><span class="eb">' + emoji(KIND_EMOJI[o.kind] || 'e_1f4e6') + '</span>' +
              '<div class="fl"><b dir="auto">' + esc(o.title) + '</b><div class="xs mut"><bdi dir="ltr">#' + esc(o.id) + '</bdi> · ' + ltr(o.created_at) + '</div></div>' +
              '<div style="text-align:end"><span class="pill ' + statusClass(o.status) + '">' + esc(o.status || '—') + '</span><div class="xs mut" style="margin-top:4px">' + num(o.price) + '</div></div></a>';
          }).join('');
          showEmpty(rows.length);
        }
        draw('');
        $$('#tabs a').forEach(function (a) {
          a.addEventListener('click', function () { $$('#tabs a').forEach(function (x) { x.classList.remove('on'); }); a.classList.add('on'); draw(a.dataset.k); });
        });
      });
    },

    track: function () {
      var id = qs.get('id'); if (id) { $('#oid').value = id; doTrack(); }
    },

    'rashq-services': function () {
      var p = qs.get('p') || 'تيك توك';
      setBind('plat', p);
      $('#plats').innerHTML = Object.keys(BRANDS).map(function (n) {
        return '<a href="rashq-services.html?p=' + encodeURIComponent(n) + '"' + (n === p ? ' class="on"' : '') + '>' + brandBadge(n) + n + '</a>';
      }).join('');
      api('/services/rashq?platform=' + encodeURIComponent(p)).then(function (r) {
        var rows = r.data.services || [];
        setBind('count', num(rows.length));
        $('#list').innerHTML = rows.map(function (s) {
          return '<a class="item px rp" href="order.html?k=rashq&p=' + encodeURIComponent(p) + '&i=' + s.index + '">' + brandBadge(p) +
            '<div class="fl"><b dir="auto">' + esc(s.name) + '</b><div class="xs mut">' + (s.min ? 'من ' + num(s.min) + ' إلى ' + num(s.max) : esc(s.description || '')) + '</div></div>' +
            '<span class="pill y">💎 ' + num(s.price) + ' /1000</span></a>';
        }).join('');
        showEmpty(rows.length);
      });
    },

    'game-category': function () {
      var c = qs.get('c') || '';
      setBind('cat', c);
      api('/services/games?category=' + encodeURIComponent(c)).then(function (r) {
        var rows = r.data.services || [];
        $('#list').innerHTML = rows.map(function (s) {
          return '<a class="item px rp" href="order.html?k=game&c=' + encodeURIComponent(c) + '&i=' + s.index + '"><span class="eb">' + emoji('e_1f48e') + '</span>' +
            '<div class="fl"><b dir="auto">' + esc(s.name) + '</b><div class="xs mut">' + esc(s.quantity || '') + (s.description ? ' · ' + esc(s.description) : '') + '</div></div>' +
            '<span class="pill y">💎 ' + num(s.price) + '</span></a>';
        }).join('');
        showEmpty(rows.length);
      });
    },

    order: function () {
      var k = qs.get('k'), i = parseInt(qs.get('i'), 10), box = $('#orderBox');
      var url = k === 'game' ? '/services/games?category=' + encodeURIComponent(qs.get('c') || '') : '/services/rashq?platform=' + encodeURIComponent(qs.get('p') || '');
      api(url).then(function (r) {
        var s = (r.data.services || []).filter(function (x) { return x.index === i; })[0];
        if (!s) { box.innerHTML = '<div class="mut" style="text-align:center">الخدمة غير موجودة</div>'; return; }
        setBind('svc_name', s.name);
        function calc() {
          var price = s.price;
          if (k !== 'game') { var q = parseInt(val('qty'), 10) || 0; price = Math.max(1, Math.round(Number(s.price) * q / 1000)); if (!q) price = 0; }
          $('#oPrice').textContent = num(price);
          var left = (user.points || 0) - price;
          $('#oNeedLbl').textContent = left >= 0 ? 'المتبقي بعد الطلب' : 'يلزم شحن';
          $('#oNeed').textContent = num(Math.abs(left)); $('#oNeed').style.color = left >= 0 ? '' : '#FFA3A3';
        }
        if (k === 'game') {
          box.innerHTML = '<b style="font-size:18px">' + esc(s.name) + '</b><div class="mut xs">' + esc(s.quantity || '') + (s.description ? ' · ' + esc(s.description) : '') + '</div>';
        } else {
          box.innerHTML = '<b style="font-size:18px">' + esc(s.name) + '</b>' + (s.description ? '<div class="mut xs">' + esc(s.description) + '</div>' : '') +
            '<div class="fld2"><label for="qty">الكمية</label><div class="in"><i class="ic i-hash"></i><input id="qty" inputmode="numeric" placeholder="' + num(s.min) + '"></div><span class="xs mut">الحد الأدنى ' + num(s.min) + ' · الحد الأعلى ' + num(s.max) + '</span></div>';
          $('#qty').addEventListener('input', calc);
        }
        calc();
      });
    },

    recharge: function () {
      api('/recharges').then(function (r) {
        var rows = r.data.recharges || [];
        $('#list').innerHTML = rows.map(function (x) {
          return '<div class="tr"><div class="fl"><b>' + esc(x.method || '') + '</b><div class="xs mut">' + ltr(x.created_at || '') + (x.note ? ' · ' + esc(x.note) : '') + '</div></div><b style="color:#7BE5A4"><bdi dir="ltr">+' + num(x.points) + '</bdi></b></div>';
        }).join('');
        $('#list').hidden = !rows.length; showEmpty(rows.length);
      });
    },

    tamweel: function () {
      api('/tamweel').then(function (r) {
        var rows = r.data.items || [];
        $('#list').innerHTML = rows.map(function (x) {
          return '<div class="item"><span class="eb">' + emoji('e_1f4e2') + '</span><div class="fl"><b>' + esc(x.channel_name || x.channel_link || '—') + '</b><div class="xs mut">' + num(x.members) + ' عضو · ' + num(x.price) + ' نقطة</div></div><span class="pill ' + statusClass(x.status) + '">' + esc(x.status || '—') + '</span></div>';
        }).join('');
        showEmpty(rows.length);
      });
    }
  };


  /* ───────── لوحة التحكم (نفس أقسام لوحة البوت) ───────── */
  function sheet(title, fields, actions) {
    var bg = document.createElement('div'); bg.className = 'sheet-bg';
    var h = '<div class="sheet"><h2 style="font-size:20px">' + esc(title) + '</h2>';
    fields.forEach(function (f) {
      h += '<div class="fld2"><label for="sf_' + f.id + '">' + esc(f.label) + '</label><div class="in">' +
        (f.type === 'textarea'
          ? '<textarea id="sf_' + f.id + '" style="all:unset;flex:1;min-height:96px;font:600 16px Tajawal,sans-serif;color:#fff;white-space:pre-wrap;padding:12px 0;direction:rtl">' + esc(f.value) + '</textarea>'
          : '<input id="sf_' + f.id + '" ' + (f.type === 'number' ? 'inputmode="numeric" ' : '') + (f.ro ? 'readonly ' : '') + 'value="' + esc(f.value == null ? '' : f.value) + '" placeholder="' + esc(f.ph || '') + '">') + '</div></div>';
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
  function adm(path, body) { return api('/admin/' + path, body ? 'POST' : 'GET', body); }
  function okmsg(r, msg) { if (r.ok) { toast(msg || 'تم'); return true; } toast((r.data && r.data.message) || 'فشلت العملية', true); return false; }
  function trow(i, title, sub, right) {
    return '<div class="tr px" data-i="' + i + '"><div class="fl"><b dir="auto">' + esc(title) + '</b><div class="xs mut" dir="auto">' + esc(sub || '') + '</div></div>' + (right || '') + '</div>';
  }
  function clicks(listSel, fn) { $(listSel).addEventListener('click', function (e) { var t = e.target.closest('[data-i]'); if (t) fn(+t.dataset.i); }); }
  function swHtml(on) { return '<span class="sw' + (on ? ' on' : '') + '"></span>'; }
  function showList(rows) { var l = $('#list'); l.hidden = !rows; showEmpty(rows); }

  function servicesAdmin(kind) {
    var isR = kind === 'rashq', cur = '', rows = [];
    var base = isR ? '/services/rashq' : '/services/games', param = isR ? 'platform' : 'category', key = isR ? 'platform' : 'category';
    function tabs() {
      api(base).then(function (r) {
        var d = r.data.platforms || r.data.categories || {}, names = Object.keys(d);
        if (!cur && names.length) cur = names[0];
        $('#tabs').innerHTML = names.map(function (n) { return '<a data-n="' + esc(n) + '"' + (n === cur ? ' class="on"' : '') + '>' + esc(n) + ' (' + d[n] + ')</a>'; }).join('');
        draw();
      });
    }
    function draw() {
      if (!cur) return showList(0);
      api(base + '?' + param + '=' + encodeURIComponent(cur)).then(function (r) {
        rows = r.data.services || [];
        $('#list').innerHTML = rows.map(function (s, i) {
          return trow(i, s.name, isR ? 'من ' + num(s.min) + ' إلى ' + num(s.max) : (s.quantity || ''), '<span class="pill y">' + emoji('e_1f48e', 14) + ' ' + ltr(num(s.price)) + '</span>');
        }).join('');
        showList(rows.length);
      });
    }
    function fields(s) {
      s = s || {};
      return isR
        ? [{ id: 'name', label: 'اسم الخدمة', value: s.name }, { id: 'price', label: 'السعر لكل 1000', type: 'number', value: s.price }, { id: 'min', label: 'الحد الأدنى', type: 'number', value: s.min }, { id: 'max', label: 'الحد الأقصى', type: 'number', value: s.max }, { id: 'description', label: 'الوصف', value: s.description }, { id: 'api_id', label: 'رقم الخدمة في الموقع', value: s.api_id }]
        : [{ id: 'name', label: 'اسم الخدمة', value: s.name }, { id: 'quantity', label: 'الكمية', value: s.quantity }, { id: 'price', label: 'السعر', type: 'number', value: s.price }, { id: 'description', label: 'الوصف', value: s.description }, { id: 'receive_id', label: 'معرف الاستلام', value: s.receive_id }];
    }
    $('#tabs').addEventListener('click', function (e) { var a = e.target.closest('[data-n]'); if (a) { cur = a.dataset.n; tabs(); } });
    clicks('#list', function (i) {
      var s = rows[i];
      sheet('تعديل: ' + s.name, fields(s), [
        { label: 'حفظ', fn: function (v) { v[key] = cur; v.index = i; return adm(kind + '/update', v).then(function (r) { var ok = okmsg(r, 'تم الحفظ'); if (ok) draw(); return ok; }); } },
        { label: 'حذف', cls: 'r', fn: function () { return adm(kind + '/delete', (function () { var o = { index: i }; o[key] = cur; return o; })()).then(function (r) { var ok = okmsg(r, 'تم الحذف'); if (ok) tabs(); return ok; }); } }
      ]);
    });
    $('[data-add]').addEventListener('click', function () {
      sheet('إضافة خدمة إلى: ' + cur, fields(), [{ label: 'إضافة', wide: true, fn: function (v) { v[key] = cur; return adm(kind + '/add', v).then(function (r) { var ok = okmsg(r, 'تمت الإضافة'); if (ok) tabs(); return ok; }); } }]);
    });
    tabs();
  }

  Object.assign(loaders, {
    admin: function () { adm('stats').then(function (r) { Object.keys(r.data).forEach(function (k) { setBind('a_' + k, num(r.data[k])); }); }); },
    'admin-stats': function () { loaders.admin(); },
    'admin-games': function () { servicesAdmin('games'); },
    'admin-rashq': function () { servicesAdmin('rashq'); },

    'admin-users': function () {
      var t, rows = [];
      function load() {
        adm('users?q=' + encodeURIComponent(val('q'))).then(function (r) {
          rows = r.data.users || []; setBind('total', num(r.data.total));
          $('#list').innerHTML = rows.map(function (u, i) {
            return trow(i, u.name, u.uid + (u.username ? ' · ' + u.username : ''), '<div style="text-align:end"><b>' + num(u.points) + '</b><div><span class="pill ' + (u.web ? 'g' : '') + '">' + (u.web ? 'بوت + تطبيق' : 'بوت') + '</span></div></div>');
          }).join('');
          showList(rows.length);
        });
      }
      $('#q').addEventListener('input', function () { clearTimeout(t); t = setTimeout(load, 300); });
      clicks('#list', function (i) {
        var u = rows[i];
        function op(mode) { return function (v) { return adm('user/points', { uid: u.uid, amount: v.amount || 0, mode: mode }).then(function (r) { okmsg(r, 'تم'); if (r.ok) load(); return r.ok; }); }; }
        sheet(u.name + ' · ' + num(u.points) + ' نقطة', [{ id: 'amount', label: 'عدد النقاط', type: 'number', ph: '100' }], [
          { label: '➕ إضافة', fn: op('add') }, { label: '➖ خصم', cls: 'r', fn: op('deduct') },
          { label: '🔄 تصفير النقاط', cls: 'r', wide: true, fn: op('reset') }]);
      });
      load();
    },

    'admin-recharge': function () {
      adm('stats').then(function (r) { setBind('a_recharges', num(r.data.recharges)); setBind('a_recharge_points', num(r.data.recharge_points)); });
      function toggles() {
        adm('toggles').then(function (r) {
          var t = (r.data.toggles || []).filter(function (x) { return /asiacell|stars/.test(x.key); });
          $('#rtoggles').innerHTML = t.map(function (x) { return '<div class="item px" data-k="' + x.key + '"><div class="fl"><b>' + esc(x.label) + '</b></div>' + swHtml(x.on) + '</div>'; }).join('');
        });
      }
      $('#rtoggles').addEventListener('click', function (e) { var t = e.target.closest('[data-k]'); if (t) adm('toggles/set', { key: t.dataset.k }).then(function (r) { okmsg(r, 'تم التغيير'); toggles(); }); });
      function log() {
        adm('recharges').then(function (r) {
          var rows = r.data.recharges || [];
          $('#list').innerHTML = rows.map(function (x, i) { return trow(i, x.user_id, (x.created_at || '') + (x.note ? ' · ' + x.note : ''), '<span class="pill">' + esc(x.method || '') + '</span><b style="color:#7BE5A4">' + ltr('+' + num(x.points)) + '</b>'); }).join('');
          showList(rows.length);
        });
      }
      $('[data-add]').addEventListener('click', function () {
        sheet('شحن يدوي لمستخدم', [{ id: 'uid', label: 'أيدي المستخدم', type: 'number' }, { id: 'points', label: 'عدد النقاط', type: 'number' }], [
          { label: 'شحن', wide: true, fn: function (v) { return adm('recharge/manual', v).then(function (r) { var ok = okmsg(r, 'تم الشحن'); if (ok) log(); return ok; }); } }]);
      });
      toggles(); log();
    },

    'admin-toggles': function () {
      function load() {
        adm('toggles').then(function (r) {
          var t = r.data.toggles || [];
          $('#list').innerHTML = t.map(function (x, i) { return trow(i, x.label, x.key, swHtml(x.on)); }).join('');
          showList(t.length); $('#list').dataset.keys = JSON.stringify(t.map(function (x) { return x.key; }));
        });
      }
      clicks('#list', function (i) { var ks = JSON.parse($('#list').dataset.keys); adm('toggles/set', { key: ks[i] }).then(function (r) { okmsg(r, 'تم التغيير'); load(); }); });
      load();
    },

    'admin-settings': function () {
      var items = [];
      function load() {
        adm('settings').then(function (r) {
          items = r.data.items || [];
          $('#list').innerHTML = items.map(function (x, i) {
            var shown = x.type === 'bool' ? '' : String(x.value).slice(0, 26);
            return trow(i, x.key, x.type === 'text' ? 'نص' : x.type === 'num' ? 'رقم' : 'تشغيل/إيقاف', x.type === 'bool' ? swHtml(x.value) : '<span class="mut xs" dir="auto" style="max-width:48%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + esc(shown) + '</span>');
          }).join('');
          showList(items.length);
        });
      }
      clicks('#list', function (i) {
        var x = items[i];
        if (x.type === 'bool') { adm('settings/set', { key: x.key, value: !x.value }).then(function (r) { okmsg(r, 'تم'); load(); }); return; }
        sheet(x.key, [{ id: 'value', label: 'القيمة', type: x.type === 'num' ? 'number' : (String(x.value).length > 40 ? 'textarea' : 'text'), value: x.value }], [
          { label: 'حفظ', wide: true, fn: function (v) { return adm('settings/set', { key: x.key, value: v.value }).then(function (r) { var ok = okmsg(r, 'تم الحفظ'); if (ok) load(); return ok; }); } }]);
      });
      load();
    },

    'admin-codes': function () {
      var rows = [];
      function load() {
        adm('codes').then(function (r) {
          rows = r.data.codes || [];
          $('#list').innerHTML = rows.map(function (c, i) { return trow(i, c.code, 'استُخدم ' + c.used_count + ' من ' + (c.max_uses || '∞'), '<span class="pill y">' + emoji('e_1f48e', 14) + ' ' + ltr(num(c.points)) + '</span>'); }).join('');
          showList(rows.length);
        });
      }
      clicks('#list', function (i) {
        sheet('الكود: ' + rows[i].code, [], [{ label: 'حذف الكود', cls: 'r', wide: true, fn: function () { return adm('codes/delete', { code: rows[i].code }).then(function (r) { var ok = okmsg(r, 'تم الحذف'); if (ok) load(); return ok; }); } }]);
      });
      $('[data-add]').addEventListener('click', function () {
        sheet('كود جديد', [{ id: 'code', label: 'الكود (اتركه فارغاً لإنشاء تلقائي)' }, { id: 'points', label: 'النقاط', type: 'number' }, { id: 'max_uses', label: 'أقصى استخدام (0 = غير محدود)', type: 'number', value: 0 }], [
          { label: 'إنشاء', wide: true, fn: function (v) { return adm('codes/add', v).then(function (r) { var ok = okmsg(r, 'تم إنشاء الكود ' + (r.data.code || '')); if (ok) load(); return ok; }); } }]);
      });
      $('[data-gift]').addEventListener('click', function () {
        sheet('رابط هدية', [{ id: 'points', label: 'النقاط', type: 'number' }, { id: 'max_uses', label: 'أقصى استخدام (0 = غير محدود)', type: 'number', value: 0 }, { id: 'expiry_hours', label: 'المدة بالساعات (0 = بدون)', type: 'number', value: 0 }], [
          { label: 'إنشاء الرابط', wide: true, fn: function (v) {
            return adm('gift/create', v).then(function (r) {
              if (!okmsg(r, 'تم إنشاء الرابط')) return false;
              setTimeout(function () { sheet('رابط الهدية', [{ id: 'l', label: 'انسخ الرابط', value: r.data.link, ro: true }], [{ label: 'إغلاق', close: true, wide: true }]); }, 200);
              return true;
            });
          } }]);
      });
      load();
    },

    'admin-channels': function () {
      var rows = [];
      function load() {
        adm('channels').then(function (r) {
          rows = r.data.channels || [];
          $('#list').innerHTML = rows.map(function (c, i) { return trow(i, c.channel_name || c.channel_id, c.channel_id, '<span class="pill">' + num(c.target_members) + '</span>'); }).join('');
          showList(rows.length);
        });
      }
      clicks('#list', function (i) {
        sheet(rows[i].channel_name || rows[i].channel_id, [], [{ label: 'حذف القناة', cls: 'r', wide: true, fn: function () { return adm('channels/delete', { channel_id: rows[i].channel_id }).then(function (r) { var ok = okmsg(r, 'تم الحذف'); if (ok) load(); return ok; }); } }]);
      });
      $('[data-add]').addEventListener('click', function () {
        sheet('إضافة قناة', [{ id: 'channel_id', label: 'معرف القناة (@channel أو -100...)' }, { id: 'channel_name', label: 'الاسم' }, { id: 'target_members', label: 'العدد المستهدف', type: 'number', value: 0 }, { id: 'invite_link', label: 'رابط الدعوة (اختياري)' }], [
          { label: 'إضافة', wide: true, fn: function (v) { return adm('channels/add', v).then(function (r) { var ok = okmsg(r, 'تمت الإضافة'); if (ok) load(); return ok; }); } }]);
      });
      load();
    },

    'admin-free': function () {
      var rows = [];
      function load() {
        adm('free').then(function (r) {
          rows = r.data.items || [];
          $('#list').innerHTML = rows.map(function (x, i) { return trow(i, x.name, x.description || x.created_at || '', ''); }).join('');
          showList(rows.length);
        });
      }
      clicks('#list', function (i) {
        sheet(rows[i].name, [], [{ label: 'حذف الخدمة', cls: 'r', wide: true, fn: function () { return adm('free/delete', { index: i }).then(function (r) { var ok = okmsg(r, 'تم الحذف'); if (ok) load(); return ok; }); } }]);
      });
      $('[data-add]').addEventListener('click', function () {
        sheet('خدمة مجانية', [{ id: 'name', label: 'الاسم' }, { id: 'description', label: 'الوصف' }], [
          { label: 'إضافة', wide: true, fn: function (v) { return adm('free/add', v).then(function (r) { var ok = okmsg(r, 'تمت الإضافة'); if (ok) load(); return ok; }); } }]);
      });
      load();
    },

    'admin-backups': function () {
      function load() {
        adm('backups').then(function (r) {
          var rows = r.data.backups || [];
          $('#list').innerHTML = rows.map(function (b, i) { return trow(i, b.name, (b.size / 1048576).toFixed(2) + ' MB · ' + b.mtime, '<a class="back px" role="button" tabindex="0" data-dl="' + esc(b.name) + '" style="width:40px;height:40px;flex-basis:40px;border-radius:13px"><i class="ic i-down" style="width:18px;height:18px"></i></a>'); }).join('');
          showList(rows.length);
        });
      }
      $('[data-backup]').addEventListener('click', function () { adm('backups/create', {}).then(function (r) { okmsg(r, 'تم إنشاء النسخة'); load(); }); });
      $('[data-clean]').addEventListener('click', function () { sheet('حذف كل النسخ القديمة؟', [], [{ label: 'نعم، احذف', cls: 'r', fn: function () { return adm('backups/clean', {}).then(function (r) { var ok = okmsg(r, 'تم الحذف'); if (ok) load(); return ok; }); } }, { label: 'إلغاء', close: true, cls: 'dk' }]); });
      load();
    },

    'admin-server': function () {
      adm('server').then(function (r) {
        var s = r.data.server || {}, rows = [['اسم الاستضافة', s.provider], ['اسم الجهاز', s.hostname], ['عنوان الاستضافة (IP)', s.public_ip || s.local_ip], ['المنفذ', s.port], ['عنوان الخادم للتطبيق', s.url], ['النظام', s.os], ['بايثون', s.python], ['ربط التطبيق', r.data.linked ? 'مفعّل ' + (r.data.linked_at || '') : 'غير مفعّل']];
        $('#list').innerHTML = rows.map(function (x) { return '<div class="tr"><span class="mut">' + esc(x[0]) + '</span><b dir="ltr" style="margin-inline-start:auto;text-align:end;word-break:break-all">' + esc(x[1] == null ? '—' : x[1]) + '</b></div>'; }).join('');
      });
    },

    'bot-link': function () {
      var msgEl = $('#linkMsg');
      function msg(t, err) { msgEl.textContent = t; msgEl.style.color = err ? '#FFA3A3' : '#A9B6DA'; }
      $('#lf').addEventListener('change', function () {
        var f = this.files && this.files[0]; if (!f) return;
        if (f.size > 20480) return msg('حجم الملف كبير. الحد الأقصى 20 KB', true);
        var rd = new FileReader();
        rd.onload = function () {
          var j; try { j = JSON.parse(String(rd.result)); } catch (e) { return msg('الملف ليس بصيغة JSON صحيحة', true); }
          if (!j || j.type !== 'velox-link' || !j.key || !j.server || !j.server.url) return msg('ملف ربط غير صالح. المطلوب: type=velox-link و key و server.url', true);
          verify(j);
        };
        rd.readAsText(f);
      });
      function post(base, path, key) {
        return fetch(base + '/api' + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ key: key }) })
          .then(function (r) { return r.json().then(function (d) { return { ok: r.ok, d: d }; }); });
      }
      function verify(j) {
        var base = String(j.server.url).replace(/\/+$/, '');
        msg('جاري التحقق من البوت...');
        post(base, '/bot/verify', j.key).then(function (x) {
          if (!x.ok) return msg(x.d.message || 'فشل التحقق من البوت', true);
          card(x.d.bot, base, j.key);
        }).catch(function () { msg('تعذّر الاتصال بالخادم ' + base + ' (تأكد أنه يعمل والمنفذ مفتوح)', true); });
      }
      function card(b, base, key) {
        msg('');
        var rows = [['اسم البوت', b.name], ['معرف البوت', '@' + b.username], ['رقم البوت', b.id], ['عدد المستخدمين', num(b.users)], ['الطلبات المنجزة', num(b.orders)], ['خدمات الألعاب', num(b.games)], ['خدمات الرشق', num(b.rashq)], ['قنوات الاشتراك', num(b.channels)], ['الاستضافة', b.server && b.server.provider], ['عنوان الخادم', b.server && b.server.url]];
        var el = $('#botCard'); el.style.display = 'flex';
        el.innerHTML = '<div class="glass rise" style="display:flex;flex-direction:column;gap:6px"><div class="rw" style="margin-bottom:6px"><span class="eb g">' + emoji('e_2705') + '</span><div class="fl"><h2 style="font-size:19px">تم التحقق من البوت</h2><div class="xs mut">تأكد من التفاصيل ثم اربطه</div></div></div>' +
          rows.map(function (x) { return '<div class="rw s" style="padding:6px 0;border-bottom:1px solid rgba(255,255,255,.07)"><span class="mut">' + esc(x[0]) + '</span><b dir="ltr" style="text-align:end;word-break:break-all">' + esc(x[1] == null ? '—' : x[1]) + '</b></div>'; }).join('') + '</div>' +
          (b.linked ? '<div class="item"><span class="eb g">' + emoji('e_2705') + '</span><span class="fl xs">هذا البوت مربوط بالتطبيق مسبقاً.</span></div>' : '') +
          '<a class="cta g px rp" id="doLink" role="button" tabindex="0">' + (b.linked ? 'إعادة تثبيت الربط' : 'اربط الآن') + '</a>';
        $('#doLink').addEventListener('click', function () {
          var btn = this; setBusy(btn, true);
          post(base, '/bot/link', key).then(function (x) {
            setBusy(btn, false);
            if (!x.ok) return msg(x.d.message || 'فشل الربط', true);
            store.set('api', base + '/api');
            btn.outerHTML = '<div class="glass" style="text-align:center;display:flex;flex-direction:column;gap:10px"><span style="margin:0 auto">' + emoji('e_2705', 56) + '</span><h2>تم ربط البوت بالتطبيق</h2><p class="mut" style="line-height:1.7">' + (x.d.already ? 'كان البوت مربوطاً مسبقاً.' : 'أُرسل إشعار إلى ' + num(x.d.notified) + ' مستخدم: البوت يعمل الآن داخل التطبيق للجميع حتى المطور.') + '</p><a class="cta g px rp" href="login.html">تسجيل الدخول</a></div>';
          }).catch(function () { setBusy(btn, false); msg('تعذّر الاتصال بالخادم', true); });
        });
      }
    },

    waiting: function () {
      $('[data-retry]').addEventListener('click', function () {
        api('/app/status').then(function (r) {
          if (r.ok && r.data.linked) location.href = 'login.html';
          else toast(r.offline ? 'تعذّر الاتصال بالخادم' : 'ما زال البوت غير مربوط. سيصلك إشعار عند التفعيل.', true);
        });
      });
    }
  });

  function doTrack() {
    var id = val('oid').replace(/^#/, '');
    if (!/^\d+$/.test(id)) return fail($('#oid'), 'اكتب رقم الطلب');
    api('/track?id=' + id).then(function (r) {
      var box = $('#result');
      if (!r.ok) { box.innerHTML = '<div class="glass" style="text-align:center;color:#FFA3A3">' + esc(r.data.message || 'لم يتم العثور على الطلب') + '</div>'; return; }
      var o = r.data.order, st = statusClass(o.status), pct = st === 'g' ? 100 : st === 'r' ? 0 : 50;
      box.innerHTML = '<div class="glass" style="display:flex;flex-direction:column;gap:14px"><div class="rw"><div class="fl"><h2 style="font-size:19px">الطلب <bdi dir="ltr">#' + esc(o.id) + '</bdi></h2><div class="xs mut" dir="auto">' + esc(o.title) + '</div></div><span class="pill ' + st + '">' + esc(o.status || '—') + '</span></div>' +
        '<div class="prog"><i style="width:' + pct + '%"></i></div>' +
        '<div class="rw s"><span class="mut">السعر</span><b>' + num(o.price) + '</b></div><div class="rw s"><span class="mut">الكمية</span><b>' + esc(o.quantity || '—') + '</b></div><div class="rw s"><span class="mut">التاريخ</span><b>' + ltr(o.created_at || '—') + '</b></div>' +
        (o.link ? '<div class="rw s"><span class="mut">الرابط</span><b style="direction:ltr;max-width:60%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + esc(o.link) + '</b></div>' : '') + '</div>';
    });
  }

  function download(path, name) {
    var tok = store.get('token');
    fetch(API + path, { headers: { Authorization: 'Bearer ' + tok } }).then(function (r) {
      if (!r.ok) throw new Error(); var cd = r.headers.get('Content-Disposition') || '';
      var m = /filename\*?=(?:UTF-8'')?"?([^";]+)/i.exec(cd); return r.blob().then(function (b) { return { b: b, n: (m && decodeURIComponent(m[1])) || name }; });
    }).then(function (x) {
      if (window.VeloxApp && window.VeloxApp.save) {
        var rd = new FileReader();
        rd.onload = function () { window.VeloxApp.save(x.n, x.b.type || 'application/octet-stream', String(rd.result).split(',')[1]); };
        rd.readAsDataURL(x.b); return;
      }
      var a = document.createElement('a'); a.href = URL.createObjectURL(x.b); a.download = x.n; document.body.appendChild(a); a.click(); a.remove();
    }).catch(function () { toast('تعذّر التحميل', true); });
  }

  /* ───────── الدخول والتسجيل ───────── */
  function doLogin(btn) {
    var id = val('id'), pw = val('pw');
    if (!/^\d{5,15}$/.test(id) && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(id)) return fail($('#id'), 'اكتب الأيدي (أرقام) أو البريد الإلكتروني');
    if (!pw) return fail($('#pw'), 'اكتب كلمة السر');
    setBusy(btn, true);
    api('/login', 'POST', { id: id, password: pw }).then(function (r) {
      setBusy(btn, false);
      if (r.ok) { store.set('token', r.data.token); location.href = 'home.html'; }
      else if (r.offline) serverDown();
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
      if (r.ok) { store.set('linkCode', r.data.link_code); location.href = 'link-bot.html'; }
      else if (r.offline) serverDown();
      else fail($('#i'), r.data.message || 'تعذّر إنشاء الحساب');
    });
  }
  function doLinkCheck(btn) {
    var code = store.get('linkCode');
    setBusy(btn, true);
    api('/link/check', 'POST', { code: code }).then(function (r) {
      setBusy(btn, false);
      if (r.ok && r.data.linked) { store.set('token', r.data.token); location.href = 'welcome.html'; }
      else if (r.offline) serverDown();
      else if (!r.ok) toast(r.data.message || 'الرمز غير صالح', true);
      else toast('لم يتم التحقق في البوت بعد. أرسل الرمز للبوت واضغط "نعم، تحقق" ثم حاول مرة ثانية.', true);
    });
  }
  function saveServer(btn) {
    var u = val('srv').replace(/\/+$/, '').replace(/\/api$/, '');
    if (!u) return fail($('#srv'), 'اكتب عنوان الخادم');
    if (!/^https?:\/\//i.test(u)) u = 'https://' + u;
    var st = $('#srvState'); setBusy(btn, true); st.textContent = '';
    fetch(u + '/api/stats').then(function (r) { return r.json(); }).then(function (d) {
      setBusy(btn, false);
      if (d && typeof d.total_orders !== 'undefined') { store.set('api', u + '/api'); st.style.color = '#7BE5A4'; st.textContent = 'تم الاتصال بنجاح'; setTimeout(function () { location.href = 'index.html'; }, 700); }
      else throw new Error();
    }).catch(function () { setBusy(btn, false); st.style.color = '#FFA3A3'; st.textContent = 'تعذّر الاتصال. تأكد من العنوان ومن أن server.py يعمل والمنفذ مفتوح.'; });
  }

  function doGoogle() {
    if (!GOOGLE_CLIENT_ID) { toast('الدخول بـ Google غير مفعّل بعد (يحتاج Client ID في js/config.js)', true); return; }
    function go() {
      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: function (resp) {
          api('/google', 'POST', { credential: resp.credential }).then(function (r) {
            if (r.ok) { store.set('token', r.data.token); location.href = 'home.html'; }
            else toast(r.data.message || 'تعذّر الدخول بحساب Google', true);
          });
        }
      });
      window.google.accounts.id.prompt();
    }
    if (window.google && window.google.accounts) return go();
    var s = document.createElement('script'); s.src = 'https://accounts.google.com/gsi/client'; s.async = true; s.onload = go; document.head.appendChild(s);
  }

  document.addEventListener('click', function (e) {
    var sv = e.target.closest('[data-save-server]');
    if (sv) { saveServer(sv); return; }
    var t = e.target.closest('[data-submit],[data-toggle-pw],[data-logout],[data-google],[data-copy-code],[data-track],[data-zip],[data-dl]');
    if (!t) return;
    if (t.matches('[data-submit="login"]')) doLogin(t);
    else if (t.matches('[data-submit="register"]')) doRegister(t);
    else if (t.matches('[data-submit="linkcheck"]')) doLinkCheck(t);
    else if (t.matches('[data-toggle-pw]')) { var i = $('#pw'); if (i) i.type = i.type === 'password' ? 'text' : 'password'; }
    else if (t.matches('[data-logout]')) { store.del('token'); location.href = 'login.html'; }
    else if (t.matches('[data-google]')) doGoogle();
    else if (t.matches('[data-track]')) doTrack();
    else if (t.matches('[data-zip]')) download('/admin/hosting/zip?mode=' + t.dataset.zip, 'velox_hosting.zip');
    else if (t.matches('[data-dl]')) download('/admin/backups/download?name=' + encodeURIComponent(t.dataset.dl), t.dataset.dl);
    else if (t.matches('[data-copy-code]')) {
      var c = store.get('linkCode') || '';
      (navigator.clipboard ? navigator.clipboard.writeText(c) : Promise.reject()).then(function () { toast('تم نسخ الرمز'); }, function () { toast(c); });
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && (page === 'login' || page === 'register')) { var b = $('[data-submit]'); if (b) b.click(); }
    if (e.key === 'Enter' && page === 'track') doTrack();
    if (e.key === 'Enter' && page === 'server') { var sb = $('[data-save-server]'); if (sb) sb.click(); }
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches && e.target.matches('[role="button"]')) { e.preventDefault(); e.target.click(); }
  });

  /* صور احتياطية: WebP ← PNG ← إخفاء */
  function fixImg(img) {
    if (img.dataset.fixed === '2') return;
    var stage = img.dataset.fixed === '1' ? 2 : 1;
    img.dataset.fixed = String(stage);
    if (stage === 1 && /\.webp$/.test(img.src)) img.src = img.src.replace(/\.webp$/, '.png');
    else img.style.visibility = 'hidden';
  }
  document.addEventListener('error', function (e) {
    if (e.target && e.target.tagName === 'IMG' && e.target.classList.contains('em')) fixImg(e.target);
  }, true);
  $$('img.em').forEach(function (img) { if (img.complete && img.naturalWidth === 0) fixImg(img); });

  start();
})();
