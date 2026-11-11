/* Learning agent page: runs in a visible foreground service on the phone, only for the pages you list (robots.txt respected) */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const A=new Proxy({},{get:(_,k)=>window.Android?window.Android[k]:undefined});
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
 openCollector(){
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
 ,
 /* ---------------- task chat: write a task like a message, the agent runs it in the background, notifies you, and the chat can use what it learned ---------------- */
 open(){
  const old=$('agentT');if(old)old.remove();
  const d=document.createElement('div');d.id='agentT';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="atB">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('وكيل المهام','Task agent')+'</div><button class="sc-ib" id="atM" title="'+T('المزيد','More')+'">'+K.icon('ellipsis',22)+'</button></div>'
   +'<div class="sc-body at-body" id="atList"></div>'
   +'<div class="at-bar"><textarea id="atIn" rows="1" placeholder="'+T('اكتب مهمة للوكيل…','Write a task for the agent…')+'"></textarea><button id="atSend">'+K.icon('arrow-up',20)+'</button></div>';
  $('atB').onclick=()=>{clearInterval(K.agent._t);d.remove()};
  $('atM').onclick=async()=>{const i=await K.ui.choose(T('وكيل المهام','Task agent'),[T('اختبر ما تعلّمه (محادثة)','Test what it learned (chat)'),T('جمع صفحات (متقدم)','Collect pages (advanced)'),T('قيود الوكيل','Agent rules'),T('مسح قائمة المهام','Clear task list')]);
   if(i===0){d.remove();clearInterval(K.agent._t);K.home.newChat();K.toast(T('اسأل أي سؤال عن مهامك، وسيستخدم الوكيل ما حفظه ويذكر ذلك','Ask about your tasks; the answer will use what the agent saved and say so'))}
   else if(i===1)K.agent.openCollector();
   else if(i===2){K.ui.info(T('قيود الوكيل','Agent rules'),T('مصادر رسمية فقط (ويكيبيديا، MDN، Stack Overflow عبر واجهاتها الرسمية). لا يوتيوب ولا تيك توك ولا حسابات ولا تنزيل تطبيقات أو ملفات. لا يثبّت شيئاً ولا يلمس تطبيقاتك.','Official sources only (Wikipedia, MDN, Stack Overflow via their official APIs). No YouTube/TikTok, no accounts, no downloading apps or files. Installs nothing, touches none of your apps.'))}
   else if(i===3){if(await K.ui.confirm(T('مسح قائمة المهام؟ (المعرفة المحفوظة تبقى)','Clear the task list? (saved knowledge stays)'),true)){A&&A.agentClearTasks&&A.agentClearTasks();paintT()}}};
  const fmt=t=>t?new Date(t).toLocaleTimeString(K.lang()==='ar'?'ar':'en',{hour:'2-digit',minute:'2-digit'}):'';
  const paintT=()=>{let ts=[];try{ts=JSON.parse((A&&A.agentTasks&&A.agentTasks())||'[]')}catch(e){}
   const el=$('atList');if(!el)return;const near=el.scrollHeight-el.scrollTop-el.clientHeight<120;
   if(!ts.length){el.innerHTML='<div class="at-empty"><b>'+T('اكتب مهمتك وأنا أنفّذها','Write a task and I will run it')+'</b><p>'+T('يعمل الوكيل في الخلفية على جوالك، ويبحث في مصادر رسمية، ويلخّص بالنموذج، ويحفظ ما تعلّمه، ثم يرسل لك إشعاراً عند كل مهمة. يمكنك إرسال عدة مهام وتُنفَّذ بالتتابع.','The agent works in the background on your phone, searches official sources, summarises with the model, saves what it learned and notifies you for each task. Send several tasks; they run in order.')+'</p></div>';return}
   el.innerHTML=ts.map(t=>{const st=t.status;const lab={pending:T('في الانتظار','Queued'),running:T('قيد التنفيذ','Running'),done:T('اكتملت ✅','Done ✅'),failed:T('تعذّرت ⚠️','Failed ⚠️')}[st]||st;
    let body='';if(st==='running')body='<div class="at-step"><i class="td"></i>'+esc(t.step||'')+'</div>';
    else if(st==='done')body='<div class="at-res">'+K.md(t.result||'')+'</div>'+((t.sources||[]).length?'<div class="at-src">'+T('المصادر','Sources')+': '+(t.sources||[]).map((s,i)=>'<a href="'+esc(s.url)+'" data-url="'+esc(s.url)+'">['+(i+1)+'] '+esc(s.title).slice(0,40)+'</a>').join(' · ')+'</div>':'')+(t.summarised===false?'<div class="at-warn">'+T('حُفظت المصادر دون تلخيص (لا يوجد نموذج متاح)','Sources saved without a summary (no model available)')+'</div>':'');
    else if(st==='failed')body='<div class="at-warn">'+esc(t.error||'')+'</div>';
    return '<div class="at-u"><span>'+esc(t.text)+'</span><small>'+fmt(t.created)+'</small></div><div class="at-a"><div class="at-l"><b>'+lab+'</b>'+(t.done?'<small>'+fmt(t.done)+'</small>':'')+'</div>'+body+'</div>'}).join('');
   if(near)el.scrollTop=el.scrollHeight};
  window.__agent=function(){paintT()};
  K.agent._t=setInterval(paintT,2500);paintT();
  const ta=$('atIn');ta.oninput=()=>{ta.style.height='auto';ta.style.height=Math.min(140,ta.scrollHeight)+'px'};
  const send=()=>{const v=ta.value.trim();if(!v)return;
   if(!(A&&A.agentAddTask)){K.toast(T('يعمل داخل التطبيق فقط (ليس في المعاينة)','Works inside the app only (not in preview)'));return}
   const id=A.agentAddTask(v);if(!id){K.toast(T('تعذّر إضافة المهمة','Could not add the task'));return}ta.value='';ta.style.height='auto';paintT();K.native.haptic()};
  $('atSend').onclick=send}
};
})();

document.addEventListener('click',e=>{const b=e.target.closest&&e.target.closest('[data-act=agent]');if(b){K.home.closeSheet();K.agent.open()}});
