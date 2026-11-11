/* Encryption: private chats (files / apps / websites) with quick local tools. Everything runs on the device (AES-256-GCM + PBKDF2). */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const TYPES={
 file:{ic:'file',c:'#f59e0b',a:'تشفير الملفات',e:'File encryption',da:'شفّر أي ملف بكلمة مرور وفكّ تشفيره',de:'Encrypt any file with a password and decrypt it'},
 app:{ic:'code-xml',c:'#8b5cf6',a:'حماية التطبيقات',e:'App protection',da:'نسخ مشفّرة وبصمات وفحص لتطبيقاتك (APK)',de:'Encrypted copies, fingerprints and checks for your apps (APK)'},
 site:{ic:'globe',c:'#0ea5e9',a:'حماية المواقع',e:'Website protection',da:'قفل صفحات HTML بكلمة مرور وأدوات حماية',de:'Password-lock HTML pages and protection tools'}};
const TOOLS={
 encf:['lock',T('تشفير ملف','Encrypt a file')],decf:['key',T('فك تشفير','Decrypt')],hash:['shield-check',T('بصمة ملف','File hash')],
 hasht:['file-text',T('تجزئة نص','Hash text')],b64:['link',T('Base64','Base64')],pass:['sparkles',T('كلمة مرور قوية','Strong password')],
 scan:['circle-check',T('فحص الأمان','Security scan')],tips:['info',T('نصائح الحماية','Protection tips')],
 lock:['lock',T('قفل صفحة HTML','Lock an HTML page')],csp:['shield-check',T('ترويسة CSP','CSP header')],obf:['code-xml',T('تمويه JavaScript','Obfuscate JS')],sri:['link',T('بصمة SRI','SRI hash')]};
const SET={file:['encf','decf','hash','hasht','b64','pass'],app:['encf','decf','hash','scan','tips','pass'],site:['lock','encf','hasht','csp','obf','sri','pass']};
const u8b64=u=>{let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));return btoa(s)};
const b64u8=b=>{const s=atob(b),u=new Uint8Array(s.length);for(let i=0;i<s.length;i++)u[i]=s.charCodeAt(i);return u};
const hex=u=>[...new Uint8Array(u)].map(x=>x.toString(16).padStart(2,'0')).join('');
async function key(pw,salt){const km=await crypto.subtle.importKey('raw',new TextEncoder().encode(pw),'PBKDF2',false,['deriveKey']);return crypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:200000,hash:'SHA-256'},km,{name:'AES-GCM',length:256},false,['encrypt','decrypt'])}
async function encrypt(u8,pw,name){
 const salt=crypto.getRandomValues(new Uint8Array(16)),iv=crypto.getRandomValues(new Uint8Array(12));
 const head=new TextEncoder().encode(JSON.stringify({n:name,t:Date.now()})),plain=new Uint8Array(4+head.length+u8.length);
 new DataView(plain.buffer).setUint32(0,head.length);plain.set(head,4);plain.set(u8,4+head.length);
 const ct=new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv},await key(pw,salt),plain));
 const out=new Uint8Array(5+16+12+ct.length);out.set(new TextEncoder().encode('BWSL1'));out.set(salt,5);out.set(iv,21);out.set(ct,33);return out}
async function decrypt(u8,pw){
 if(new TextDecoder().decode(u8.subarray(0,5))!=='BWSL1')throw new Error('format');
 const pl=new Uint8Array(await crypto.subtle.decrypt({name:'AES-GCM',iv:u8.subarray(21,33)},await key(pw,u8.subarray(5,21)),u8.subarray(33)));
 const hl=new DataView(pl.buffer,pl.byteOffset).getUint32(0),h=JSON.parse(new TextDecoder().decode(pl.subarray(4,4+hl)));return {name:h.n,data:pl.subarray(4+hl)}}
const digest=async(alg,u)=>hex(await crypto.subtle.digest(alg,u));
const fmt=n=>n<1024?n+' B':n<1048576?(n/1024).toFixed(1)+' KB':(n/1048576).toFixed(1)+' MB';
function post(userText,md){
 const S=K.state,conv=S.cur;const m={role:'user',c:userText};conv.msgs.push(m);K.chat.addUser(m);
 const ai={role:'assistant',c:md,t:'',s:0,src:[],v:0};conv.msgs.push(ai);
 const v=new K.chat.AIView(ai,{last:true,live:false});document.getElementById('msgs').appendChild(v.el);
 document.getElementById('empty').classList.add('hidden');conv.ts=Date.now();K.db.save();K.chat.scrollEnd(true)}
const ask=(title,secret,multi)=>K.ui.prompt({title,secret:!!secret,multi:!!multi});
async function getPw(confirm){
 const p=await ask(T('كلمة المرور','Password'),true);if(!p)return null;
 if(confirm){const p2=await ask(T('أعد كتابة كلمة المرور','Repeat the password'),true);if(p2!==p){K.toast(T('كلمتا المرور غير متطابقتين','Passwords do not match'));return null}}
 if(p.length<6){K.toast(T('استخدم 6 أحرف على الأقل','Use at least 6 characters'));return null}return p}
const saved=r=>!(r&&String(r).indexOf('err')===0);
const RUN={
 async encf(){
  const f=await K.native.pickFile();if(!f||!f.name)return;if(f.tooBig||!f.b64){K.toast(T('الملف أكبر من 6 ميغابايت','File is larger than 6 MB'));return}
  const pw=await getPw(true);if(!pw)return;K.toast(T('جارٍ التشفير…','Encrypting…'));
  const u=b64u8(f.b64),out=await encrypt(u,pw,f.name),name=f.name+'.enc';
  const r=await K.native.saveFile(name,'application/octet-stream',u8b64(out));
  post('🔐 '+T('تشفير: ','Encrypt: ')+f.name,(saved(r)?'✅ **'+T('تم التشفير','Encrypted')+'**\n\n':'⚠ **'+T('تعذّر الحفظ','Could not save')+'**\n\n')
   +T('- الملف: `'+name+'` ('+fmt(out.length)+')\n- الخوارزمية: AES-256-GCM مع PBKDF2 (200 ألف دورة)\n- البصمة: `'+(await digest('SHA-256',out)).slice(0,32)+'…`\n- المكان: التنزيلات/Bawsala\n\n> ⚠️ احفظ كلمة المرور في مكان آمن، لا يمكن استرجاع الملف بدونها.',
    '- File: `'+name+'` ('+fmt(out.length)+')\n- Algorithm: AES-256-GCM with PBKDF2 (200k rounds)\n- Fingerprint: `'+(await digest('SHA-256',out)).slice(0,32)+'…`\n- Location: Downloads/Bawsala\n\n> ⚠️ Keep the password somewhere safe — the file cannot be recovered without it.'))},
 async decf(){
  const f=await K.native.pickFile();if(!f||!f.name||!f.b64)return;
  const pw=await ask(T('كلمة المرور','Password'),true);if(!pw)return;
  try{const r=await decrypt(b64u8(f.b64),pw);const s=await K.native.saveFile(r.name,'application/octet-stream',u8b64(r.data));
   post('🔓 '+T('فك تشفير: ','Decrypt: ')+f.name,(saved(s)?'✅ **'+T('تم فك التشفير','Decrypted')+'**\n\n':'⚠ **'+T('تعذّر الحفظ','Could not save')+'**\n\n')+T('- الملف الأصلي: `'+r.name+'` ('+fmt(r.data.length)+')\n- المكان: التنزيلات/Bawsala','- Original file: `'+r.name+'` ('+fmt(r.data.length)+')\n- Location: Downloads/Bawsala'))}
  catch(e){post('🔓 '+T('فك تشفير: ','Decrypt: ')+f.name,'❌ **'+T('فشل فك التشفير','Decryption failed')+'**\n\n'+T('كلمة المرور خاطئة أو أن الملف ليس مشفّراً بواسطة بوصلة أو تالف.','Wrong password, or the file was not encrypted by Bawsala, or it is damaged.'))}},
 async hash(){
  const f=await K.native.pickFile();if(!f||!f.name||!f.b64)return;const u=b64u8(f.b64);
  post('🧮 '+T('بصمة: ','Hash: ')+f.name,'**'+f.name+'** · '+fmt(u.length)+'\n\n- SHA-256: `'+await digest('SHA-256',u)+'`\n- SHA-1: `'+await digest('SHA-1',u)+'`\n\n'+T('> 💡 قارن البصمة مع التي يعلنها المصدر للتأكد أن الملف لم يتغيّر.','> 💡 Compare the hash with the one the source publishes to be sure the file was not altered.'))},
 async hasht(){
  const t=await ask(T('النص','Text'),false,true);if(!t)return;const u=new TextEncoder().encode(t);
  post('🧮 '+T('تجزئة نص','Hash text'),'- SHA-256: `'+await digest('SHA-256',u)+'`\n- SHA-512: `'+(await digest('SHA-512',u)).slice(0,64)+'…`\n- SHA-1: `'+await digest('SHA-1',u)+'`')},
 async b64(){
  const t=await ask(T('نص للترميز أو فك الترميز','Text to encode / decode'),false,true);if(!t)return;
  const enc=u8b64(new TextEncoder().encode(t));let dec='';try{dec=new TextDecoder('utf-8',{fatal:true}).decode(b64u8(t.trim()))}catch(e){}
  post('🔤 Base64','- '+T('الترميز','Encoded')+': `'+enc+'`'+(dec?'\n- '+T('فك الترميز','Decoded')+': `'+dec.replace(/`/g,"'")+'`':'')+'\n\n'+T('> ℹ️ Base64 ترميز وليس تشفيراً؛ لا يحمي البيانات.','> ℹ️ Base64 is encoding, not encryption — it does not protect data.'))},
 async pass(){
  const A='ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*?',r=crypto.getRandomValues(new Uint32Array(20));let p='';r.forEach(x=>p+=A[x%A.length]);
  K.native.copy(p);post('🔑 '+T('كلمة مرور قوية','Strong password'),'`'+p+'`\n\n'+T('- 20 خانة عشوائية، نُسخت إلى الحافظة.\n- لا تعيد استخدامها في موقع آخر.','- 20 random characters, copied to the clipboard.\n- Do not reuse it on another site.'))},
 async scan(){K.scan.open();setTimeout(()=>K.scan.pickAndRun(),350)},
 async tips(){post('🛡 '+T('نصائح حماية التطبيق','App protection tips'),T('1. **التوقيع**: وقّع التطبيق بمفتاح قوي (v2/v3) واحفظ المفتاح خارج المستودع.\n2. **التمويه**: فعّل R8/ProGuard لتصغير الكود وإخفاء الأسماء.\n3. **تشفير النصوص الحساسة**: لا تضع مفاتيح API داخل التطبيق؛ استخدم خادماً وسيطاً.\n4. **تثبيت الشهادة** (Certificate Pinning) لاتصالاتك الحسّاسة.\n5. **كشف الروت والمحاكي** للتطبيقات المالية.\n6. **فحص التبعيات** دورياً بحثاً عن ثغرات.\n7. **النسخ الاحتياطي**: احتفظ بنسخة مشفّرة من ملف APK والمفتاح (أداة «تشفير ملف»).','1. **Signing**: sign with a strong key (v2/v3) and keep it out of the repo.\n2. **Obfuscation**: enable R8/ProGuard.\n3. **Secrets**: never ship API keys inside the app; use a proxy server.\n4. **Certificate pinning** for sensitive traffic.\n5. **Root/emulator detection** for financial apps.\n6. **Audit dependencies** regularly.\n7. **Backups**: keep an encrypted copy of the APK and key (“Encrypt a file” tool).'))},
 async lock(){
  const f=await K.native.pickFile();if(!f||!f.name||!f.b64)return;const pw=await getPw(true);if(!pw)return;
  const u=b64u8(f.b64),salt=crypto.getRandomValues(new Uint8Array(16)),iv=crypto.getRandomValues(new Uint8Array(12));
  const ct=new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv},await key(pw,salt),u));
  const html='<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Protected</title><style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0f0f10;color:#fff;font-family:system-ui,sans-serif}form{display:flex;flex-direction:column;gap:12px;width:min(320px,86vw);text-align:center}input,button{height:50px;border-radius:25px;border:0;font-size:1em;padding:0 18px}input{background:#2a2a2c;color:#fff;text-align:center}button{background:#5b78e6;color:#fff;font-weight:700}</style></head><body><form id="f"><h3>\\u{1F512} Protected page</h3><input id="p" type="password" placeholder="Password" autofocus><button>Unlock</button></form><script>const D={s:"'+u8b64(salt)+'",i:"'+u8b64(iv)+'",c:"'+u8b64(ct)+'"};const B=x=>Uint8Array.from(atob(x),c=>c.charCodeAt(0));f.onsubmit=async e=>{e.preventDefault();try{const k=await crypto.subtle.importKey("raw",new TextEncoder().encode(p.value),"PBKDF2",false,["deriveKey"]);const K2=await crypto.subtle.deriveKey({name:"PBKDF2",salt:B(D.s),iterations:200000,hash:"SHA-256"},k,{name:"AES-GCM",length:256},false,["decrypt"]);const t=await crypto.subtle.decrypt({name:"AES-GCM",iv:B(D.i)},K2,B(D.c));document.open();document.write(new TextDecoder().decode(t));document.close()}catch(x){alert("Wrong password")}}<\\/script></body></html>';
  const name=f.name.replace(/\.[^.]+$/,'')+'.locked.html',r=await K.native.saveFile(name,'text/html',utf8b64(html));
  post('🔒 '+T('قفل صفحة: ','Lock page: ')+f.name,(saved(r)?'✅ **'+T('تم إنشاء الصفحة المقفلة','Locked page created')+'**':'⚠ **'+T('تعذّر الحفظ','Could not save')+'**')+'\n\n'+T('- الملف: `'+name+'`\n- لا يُعرض المحتوى إلا بعد إدخال كلمة المرور (فك التشفير داخل المتصفح).\n\n> ⚠️ هذا يحمي المحتوى من القراءة، لكنه ليس بديلاً عن مصادقة حقيقية على الخادم.','- File: `'+name+'`\n- The content shows only after the password is entered (decrypted in the browser).\n\n> ⚠️ This protects the content from being read, but it is not a substitute for real server-side authentication.'))},
 async csp(){post('🛡 CSP',T('ترويسة سياسة أمان المحتوى مقترحة لموقع ثابت:','A suggested Content-Security-Policy for a static site:')+'\n\n```http\nContent-Security-Policy: default-src \'self\'; script-src \'self\'; style-src \'self\' \'unsafe-inline\'; img-src \'self\' data:; object-src \'none\'; base-uri \'self\'; frame-ancestors \'none\'; form-action \'self\'\nStrict-Transport-Security: max-age=31536000; includeSubDomains\nX-Content-Type-Options: nosniff\nReferrer-Policy: strict-origin-when-cross-origin\nPermissions-Policy: camera=(), microphone=(), geolocation=()\n```\n'+T('> 💡 عدّلها حسب مصادر موقعك (خطوط، تحليلات…) ثم افحص موقعك من «الأمان والفحص».','> 💡 Adjust it to your site’s sources (fonts, analytics…) then scan your site from “Security & Scan”.'))},
 async obf(){
  const t=await ask(T('كود JavaScript','JavaScript code'),false,true);if(!t)return;const o='eval(decodeURIComponent(escape(atob("'+u8b64(new TextEncoder().encode(t))+'"))))';
  post('🧩 '+T('تمويه JavaScript','Obfuscate JS'),'```javascript\n'+o+'\n```\n'+T('> ⚠️ هذا تمويه بسيط يعيق القراءة العابرة فقط ولا يحمي الكود حقاً؛ لا تضع أسراراً داخل جافاسكربت الموقع.','> ⚠️ This is light obfuscation that only hinders casual reading — it does not truly protect code. Never put secrets in front-end JavaScript.'))},
 async sri(){
  const t=await ask(T('الصق محتوى السكربت أو الـCSS','Paste the script / CSS content'),false,true);if(!t)return;
  const d=await crypto.subtle.digest('SHA-384',new TextEncoder().encode(t));
  post('🔗 SRI','```html\n<script src="YOUR_URL" integrity="sha384-'+u8b64(new Uint8Array(d))+'" crossorigin="anonymous"></script>\n```\n'+T('> 💡 تمنع هذه البصمة تحميل الملف إن تغيّر على الخادم الخارجي.','> 💡 This hash blocks the file if it is altered on the external server.'))}
};
function utf8b64(s){return u8b64(new TextEncoder().encode(s))}
const PERSONA={file:'Role: file-encryption assistant. Explain encryption (AES-256-GCM, passwords, key storage, backups) and help the user protect files. The app has local tools for this (encrypt/decrypt files, hashes, Base64, password generator). Never ask the user to paste real passwords or keys in chat.',
 app:'Role: mobile app security assistant. Advise on signing, obfuscation, secrets, certificate pinning and APK protection. The app has local tools (encrypted backup, hash, scan). Never ask for real keys or passwords.',
 site:'Role: website protection assistant. Advise on HTTPS, security headers, CSP, SRI, password-locking pages and safe deployment. The app has local tools (lock an HTML page, CSP, SRI, hashes). Never ask for real passwords.'};
K.enc={
 TYPES,persona(t){return PERSONA[t]||''},
 chips(type){return '<span class="etl">'+(SET[type]||[]).map(k=>'<button class="mchip tool" data-enc="'+k+'">'+K.icon(TOOLS[k][0],15)+'<span>'+TOOLS[k][1]+'</span></button>').join('')+'</span>'},
 run(k){if(RUN[k]){K.native.haptic();RUN[k]().catch(e=>K.toast(T('تعذّر التنفيذ','Failed')))}},
 open(){
  if($('enc'))$('enc').remove();
  const d=document.createElement('div');d.id='enc';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);K.enc.paint()},
 paint(){
  const d=$('enc');if(!d)return;const list=K.db.convs.filter(c=>c.enc).sort((a,b)=>b.ts-a.ts);
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="enBack">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('التشفير','Encryption')+'</div><span class="sc-ib ph"></span></div><div class="sc-body">'
   +'<div class="sc-hero"><div class="sc-shield"><img class="sc-em big" src="emoji/1f510.svg" alt=""><i class="r r1"></i><i class="r r2"></i></div><b>'+T('ماذا تريد أن تشفّر؟','What do you want to protect?')+'</b><span>'+T('اختر النوع وسندخلك محادثة خاصة بالتشفير، تُحفظ هناك فقط ومعها أدوات سريعة','Pick a type and you enter a private encryption chat, saved only there, with quick tools')+'</span></div>'
   +Object.keys(TYPES).map((k,i)=>'<button class="sc-act" data-k="'+k+'" style="--c:'+TYPES[k].c+';--i:'+i+'"><span class="ti">'+K.icon(TYPES[k].ic,24)+'</span><div><b>'+T(TYPES[k].a,TYPES[k].e)+'</b><small>'+T(TYPES[k].da,TYPES[k].de)+'</small></div>'+K.icon('chevron-left',20)+'</button>').join('')
   +'<div class="sc-sect">'+T('المحادثات الخاصة','Private chats')+'</div>'
   +(list.length?list.map((c,i)=>'<div class="sc-h" data-i="'+i+'"><span class="dot" style="background:'+TYPES[c.enc].c+'"></span><button class="op" style="flex:1;text-align:start;display:flex;flex-direction:column"><b>'+esc(c.title)+'</b><small>'+c.msgs.length+' '+T('رسالة','messages')+'</small></button><button class="rm" style="color:var(--text3);padding:6px">'+K.icon('trash-2',18)+'</button></div>').join(''):'<div class="sc-empty"><img class="sc-em" src="emoji/1f512.svg" alt=""><span>'+T('لا توجد محادثات خاصة بعد','No private chats yet')+'</span></div>')
   +'<p class="sc-note">'+T('التشفير يتم داخل جهازك (AES-256-GCM). لا نحفظ كلمات المرور ولا نستطيع استرجاعها.','Encryption happens on your device (AES-256-GCM). We never store passwords and cannot recover them.')+'</p></div>';
  $('enBack').onclick=()=>{d.classList.add('out');setTimeout(()=>d.remove(),240)};
  d.querySelectorAll('.sc-act').forEach(b=>b.onclick=()=>K.enc.start(b.dataset.k));
  d.querySelectorAll('.sc-h').forEach(r=>{const c=list[+r.dataset.i];r.querySelector('.op').onclick=()=>K.enc.enter(c);r.querySelector('.rm').onclick=async()=>{if(await K.ui.confirm(T('حذف هذه المحادثة الخاصة؟','Delete this private chat?'),true)){K.db.remove(c);K.enc.paint()}}})},
 start(type){
  const c=K.db.create();c.enc=type;c.mode='enc';c.title='🔒 '+T(TYPES[type].a,TYPES[type].e);
  const tl=SET[type].map(k=>'- **'+TOOLS[k][1]+'**').join('\n');
  c.msgs.push({role:'assistant',c:T('هذه **محادثة خاصة** بـ'+TYPES[type].a+' — تُحفظ هنا فقط ولا تظهر في سجل محادثاتك العام.\n\nالأدوات السريعة أسفل الشاشة:\n'+tl+'\n\nاضغط أداة، أو اسألني أي سؤال عن الحماية. 🔐','This is a **private chat** for '+TYPES[type].e.toLowerCase()+' — saved here only and hidden from your general history.\n\nQuick tools at the bottom:\n'+tl+'\n\nTap a tool, or ask me anything about protection. 🔐'),t:'',s:0,src:[],v:0});
  c.ts=Date.now();K.db.convs.unshift(c);K.db.save();K.enc.enter(c)},
 enter(c){
  const e=document.getElementById('enc');e&&e.remove();const s=document.getElementById('scan');s&&s.remove();
  if(K.state.streaming)K.chat.stop();K.state.cur=c;K.pref.set('cur',c.id);K.chat.show(c);K.home.closeDrawer();K.home.paintPills()},
 back(){const d=$('enc');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true}
};
})();
