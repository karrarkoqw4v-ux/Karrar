/* قاعدة بيانات التطبيق المحلية (داخل الجهاز) — بدون أي خادم.
   نفس واجهة الخادم السابق، لكنها تعمل داخل التطبيق وتحفظ الحسابات والرصيد والطلبات في ذاكرة الهاتف. */
(function () {
  'use strict';
  var KEY = 'vxdb', SESSION = 'vxa_session';
  var PLATFORMS = ['تيك توك', 'انستغرام', 'فيسبوك', 'يوتيوب', 'كواي', 'تليجرام', 'ثريدز', 'واتساب', 'خدمات مميزة', 'تفاعلات تليجرام'];
  var GAMES = ['شدات ببجي موبايل', 'نجوم تليجرام', 'حسابات نتفلكس', 'جواهر فري فاير', 'ارقام تليجرام', 'تمويل اشتراك اجباري', 'تفعيل تليجرام مميز', 'رصيد اسياسيل', 'جواهر لودو', 'رصيد اثير', 'ذهب لودو'];
  var STATUSES = ['قيد الانتظار', 'قيد التنفيذ', 'مكتمل', 'مرفوض'];
  var DEFAULTS = { app_name: 'Velox', currency: 'نقطة', daily_points: '50', registration_open: '1', support_link: '', channel_link: '', contact_text: 'للشحن تواصل مع الدعم.' };
  var ADMIN = { uid: '8010837412', username: 'admin', email: 'admin@velox.app', password: '1005', name: 'المسؤول' };
  var db = null, ready = null;

  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  function now() { var d = new Date(); return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds()); }
  function parse(s) { return new Date(String(s).replace(' ', 'T')); }
  function save() { try { localStorage.setItem(KEY, JSON.stringify(db)); } catch (e) {} }
  function hex(buf) { return Array.prototype.map.call(new Uint8Array(buf), function (b) { return ('0' + b.toString(16)).slice(-2); }).join(''); }
  function rnd(n) { var a = new Uint8Array(n); (window.crypto || window.msCrypto).getRandomValues(a); return hex(a); }

  function hashPw(pw, salt) {
    try {
      var enc = new TextEncoder();
      return crypto.subtle.importKey('raw', enc.encode(pw), 'PBKDF2', false, ['deriveBits']).then(function (k) {
        return crypto.subtle.deriveBits({ name: 'PBKDF2', salt: enc.encode(salt), iterations: 100000, hash: 'SHA-256' }, k, 256);
      }).then(hex);
    } catch (e) {
      var h = 5381, s = salt + pw; for (var i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
      return Promise.resolve('f' + (h >>> 0).toString(16));
    }
  }

  function init() {
    if (ready) return ready;
    ready = new Promise(function (resolve) {
      try { db = JSON.parse(localStorage.getItem(KEY)); } catch (e) { db = null; }
      if (db && db.users) return resolve();
      db = { v: 1, seq: { users: 0, services: 0, orders: 0, ledger: 0 }, users: [], services: [], orders: [], codes: [], code_uses: [], ledger: [], settings: JSON.parse(JSON.stringify(DEFAULTS)) };
      var salt = rnd(8);
      hashPw(ADMIN.password, salt).then(function (h) {
        db.users.push({ id: ++db.seq.users, uid: ADMIN.uid, name: ADMIN.name, username: ADMIN.username, email: ADMIN.email, salt: salt, hash: h, balance: 0, spent: 0, is_admin: 1, banned: 0, last_daily: null, created_at: now() });
        save(); resolve();
      });
    });
    return ready;
  }

  function out(status, data) { return { ok: status >= 200 && status < 300, status: status, data: data }; }
  function S(k) { var v = db.settings[k]; return v == null ? DEFAULTS[k] : v; }
  function toInt(v, d) { var n = parseInt(String(v == null ? '' : v).trim(), 10); return isNaN(n) ? (d || 0) : n; }
  function led(uid, amount, kind, note) { db.ledger.push({ id: ++db.seq.ledger, user_id: uid, amount: amount, kind: kind, note: note || '', created_at: now() }); }
  function pub(u) { return { id: u.id, uid: u.uid, name: u.name, username: u.username, email: u.email || '', balance: u.balance, spent: u.spent, admin: !!u.is_admin, created_at: u.created_at }; }
  function byId(id) { return db.users.filter(function (u) { return u.id === id; })[0]; }
  function me() { var id = parseInt(localStorage.getItem(SESSION), 10); var u = id ? byId(id) : null; return u || null; }
  function calcPrice(s, qty) { return s.kind === 'game' ? s.price : Math.max(1, Math.round(s.price * qty / 1000)); }
  function pubSvc(s) { return { id: s.id, kind: s.kind, category: s.category, name: s.name, price: s.price, min_qty: s.min_qty, max_qty: s.max_qty, quantity: s.quantity, description: s.description }; }
  function pubOrder(o) { return { id: o.id, kind: o.kind, category: o.category, service_name: o.service_name, link: o.link, quantity: o.quantity, price: o.price, status: o.status, note: o.note, created_at: o.created_at, updated_at: o.updated_at }; }

  function dispatch(m, r, q, b) {
    var u, i;
    if (r === 'app/info' && m === 'GET') return Promise.resolve(out(200, { name: S('app_name'), currency: S('currency'), support_link: S('support_link'), channel_link: S('channel_link'), contact_text: S('contact_text'), daily_points: toInt(S('daily_points')), registration_open: S('registration_open') === '1', platforms: PLATFORMS, games: GAMES }));

    if (r === 'register' && m === 'POST') {
      if (S('registration_open') !== '1') return Promise.resolve(out(403, { message: 'التسجيل مغلق حالياً' }));
      var name = String(b.name || '').trim().slice(0, 40), un = String(b.username || '').trim().toLowerCase(), pw = String(b.password || ''), em = String(b.email || '').trim().toLowerCase().slice(0, 120);
      if (name.length < 2) return Promise.resolve(out(400, { message: 'اكتب اسمك' }));
      if (!/^[a-z0-9_.]{3,20}$/.test(un)) return Promise.resolve(out(400, { message: 'اسم المستخدم 3-20 حرفاً إنجليزياً أو أرقام أو _' }));
      if (pw.length < 6) return Promise.resolve(out(400, { message: 'كلمة السر 6 أحرف على الأقل' }));
      if (em && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(em)) return Promise.resolve(out(400, { message: 'البريد الإلكتروني غير صحيح' }));
      if (db.users.some(function (x) { return x.username === un; })) return Promise.resolve(out(409, { message: 'اسم المستخدم مستخدم، اختر غيره' }));
      if (em && db.users.some(function (x) { return (x.email || '').toLowerCase() === em; })) return Promise.resolve(out(409, { message: 'البريد مسجّل بالفعل' }));
      var uid; do { uid = String(10000000 + Math.floor(Math.random() * 89999999)); } while (db.users.some(function (x) { return x.uid === uid; }));
      var salt = rnd(8);
      return hashPw(pw, salt).then(function (h) {
        var nu = { id: ++db.seq.users, uid: uid, name: name, username: un, email: em || null, salt: salt, hash: h, balance: 0, spent: 0, is_admin: 0, banned: 0, last_daily: null, created_at: now() };
        db.users.push(nu); save(); localStorage.setItem(SESSION, String(nu.id));
        return out(200, { ok: true, user: pub(nu) });
      });
    }
    if (r === 'login' && m === 'POST') {
      var lg = String(b.login || '').trim().toLowerCase();
      u = db.users.filter(function (x) { return x.username === lg || (x.email || '').toLowerCase() === lg || x.uid === lg; })[0];
      if (!u) return Promise.resolve(out(401, { message: 'بيانات الدخول غير صحيحة' }));
      return hashPw(String(b.password || ''), u.salt).then(function (h) {
        if (h !== u.hash) return out(401, { message: 'بيانات الدخول غير صحيحة' });
        if (u.banned) return out(403, { message: 'هذا الحساب محظور' });
        localStorage.setItem(SESSION, String(u.id));
        return out(200, { ok: true, user: pub(u) });
      });
    }
    if (r === 'logout') { localStorage.removeItem(SESSION); return Promise.resolve(out(200, { ok: true })); }

    u = me();
    if (!u) return Promise.resolve(out(401, { message: 'غير مسجّل' }));
    if (u.banned) return Promise.resolve(out(403, { message: 'هذا الحساب محظور' }));
    var res = function (s, d) { return Promise.resolve(out(s, d)); };

    if (r.indexOf('admin/') !== 0) {
      if (r === 'me' && m === 'GET') {
        var d = pub(u); d.orders = db.orders.filter(function (o) { return o.user_id === u.id; }).length;
        d.can_daily = !u.last_daily || (Date.now() - parse(u.last_daily).getTime() >= 86400000); d.daily_points = toInt(S('daily_points'));
        return res(200, { user: d });
      }
      if (r === 'me/password' && m === 'POST') {
        return hashPw(String(b.old || ''), u.salt).then(function (h) {
          if (h !== u.hash) return out(400, { message: 'كلمة السر الحالية غير صحيحة' });
          if (String(b['new'] || '').length < 6) return out(400, { message: 'كلمة السر الجديدة 6 أحرف على الأقل' });
          var salt = rnd(8); return hashPw(String(b['new']), salt).then(function (h2) { u.salt = salt; u.hash = h2; save(); return out(200, { ok: true }); });
        });
      }
      if (r === 'catalog' && m === 'GET') {
        var kind = q.kind === 'game' ? 'game' : 'rashq', cnt = {};
        db.services.forEach(function (s) { if (s.kind === kind && s.active) cnt[s.category] = (cnt[s.category] || 0) + 1; });
        var names = kind === 'game' ? GAMES : PLATFORMS, o = {}; names.forEach(function (n) { o[n] = cnt[n] || 0; });
        return res(200, { categories: o });
      }
      if (r === 'services' && m === 'GET') {
        var k2 = q.kind === 'game' ? 'game' : 'rashq';
        return res(200, { services: db.services.filter(function (s) { return s.kind === k2 && s.category === (q.category || '') && s.active; }).map(pubSvc) });
      }
      if (r === 'services/one' && m === 'GET') {
        var sv = db.services.filter(function (s) { return s.id === toInt(q.id) && s.active; })[0];
        return sv ? res(200, { service: pubSvc(sv) }) : res(404, { message: 'الخدمة غير موجودة' });
      }
      if (r === 'orders' && m === 'POST') {
        var svc = db.services.filter(function (s) { return s.id === toInt(b.service_id) && s.active; })[0];
        if (!svc) return res(404, { message: 'الخدمة غير موجودة' });
        var link = String(b.link || '').trim().slice(0, 300);
        if (!link) return res(400, { message: svc.kind === 'game' ? 'اكتب الرابط أو معرف اللاعب' : 'اكتب الرابط' });
        var qty = toInt(b.quantity);
        if (svc.kind === 'rashq') { if (qty < svc.min_qty || (svc.max_qty && qty > svc.max_qty)) return res(400, { message: 'الكمية يجب أن تكون بين ' + svc.min_qty + ' و ' + svc.max_qty }); } else qty = 0;
        var price = calcPrice(svc, qty);
        if (u.balance < price) return res(402, { message: 'رصيدك غير كافٍ. المطلوب ' + price + ' ورصيدك ' + u.balance });
        u.balance -= price; u.spent += price;
        var od = { id: ++db.seq.orders, user_id: u.id, kind: svc.kind, category: svc.category, service_id: svc.id, service_name: svc.name, link: link, quantity: qty, price: price, status: STATUSES[0], note: '', created_at: now(), updated_at: null };
        db.orders.push(od); led(u.id, -price, 'order', 'طلب #' + od.id + ' ' + svc.name); save();
        return res(200, { ok: true, order_id: od.id, balance: u.balance });
      }
      if (r === 'orders' && m === 'GET') return res(200, { orders: db.orders.filter(function (o) { return o.user_id === u.id; }).slice().reverse().slice(0, 200).map(pubOrder) });
      if (r === 'track' && m === 'GET') {
        var oid = toInt(String(q.id || '').replace('#', ''));
        var ot = db.orders.filter(function (o) { return o.id === oid && (o.user_id === u.id || u.is_admin); })[0];
        return ot ? res(200, { order: pubOrder(ot) }) : res(404, { message: 'لم يتم العثور على الطلب' });
      }
      if (r === 'ledger' && m === 'GET') return res(200, { items: db.ledger.filter(function (x) { return x.user_id === u.id; }).slice().reverse().slice(0, 100) });
      if (r === 'redeem' && m === 'POST') {
        var code = String(b.code || '').trim().toUpperCase(), c = db.codes.filter(function (x) { return x.code === code; })[0];
        if (!c) return res(404, { message: 'الكود غير صحيح' });
        if (c.max_uses && c.used_count >= c.max_uses) return res(400, { message: 'انتهى عدد استخدامات الكود' });
        if (db.code_uses.some(function (x) { return x.user_id === u.id && x.code === code; })) return res(400, { message: 'استخدمت هذا الكود من قبل' });
        db.code_uses.push({ user_id: u.id, code: code }); c.used_count++; u.balance += c.points; led(u.id, c.points, 'code', 'كود ' + code); save();
        return res(200, { ok: true, points: c.points, balance: u.balance });
      }
      if (r === 'daily' && m === 'POST') {
        var pts = toInt(S('daily_points'));
        if (u.last_daily) { var left = 86400000 - (Date.now() - parse(u.last_daily).getTime()); if (left > 0) return res(400, { message: 'الهدية متاحة بعد ' + Math.floor(left / 3600000) + ' ساعة و ' + Math.floor(left % 3600000 / 60000) + ' دقيقة' }); }
        if (pts <= 0) return res(400, { message: 'الهدية اليومية غير مفعّلة' });
        u.balance += pts; u.last_daily = now(); led(u.id, pts, 'daily', 'الهدية اليومية'); save();
        return res(200, { ok: true, points: pts, balance: u.balance });
      }
      if (r === 'transfer' && m === 'POST') {
        var to = String(b.to || '').trim().toLowerCase().replace(/^@/, ''), amt = toInt(b.amount);
        if (amt <= 0) return res(400, { message: 'اكتب عدد النقاط' });
        var t = db.users.filter(function (x) { return x.username === to || x.uid === to; })[0];
        if (!t || t.banned) return res(404, { message: 'المستخدم غير موجود' });
        if (t.id === u.id) return res(400, { message: 'لا يمكنك التحويل لنفسك' });
        if (u.balance < amt) return res(402, { message: 'رصيدك غير كافٍ' });
        u.balance -= amt; t.balance += amt; led(u.id, -amt, 'transfer', 'تحويل إلى @' + t.username); led(t.id, amt, 'transfer', 'تحويل من @' + u.username); save();
        return res(200, { ok: true, balance: u.balance });
      }
      return res(404, { message: 'غير موجود' });
    }

    if (!u.is_admin) return res(403, { message: 'صلاحية المسؤول مطلوبة' });
    var a = r.slice(6);
    if (a === 'stats' && m === 'GET') {
      var sum = function (f) { return db.users.reduce(function (s, x) { return s + x[f]; }, 0); };
      return res(200, { users: db.users.length, orders: db.orders.length, pending: db.orders.filter(function (o) { return o.status === STATUSES[0]; }).length, completed: db.orders.filter(function (o) { return o.status === 'مكتمل'; }).length, services: db.services.length, balance_total: sum('balance'), spent_total: sum('spent') });
    }
    if (a === 'users' && m === 'GET') {
      var s = String(q.q || '').trim().toLowerCase();
      var list = db.users.filter(function (x) { return !s || x.name.toLowerCase().indexOf(s) >= 0 || x.username.indexOf(s) >= 0 || x.uid.indexOf(s) >= 0 || (x.email || '').toLowerCase().indexOf(s) >= 0; }).slice().reverse().slice(0, 100);
      return res(200, { users: list.map(function (x) { var p = pub(x); p.banned = !!x.banned; return p; }), total: db.users.length });
    }
    if (a === 'user/balance' && m === 'POST') {
      var tu = byId(toInt(b.id)); if (!tu) return res(404, { message: 'المستخدم غير موجود' });
      var am = Math.abs(toInt(b.amount)), delta;
      if (b.mode === 'add') delta = am; else if (b.mode === 'deduct') delta = -Math.min(am, tu.balance); else if (b.mode === 'reset') delta = -tu.balance; else return res(400, { message: 'عملية غير معروفة' });
      tu.balance += delta; led(tu.id, delta, 'admin', 'من المسؤول'); save(); return res(200, { ok: true, balance: tu.balance });
    }
    if (a === 'user/ban' && m === 'POST') {
      var bu = byId(toInt(b.id)); if (!bu || bu.is_admin) return res(400, { message: 'لا يمكن تنفيذ العملية' });
      bu.banned = b.banned ? 1 : 0; save(); return res(200, { ok: true });
    }
    if (a === 'orders' && m === 'GET') {
      var st = q.status || '';
      var os = db.orders.filter(function (o) { return !st || o.status === st; }).slice().reverse().slice(0, 200).map(function (o) { var x = pubOrder(o); var ow = byId(o.user_id) || {}; x.username = ow.username; x.user_name = ow.name; return x; });
      return res(200, { orders: os, statuses: STATUSES });
    }
    if (a === 'order/status' && m === 'POST') {
      var o2 = db.orders.filter(function (o) { return o.id === toInt(b.id); })[0], ns = b.status;
      if (!o2 || STATUSES.indexOf(ns) < 0) return res(400, { message: 'بيانات غير صحيحة' });
      if (ns === 'مرفوض' && o2.status !== 'مرفوض') { var ow2 = byId(o2.user_id); if (ow2) { ow2.balance += o2.price; ow2.spent = Math.max(0, ow2.spent - o2.price); led(ow2.id, o2.price, 'refund', 'استرداد طلب #' + o2.id); } }
      else if (o2.status === 'مرفوض' && ns !== 'مرفوض') return res(400, { message: 'الطلب المرفوض تم استرداد نقاطه، لا يمكن إعادة فتحه' });
      o2.status = ns; o2.note = String(b.note || '').slice(0, 200); o2.updated_at = now(); save(); return res(200, { ok: true });
    }
    if (a === 'services' && m === 'GET') {
      var k3 = q.kind === 'game' ? 'game' : 'rashq';
      return res(200, { services: db.services.filter(function (s) { return s.kind === k3 && s.category === (q.category || ''); }), categories: k3 === 'game' ? GAMES : PLATFORMS });
    }
    if (a === 'service/save' && m === 'POST') {
      var k4 = b.kind === 'game' ? 'game' : 'rashq', cat = String(b.category || '');
      if ((k4 === 'game' ? GAMES : PLATFORMS).indexOf(cat) < 0) return res(400, { message: 'القسم غير موجود' });
      var nm = String(b.name || '').trim().slice(0, 120); if (!nm) return res(400, { message: 'اكتب اسم الخدمة' });
      var vals = { name: nm, price: toInt(b.price), min_qty: toInt(b.min_qty), max_qty: toInt(b.max_qty), quantity: String(b.quantity || '').slice(0, 60), description: String(b.description || '').slice(0, 300), active: (b.active === 0 || b.active === false) ? 0 : 1 };
      if (b.id) { var ex = db.services.filter(function (s) { return s.id === toInt(b.id); })[0]; if (ex) for (var key in vals) ex[key] = vals[key]; }
      else { vals.id = ++db.seq.services; vals.kind = k4; vals.category = cat; vals.created_at = now(); db.services.push(vals); }
      save(); return res(200, { ok: true });
    }
    if (a === 'service/delete' && m === 'POST') { db.services = db.services.filter(function (s) { return s.id !== toInt(b.id); }); save(); return res(200, { ok: true }); }
    if (a === 'codes' && m === 'GET') return res(200, { codes: db.codes.slice().reverse() });
    if (a === 'codes/add' && m === 'POST') {
      var cd = String(b.code || '').trim().toUpperCase() || ('VX' + rnd(3).toUpperCase());
      if (!/^[A-Z0-9_-]{3,30}$/.test(cd)) return res(400, { message: 'الكود حروف إنجليزية وأرقام فقط' });
      if (toInt(b.points) <= 0) return res(400, { message: 'اكتب عدد النقاط' });
      if (db.codes.some(function (x) { return x.code === cd; })) return res(409, { message: 'الكود موجود مسبقاً' });
      db.codes.push({ code: cd, points: toInt(b.points), max_uses: toInt(b.max_uses), used_count: 0, created_at: now() }); save(); return res(200, { ok: true, code: cd });
    }
    if (a === 'codes/delete' && m === 'POST') { var dc = String(b.code || '').toUpperCase(); db.codes = db.codes.filter(function (x) { return x.code !== dc; }); save(); return res(200, { ok: true }); }
    if (a === 'settings' && m === 'GET') { var so = {}; Object.keys(DEFAULTS).forEach(function (k) { so[k] = S(k); }); return res(200, { settings: so }); }
    if (a === 'settings/save' && m === 'POST') { var nsx = b.settings || {}; Object.keys(nsx).forEach(function (k) { if (k in DEFAULTS) db.settings[k] = String(nsx[k]).slice(0, 500); }); save(); return res(200, { ok: true }); }
    if (a === 'storage' && m === 'GET') {
      var raw = localStorage.getItem(KEY) || '';
      return res(200, { storage: { users: db.users.length, orders: db.orders.length, services: db.services.length, codes: db.codes.length, ledger: db.ledger.length, size: raw.length, ua: navigator.userAgent.replace(/\s*VeloxApp.*/, '') } });
    }
    return res(404, { message: 'غير موجود' });
  }

  function request(method, path, body) {
    return init().then(function () {
      var qi = path.indexOf('?'), p = (qi < 0 ? path : path.slice(0, qi)).replace(/^\//, '').replace(/\/$/, ''), q = {};
      if (qi >= 0) new URLSearchParams(path.slice(qi + 1)).forEach(function (v, k) { q[k] = v; });
      return dispatch(method, p, q, body || {});
    });
  }

  window.VeloxLocal = {
    request: request,
    backupJson: function () { return init().then(function () { return JSON.stringify({ app: 'velox', exported_at: now(), data: db }, null, 1); }); },
    restoreJson: function (text) {
      return init().then(function () {
        var j = JSON.parse(text); if (!j || j.app !== 'velox' || !j.data || !j.data.users) throw new Error('bad');
        db = j.data; save(); return true;
      });
    }
  };
  init();
})();
