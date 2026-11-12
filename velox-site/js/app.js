/* Velox site — الواجهة. كل البيانات حقيقية من server/server.py (نفس قاعدة بيانات البوت). */
(function () {
  'use strict';
  var API = (window.VELOX_API || '/api').replace(/\/$/, '');
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
        return r.json().catch(function () { return {}; }).then(function (d) { return { ok: r.ok, status: r.status, data: d }; });
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
  }

  /* ───────── بدء الصفحة ───────── */
  function start() {
    if (isPublic) {
      if (page === 'link-bot') setBind('linkCode', store.get('linkCode') || 'VX-00000');
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
    },

    /* المسؤول */
    admin: function () {
      api('/admin/stats').then(function (r) { Object.keys(r.data).forEach(function (k) { setBind('a_' + k, num(r.data[k])); }); });
    },
    'admin-users': function () {
      var t;
      function load() {
        api('/admin/users?q=' + encodeURIComponent(val('q'))).then(function (r) {
          var rows = r.data.users || [];
          setBind('total', num(r.data.total));
          $('#list').innerHTML = rows.map(function (u) {
            return '<div class="tr"><div class="fl"><b>' + esc(u.name) + '</b><div class="xs mut"><bdi dir="ltr">' + esc(u.uid) + '</bdi>' + (u.username ? ' · <bdi dir="ltr">' + esc(u.username) + '</bdi>' : '') + '</div></div>' +
              '<div style="text-align:end"><b>' + num(u.points) + '</b><div><span class="pill ' + (u.web ? 'g' : '') + '">' + (u.web ? 'بوت + موقع' : 'بوت') + '</span></div></div></div>';
          }).join('');
          $('#list').hidden = !rows.length; showEmpty(rows.length);
        });
      }
      $('#q').addEventListener('input', function () { clearTimeout(t); t = setTimeout(load, 300); });
      load();
    },
    'admin-recharge': function () {
      api('/admin/stats').then(function (r) { setBind('a_recharges', num(r.data.recharges)); setBind('a_recharge_points', num(r.data.recharge_points)); });
      api('/admin/recharges').then(function (r) {
        var rows = r.data.recharges || [];
        $('#list').innerHTML = rows.map(function (x) {
          return '<div class="tr"><div class="fl"><b dir="ltr" style="display:block;text-align:start">' + esc(x.user_id) + '</b><div class="xs mut">' + ltr(x.created_at || '') + (x.note ? ' · ' + esc(x.note) : '') + '</div></div><span class="pill">' + esc(x.method || '') + '</span><b style="color:#7BE5A4"><bdi dir="ltr">+' + num(x.points) + '</bdi></b></div>';
        }).join('');
        $('#list').hidden = !rows.length; showEmpty(rows.length);
      });
    },
    'admin-games': function () { adminServices('/services/games', 'category', false); },
    'admin-rashq': function () { adminServices('/services/rashq', 'platform', true); },
    'admin-backups': function () {
      api('/admin/backups').then(function (r) {
        var rows = r.data.backups || [];
        $('#list').innerHTML = rows.map(function (b) {
          return '<div class="item"><span class="eb g">' + emoji('e_1f5c2') + '</span><div class="fl"><b style="direction:ltr;display:block;text-align:start;font-size:13px">' + esc(b.name) + '</b><div class="xs mut">' + (b.size / 1048576).toFixed(2) + ' MB · ' + esc(b.mtime) + '</div></div><a class="back px" role="button" tabindex="0" data-dl="' + esc(b.name) + '" style="width:40px;height:40px;flex-basis:40px;border-radius:13px"><i class="ic i-down" style="width:18px;height:18px"></i></a></div>';
        }).join('');
        showEmpty(rows.length);
      });
    },
    'admin-hosting': function () {
      api('/admin/hosting').then(function (r) {
        ['all', 'main'].forEach(function (m) {
          setBind('h_' + m + '_files', num(r.data[m] && r.data[m].files));
          setBind('h_' + m + '_size', ((r.data[m] ? r.data[m].size : 0) / 1048576).toFixed(1) + ' MB');
        });
      });
    },
    'admin-settings': function () {
      api('/admin/config').then(function (r) {
        var c = r.data.config || {}, keys = Object.keys(c);
        $('#list').innerHTML = keys.map(function (k) {
          return '<div class="tr"><div class="fl"><b style="direction:ltr;display:block;text-align:start;font-size:13px">' + esc(k) + '</b></div><span class="mut xs" style="max-width:55%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + esc(c[k]) + '</span></div>';
        }).join('');
        $('#list').hidden = !keys.length; showEmpty(keys.length);
      });
    },
    'admin-db': function () {
      api('/admin/db').then(function (r) {
        setBind('d_path', r.data.path || '—'); setBind('d_size', ((r.data.size || 0) / 1048576).toFixed(2) + ' MB');
        $('#list').innerHTML = (r.data.tables || []).map(function (t) {
          return '<div class="tr"><div class="fl"><b style="direction:ltr;display:block;text-align:start">' + esc(t.name) + '</b></div><span class="pill g">' + num(t.rows) + '</span></div>';
        }).join('');
      });
    }
  };

  function adminServices(path, param, isRashq) {
    var data = null;
    api(path).then(function (r) {
      data = r.data.platforms || r.data.categories || {};
      var names = Object.keys(data);
      $('#tabs').innerHTML = names.map(function (n, i) { return '<a data-n="' + esc(n) + '"' + (i ? '' : ' class="on"') + '>' + esc(n) + ' (' + data[n] + ')</a>'; }).join('');
      $$('#tabs a').forEach(function (a) { a.addEventListener('click', function () { $$('#tabs a').forEach(function (x) { x.classList.remove('on'); }); a.classList.add('on'); draw(a.dataset.n); }); });
      if (names.length) draw(names[0]); else showEmpty(0);
    });
    function draw(n) {
      api(path + '?' + param + '=' + encodeURIComponent(n)).then(function (r) {
        var rows = r.data.services || [];
        $('#list').innerHTML = rows.map(function (s) {
          return '<div class="tr"><div class="fl"><b dir="auto">' + esc(s.name) + '</b><div class="xs mut">' + (isRashq ? 'من ' + num(s.min) + ' إلى ' + num(s.max) : esc(s.quantity || '')) + '</div></div><span class="pill y">💎 ' + num(s.price) + '</span></div>';
        }).join('');
        $('#list').hidden = !rows.length; showEmpty(rows.length);
      });
    }
  }

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
      var a = document.createElement('a'); a.href = URL.createObjectURL(x.b); a.download = x.n; document.body.appendChild(a); a.click(); a.remove();
    }).catch(function () { toast('تعذّر التحميل', true); });
  }

  /* ───────── الدخول والتسجيل ───────── */
  function doLogin(btn) {
    var id = val('id'), pw = val('pw');
    if (!/^\d{5,15}$/.test(id)) return fail($('#id'), 'اكتب الأيدي (أرقام فقط)');
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
