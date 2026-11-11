/* Learning agent page: runs in a visible foreground service on the phone, only for the pages you list (robots.txt respected) */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const A=window.Android;
const FALLBACK={blocked:['youtube.com','youtu.be','tiktok.com','instagram.com','facebook.com','x.com','twitter.com','snapchat.com','t.me','linkedin.com','reddit.com','pinterest.com','discord.com','twitch.tv','netflix.com','spotify.com','play.google.com','apps.apple.com'],allowed:['wikipedia.org','developer.mozilla.org','developer.android.com','kotlinlang.org','dart.dev','flutter.dev','swift.org','react.dev','nodejs.org','go.dev','doc.rust-lang.org','docs.python.org','docs.oracle.com','learn.microsoft.com','web.dev'],ext:['.apk','.exe','.zip','.rar','.torrent','.mp4','.mp3','.pdf'],words:['login','signin','signup','register','account','checkout','cart','admin','password','oauth','captcha'],maxPages:100};
function policy(){try{return A&&A.agentPolicy?JSON.parse(A.agentPolicy()):FALLBACK}catch(e){return FALLBACK}}
const hm=(h,d)=>h===d||h.endsWith('.'+d);
function check(url,own){let u;try{u=new URL(url)}catch(e){return T('رابط غير صالح','Invalid URL')}const P=policy(),h=u.hostname.toLowerCase();
 if(u.protocol!=='https:')return T('غير آمن (ليس https)','Not https');
 for(const b of P.blocked)if(hm(h,b))return T('منصة تمنع الجمع الآلي ('+b+')','Platform forbids automated collection ('+b+')');
 const path=(u.pathname+'?'+u.search).toLowerCase();for(const w of P.words)if(path.includes(w))return T('صفحة حساب أو دخول أو دفع','Account / login / payment page');
 for(const e of P.ext)if(u.pathname.toLowerCase().endsWith(e))return T('ملف تنزيل أو وسائط ('+e+')','Download / media file ('+e+')');
 for(const a of P.allowed)if(hm(h,a))return null;for(const o of own)if(o&&hm(h,o))return null;
 return T('غير مدرج في المصادر المسموحة (أضفه في «مواقع أملكها» إن كان لك)','Not on the allowed list (add it under "Sites I own" if it is yours)')}
const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
function paint(st){
 const el=$('agBox');if(!el)return;st=st||{};const tot=st.total||0,p=tot?Math.round((st.done||0)/tot*100):0;
 const lab={running:T('يعمل الآن…','Running…'),done:T('تم الشغل بنجاح ✅','Finished ✅'),stopped:T('أُوقف','Stopped'),error:T('خطأ','Error')}[st.state]||T('جاهز','Ready');
 el.innerHTML='<div class="ag-st"><b>'+lab+'</b><span>'+(st.done||0)+' / '+tot+'</span></div><div class="ag-bar"><i style="width:'+p+'%"></i></div>'
  +'<div class="ag-n"><div><b>'+(st.saved||0)+'</b><small>'+T('صفحة محفوظة','pages saved')+'</small></div><div><b>'+(st.tokens||0).toLocaleString('en')+'</b><small>'+T('توكن (تقدير)','tokens (est.)')+'</small></div></div>';
 const run=st.state==='running';$('agGo').classList.toggle('hidden',run);$('agStop').classList.toggle('hidden',!run)}
window.__agent=paint;
K.agent={
 open(){
  const old=$('agent');if(old)old.remove();
  const d=document.createElement('div');d.id='agent';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="agB">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('وكيل التعلّم','Learning agent')+'</div><span class="sc-ib ph"></span></div>'
   +'<div class="sc-body"><div class="ag-card"><b>'+T('كيف يعمل','How it works')+'</b><p>'+T('يعمل في الخلفية بإشعار ظاهر، ويقرأ فقط الصفحات التي تكتبها أدناه (ويحترم robots.txt)، ويحفظ النص في مجلد التطبيق الخاص، ثم يرسل لك إشعار «تم الشغل بنجاح». أضف صفحاتك أو صفحات تسمح بإعادة استخدامها فقط.','Runs in the background with a visible notification, reads only the pages you list (robots.txt respected), stores text in the app\'s private folder, then notifies you. Add only pages you own or that allow reuse.')+'</p></div>'
   +'<details class="ag-card" id="agPol"><summary><b>'+T('قيود الوكيل (يفرضها الكود)','Agent rules (enforced in code)')+'</b></summary><div id="agPolBody"></div></details>'
   +'<div class="ag-card"><label>'+T('الروابط (رابط في كل سطر)','URLs (one per line)')+'</label><textarea id="agUrls" rows="6" dir="ltr" placeholder="https://example.com/page1">'+esc(K.pref.get('agent_urls',''))+'</textarea>'
   +'<label>'+T('مواقع أملكها (اختياري، نطاق في كل سطر)','Sites I own (optional, one domain per line)')+'</label><textarea id="agOwn" rows="2" dir="ltr" placeholder="mysite.com">'+esc(K.pref.get('agent_own',''))+'</textarea><div id="agWarn" class="ag-warn"></div>'
   +'<label>'+T('التأخر بين الصفحات (ثوانٍ)','Delay between pages (s)')+'</label><input id="agDelay" type="number" min="2" value="'+esc(K.pref.get('agent_delay','3'))+'"></div>'
   +'<div id="agBox" class="ag-card"></div>'
   +'<button class="pbtn" id="agGo">'+T('ابدأ الوكيل','Start agent')+'</button><button class="pcan hidden" id="agStop">'+T('إيقاف','Stop')+'</button>'
   +'<p class="ag-note">'+T('لا يطلب صلاحية مشرف الجهاز ولا يثبّت تطبيقات ولا يلمس تطبيقاتك الأخرى. قد يطلب أندرويد منك السماح بالإشعارات، وإن أوقف النظام التطبيق لتوفير البطارية فاستثنِه من تحسين البطارية.','No device-admin, no app installs, other apps untouched. Android may ask for notification permission; exempt the app from battery optimisation if the system stops it.')+'</p></div>';
  $('agB').onclick=()=>d.remove();
  const P=policy();$('agPolBody').innerHTML='<ul class="ag-rules"><li>'+T('يقرأ فقط الروابط التي تكتبها أنت، ولا يبحث بنفسه عن مواقع.','Reads only the URLs you type; it never searches on its own.')+'</li><li>'+T('يسمح فقط بمصادر موثوقة للتعلّم (وثائق المطوّرين وويكيبيديا) ومواقعك أنت.','Only trusted learning sources (developer docs, Wikipedia) and your own sites.')+'</li><li>'+T('ممنوع: يوتيوب وتيك توك وإنستغرام وفيسبوك وX وتيليجرام وريديت ولينكدإن وغيرها.','Blocked: YouTube, TikTok, Instagram, Facebook, X, Telegram, Reddit, LinkedIn and others.')+'</li><li>'+T('ممنوع: تسجيل الدخول أو إنشاء حسابات أو الدفع أو تجاوز الكابتشا.','Blocked: login, sign-up, payments, bypassing CAPTCHA.')+'</li><li>'+T('ممنوع: تنزيل ملفات أو تطبيقات أو فيديوهات (apk وzip وmp4…).','Blocked: downloading files, apps or videos (apk, zip, mp4…).')+'</li><li>'+T('يحترم robots.txt، ولا يتبع التحويل لموقع آخر، ويتأخر بين الصفحات، وحدّه '+P.maxPages+' صفحة في كل تشغيل.','Respects robots.txt, never follows redirects off-site, delays between pages, max '+P.maxPages+' pages per run.')+'</li><li>'+T('لا يثبّت تطبيقات ولا يطلب صلاحية مشرف الجهاز ولا يلمس تطبيقاتك الأخرى.','Installs no apps, no device-admin, touches no other apps.')+'</li></ul>';
  const warn=()=>{const own=$('agOwn').value.split('\n').map(x=>x.trim().toLowerCase()).filter(Boolean);const bad=[];$('agUrls').value.split('\n').map(x=>x.trim()).filter(Boolean).forEach(u=>{const r=check(u,own);if(r)bad.push('⛔ '+u+' — '+r)});$('agWarn').innerHTML=bad.length?bad.map(esc).join('<br>'):''};
  $('agUrls').oninput=warn;$('agOwn').oninput=warn;warn();
  try{paint(JSON.parse((A&&A.agentStatus&&A.agentStatus())||'{}'))}catch(e){paint({})}
  $('agGo').onclick=()=>{const own=$('agOwn').value.split('\n').map(x=>x.trim().toLowerCase()).filter(Boolean);const urls=$('agUrls').value.split('\n').map(x=>x.trim()).filter(x=>/^https?:\/\//.test(x)&&!check(x,own));
   if(!urls.length){K.toast(T('لا يوجد رابط مسموح. راجع القيود','No allowed URL. See the rules'));return}
   K.pref.set('agent_urls',$('agUrls').value);K.pref.set('agent_own',$('agOwn').value);K.pref.set('agent_delay',$('agDelay').value||'3');
   if(A&&A.agentStart){A.agentStart(JSON.stringify({urls:urls,own:own,delay:+$('agDelay').value||3}));paint({state:'running',done:0,total:urls.length,saved:0,tokens:0});K.toast(T('بدأ الوكيل في الخلفية','Agent started'))}
   else K.toast(T('متاح داخل التطبيق فقط','Available in the app only'))};
  $('agStop').onclick=()=>{A&&A.agentStop&&A.agentStop();K.toast(T('جارٍ الإيقاف…','Stopping…'))}}
};
})();
