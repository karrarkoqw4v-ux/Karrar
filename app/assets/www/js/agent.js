/* Learning agent page: runs in a visible foreground service on the phone, only for the pages you list (robots.txt respected) */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
/* lazy access to the native bridge; methods are re-bound to the real object (a JavascriptInterface method refuses any other receiver) */
const A=new Proxy({},{get:(_,k)=>{const o=window.Android;if(!o||typeof o[k]!=='function')return undefined;return (...a)=>o[k](...a)}});
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
  $('atM').onclick=async()=>{const i=await K.ui.choose(T('وكيل المهام','Task agent'),[T('🧪 محادثة الاختبارات (خاصة)','🧪 Test chat (private)'),T('اختبر ما تعلّمه (محادثة عادية)','Ask what it learned (normal chat)'),T('جمع صفحات (متقدم)','Collect pages (advanced)'),T('قيود الوكيل','Agent rules')]);
   if(i===0){d.remove();clearInterval(K.agent._t);K.agentTest.open()}
   else if(i===1){d.remove();clearInterval(K.agent._t);K.home.newChat();K.toast(T('اسأل أي سؤال عن مهامك، وسيستخدم الوكيل ما حفظه ويذكر ذلك','Ask about your tasks; the answer will use what the agent saved and say so'))}
   else if(i===2)K.agent.openCollector();
   else if(i===3){K.ui.info(T('قيود الوكيل','Agent rules'),T('مصادر رسمية فقط (ويكيبيديا، MDN، Stack Overflow عبر واجهاتها الرسمية). لا يوتيوب ولا تيك توك ولا حسابات ولا تنزيل تطبيقات أو ملفات. لا يثبّت شيئاً ولا يلمس تطبيقاتك. مهامك محفوظة ولا تُمسح.','Official sources only. No YouTube/TikTok, accounts, or downloads. Installs nothing. Your tasks are kept and never deleted.'))}};
  const fmt=t=>t?new Date(t).toLocaleTimeString(K.lang()==='ar'?'ar':'en',{hour:'2-digit',minute:'2-digit'}):'';
  const paintT=()=>{let ts=[];try{ts=JSON.parse((A&&A.agentTasks&&A.agentTasks())||'[]')}catch(e){}
   const el=$('atList');if(!el)return;const near=el.scrollHeight-el.scrollTop-el.clientHeight<120;
   if(!ts.length){el.innerHTML='<div class="at-empty"><b>'+T('اكتب مهمتك وأنا أنفّذها','Write a task and I will run it')+'</b><p>'+T('يعمل الوكيل في الخلفية على جوالك، ويبحث في مصادر رسمية، ويلخّص بالنموذج، ويحفظ ما تعلّمه، ثم يرسل لك إشعاراً عند كل مهمة. يمكنك إرسال عدة مهام وتُنفَّذ بالتتابع.','The agent works in the background on your phone, searches official sources, summarises with the model, saves what it learned and notifies you for each task. Send several tasks; they run in order.')+'</p></div>';return}
   el.innerHTML=ts.map(t=>{const st=t.status;const lab={pending:T('في الانتظار','Queued'),running:T('قيد التنفيذ','Running'),done:T('اكتملت ✅','Done ✅'),failed:T('تعذّرت ⚠️','Failed ⚠️')}[st]||st;
    let body='';if(st==='running')body='<div class="at-step"><i class="td"></i>'+esc(t.step||'')+'</div>';
    else if(st==='done')body='<div class="at-res md">'+K.md(t.result||'')+'</div>'+(t.verified?'<div class="at-ok">'+T('✔ دُقّقت المسودة مقابل المصادر','✔ Draft checked against the sources')+'</div>':'')+((t.sources||[]).length?'<div class="at-src">'+T('المصادر','Sources')+': '+(t.sources||[]).map((s,i)=>'<a href="'+esc(s.url)+'" data-url="'+esc(s.url)+'">['+(i+1)+'] '+esc(s.title).slice(0,40)+'</a>').join(' · ')+'</div>':'')+(t.summarised===false?'<div class="at-warn">'+T('حُفظت المصادر دون تلخيص (لا يوجد نموذج متاح)','Sources saved without a summary (no model available)')+'</div>':'');
    else if(st==='failed')body='<div class="at-warn">'+esc(t.error||'')+'</div>';
    return '<div class="at-u"><span>'+esc(t.text)+'</span><small>'+fmt(t.created)+'</small></div><div class="at-a"><div class="at-l"><b>'+lab+'</b>'+(t.done?'<small>'+fmt(t.done)+'</small>':'')+'</div>'+body+'</div>'}).join('');
   {const pend=ts.some(t=>t.status==='pending'||t.status==='running');let run=true;try{run=!A.agentRunning||A.agentRunning()}catch(e){}
    if(pend&&!run){el.insertAdjacentHTML('beforeend','<div class="at-warn" style="text-align:center;margin:16px 0">'+T('الخدمة متوقفة وفيها مهام منتظرة','The service is stopped but tasks are waiting')+'<br><button class="at-run" id="atRun">'+T('تشغيل الوكيل الآن','Run agent now')+'</button></div>');const rb=$('atRun');if(rb)rb.onclick=()=>{let m='ok';try{m=A.agentRun()}catch(e){m=String(e)}if(m!=='ok')K.ui.info(T('تعذّر التشغيل','Could not start'),String(m));else K.toast(T('بدأ الوكيل','Agent started'))}}}
   if(near)el.scrollTop=el.scrollHeight};
  window.__agent=function(){paintT()};
  K.agent._t=setInterval(paintT,2500);paintT();
  const ta=$('atIn');ta.oninput=()=>{ta.style.height='auto';ta.style.height=Math.min(140,ta.scrollHeight)+'px'};
  const send=()=>{const v=ta.value.trim();if(!v)return;
   if(!(A&&A.agentAddTask)){K.toast(T('يعمل داخل التطبيق فقط (ليس في المعاينة)','Works inside the app only (not in preview)'));return}
   let r={};try{r=JSON.parse(A.agentAddTask(v)||'{}')}catch(e){r={err:String(e)}}
   if(r.err){K.ui.info(T('تعذّر إضافة المهمة','Could not add the task'),String(r.err));return}
   ta.value='';ta.style.height='auto';paintT();K.native.haptic();
   if(r.service&&r.service!=='ok')K.ui.info(T('حُفظت المهمة لكن تعذّر تشغيل الخدمة','Task saved but the service could not start'),String(r.service)+'\n\n'+T('اضغط «تشغيل الوكيل الآن» أو استثنِ التطبيق من تحسين البطارية وأذن له بالعمل في الخلفية.','Tap "Run agent now", or exempt the app from battery optimisation and allow background activity.'))};
  $('atSend').onclick=send}
};
/* ---------------- private test chat inside the task agent: slash commands that exercise every real capability ---------------- */
const fmtB=n=>n>1048576?(n/1048576).toFixed(1)+' MB':n>1024?(n/1024).toFixed(1)+' KB':n+' B';
const when=t=>t?new Date(t).toLocaleString(K.lang()==='ar'?'ar':'en',{dateStyle:'short',timeStyle:'short'}):'—';
const HELP=T('## 🧪 محادثة اختبار الوكيل\nاكتب أوامر تبدأ بـ `/` لتجرّب كل شيء فعلياً:\n\n- `/اختبار` فحص حقيقي: الشبكة، النموذج، التخزين، الإشعارات، الخدمة\n- `/حالة` حالة الوكيل والمهام والمخزن\n- `/مهام` قائمة مهامك\n- `/مهمة نص المهمة` يضيف مهمة وينفّذها في الخلفية\n- `/المعرفة` ما تعلّمه الوكيل وحفظه\n- `/ملفات` الملفات المحفوظة على جوالك وأحجامها\n- `/حفظ نص` يحفظ ملاحظة في معرفة الوكيل\n- `/تصدير` يحفظ كل المهام والمعرفة في ملف (تنزيلات بوصلة)\n- `/ابنِ وصف الصفحة` يبني صفحة ويب، **ثم يشغّلها ويختبرها بنفسه** ويصلح أخطاءها (حتى 3 مرات) قبل أن يسلّمها لك\n\nأي كلام عادي تكتبه هنا يجيب عنه النموذج مستخدماً معرفة الوكيل ويذكر ذلك.','## 🧪 Agent test chat\nType `/` commands to test everything for real:\n\n- `/test` real checks: network, model, storage, notifications, service\n- `/status`, `/tasks`, `/task <text>`, `/knowledge`, `/files`, `/save <text>`, `/export`\n\nAny normal message is answered by the model using the agent\'s knowledge.');
async function selfTest(){
 const rows=[],ok=(n,v,d)=>rows.push((v?'✅ ':'❌ ')+n+(d?' — '+d:''));
 const A_=window.Android;ok(T('الاتصال بالتطبيق الأصلي','Native bridge'),!!(A_&&A_.agentDiag));
 let dg={};try{dg=JSON.parse(A.agentDiag()||'{}')}catch(e){}
 ok(T('التخزين (كتابة وقراءة)','Storage write/read'),!!(A.agentSelfTestWrite&&A.agentSelfTestWrite()));
 try{const r=await K.native.http('GET','https://en.wikipedia.org/w/api.php?action=query&meta=siteinfo&format=json',{'User-Agent':'BawsalaSelfTest/1.0'},'');ok(T('الشبكة (ويكيبيديا)','Network (Wikipedia)'),r&&r.code===200,'HTTP '+(r&&r.code))}catch(e){ok(T('الشبكة','Network'),false,String(e))}
 try{const t=await K.llm('Reply with the single word OK.','ping',25000);ok(T('النموذج الذكي','AI model'),!!(t&&t.length),(t||'').slice(0,30))}catch(e){ok(T('النموذج','Model'),false,String(e))}
 const nt=A.agentNotifyTest?A.agentNotifyTest():'n/a';ok(T('الإشعارات','Notifications'),nt==='sent',nt==='no-permission'?T('الإذن غير ممنوح: فعّله من إعدادات الجهاز','permission not granted'):nt);
 ok(T('الخدمة الخلفية','Background service'),!!A.agentRunning,A.agentRunning&&A.agentRunning()?T('تعمل الآن','running now'):T('جاهزة (غير شغّالة الآن)','ready (idle)'));
 if(dg.last_error)rows.push('⚠️ '+T('آخر خطأ في الخدمة','Last service error')+': `'+dg.last_error+'`');
 return '## '+T('نتيجة الفحص الحقيقي','Real self-test result')+'\n\n'+rows.join('\n\n')}

/* build -> run in a hidden sandboxed page -> read real errors -> fix -> repeat (max 3) -> only then deliver */
function testHtml(code){return new Promise(res=>{const errs=[];const f=document.createElement('iframe');f.setAttribute('sandbox','allow-scripts');f.style.cssText='position:fixed;left:-9999px;top:0;width:390px;height:700px;border:0';
 const hook='<script>window.addEventListener("error",function(e){parent.postMessage({t:"err",m:String(e.message||e.error)},"*")});window.addEventListener("unhandledrejection",function(e){parent.postMessage({t:"err",m:"promise: "+String(e.reason)},"*")});var __ce=console.error;console.error=function(){parent.postMessage({t:"err",m:"console.error: "+[].slice.call(arguments).join(" ")},"*");__ce.apply(console,arguments)};window.addEventListener("load",function(){setTimeout(function(){parent.postMessage({t:"done",len:((document.body&&document.body.innerText)||"").trim().length,ext:document.querySelectorAll("img[src^=http],script[src^=http],link[href^=http]").length},"*")},900)})<\/script>';
 let done=false;const finish=()=>{if(done)return;done=true;window.removeEventListener('message',on);clearTimeout(tm);f.remove();res(errs)};
 const on=e=>{if(e.source!==f.contentWindow)return;const d=e.data||{};if(d.t==='err')errs.push(d.m);if(d.t==='done'){if(d.len<20)errs.push('the page is empty or has almost no visible text');if(d.ext)errs.push('it uses '+d.ext+' external resource(s) that will not load offline');finish()}};
 const tm=setTimeout(()=>{errs.push('the page did not finish loading within 7 seconds');finish()},7000);
 window.addEventListener('message',on);
 f.srcdoc=/<head[^>]*>/i.test(code)?code.replace(/<head[^>]*>/i,m=>m+hook):hook+code;document.body.appendChild(f)})}
async function buildAndTest(desc,say){
 const SYS='You are a senior front-end engineer. Output ONLY ONE complete self-contained HTML document (inline CSS and JS, no external URLs, no placeholder images: use inline SVG or CSS art). Use Arabic RTL when the request is Arabic. Mobile-first, one screen at a time with a bottom tab bar when there are several screens, real working logic with sample data, loading/empty/error states, accessible contrast. Wrap localStorage in try/catch. No alert/confirm.';
 let code='',errs=[];const log=[];
 for(let round=1;round<=3;round++){
  say(T('⏳ المحاولة '+round+' من 3: '+(round===1?'أكتب الصفحة…':'أصلح الأخطاء التي اكتشفها الاختبار…'),'⏳ Attempt '+round+' of 3: '+(round===1?'writing the page…':'fixing what the test found…')));
  const prompt=round===1?desc:'Original request:\n'+desc+'\n\nYour previous HTML:\n'+code+'\n\nProblems found by an automated run of the page:\n- '+errs.join('\n- ')+'\n\nReturn the complete corrected HTML only.';
  const out=await K.llm(SYS,prompt,150000);
  const m=out.match(/<!doctype[\s\S]*<\/html>/i)||out.match(/<html[\s\S]*<\/html>/i);
  if(!m){errs=['the reply did not contain a complete HTML document'];log.push('❌ '+T('المحاولة '+round+': لم يُنتج النموذج صفحة كاملة','Attempt '+round+': no complete page'));continue}
  code=m[0];errs=await testHtml(code);
  log.push((errs.length?'❌ ':'✅ ')+T('المحاولة '+round+': ','Attempt '+round+': ')+(errs.length?errs.slice(0,3).join(' | '):T('اجتازت الاختبار (لا أخطاء، والصفحة تعرض محتوى)','passed (no errors, the page renders content)')));
  if(!errs.length)break}
 return {code,errs,log}}
K.agentTest={
 conv(){return K.db.convs.find(c=>c.agentTest)},
 open(){let c=K.agentTest.conv();
  if(!c){c=K.db.create();c.agentTest=true;c.title='🧪 '+T('اختبار الوكيل','Agent test');c.msgs.push({role:'assistant',c:HELP,t:'',s:0,src:[],v:0});c.ts=Date.now();K.db.convs.unshift(c);K.db.save()}
  if(K.state.streaming)K.chat.stop();K.state.cur=c;K.pref.set('cur',c.id);K.chat.show(c);K.home.closeDrawer&&K.home.closeDrawer()},
 handle(text,conv){const t=(text||'').trim();if(!conv.agentTest||t[0]!=='/')return false;
  const sp=t.indexOf(' '),cmd=(sp<0?t:t.slice(0,sp)).slice(1).toLowerCase(),arg=sp<0?'':t.slice(sp+1).trim();
  const say=md=>K.chat.localReply(conv,md);
  const guard=()=>{if(!(A.agentDiag)){say(T('⚠️ هذا الأمر يعمل داخل التطبيق فقط (ليس في المعاينة).','⚠️ This command works inside the app only.'));return false}return true};
  (async()=>{try{
   if(cmd==='مساعدة'||cmd==='help'||cmd==='؟'){say(HELP);return}
   if(!guard())return;
   if(cmd==='اختبار'||cmd==='test'){say(T('⏳ أجري الفحص الحقيقي…','⏳ Running real checks…'));K.chat.localReply(conv,await selfTest());return}
   if(cmd==='حالة'||cmd==='status'){const d=JSON.parse(A.agentDiag());say('## '+T('حالة الوكيل','Agent status')+'\n\n- '+T('الخدمة','Service')+': '+(d.running?T('تعمل','running'):T('متوقفة','idle'))+'\n- '+T('مهام','Tasks')+': '+d.done+' '+T('مكتملة','done')+' · '+d.pending+' '+T('منتظرة','pending')+' · '+d.running_tasks+' '+T('قيد التنفيذ','running')+' · '+d.failed+' '+T('فاشلة','failed')+'\n- '+T('المعرفة المحفوظة','Knowledge notes')+': '+d.knowledge+'\n- '+T('الملفات','Files')+': '+d.files+' ('+fmtB(d.bytes)+')\n- '+T('إذن الإشعارات','Notification permission')+': '+(d.notif_permission?'✅':'❌')+'\n- Android SDK: '+d.sdk+(d.last_error?'\n- ⚠️ '+d.last_error:''));return}
   if(cmd==='مهام'||cmd==='tasks'){const ts=JSON.parse(A.agentTasks()||'[]');say(ts.length?'## '+T('مهامك','Your tasks')+' ('+ts.length+')\n\n'+ts.map((x,i)=>(i+1)+'. '+({done:'✅',failed:'⚠️',running:'⏳',pending:'🕒'}[x.status]||'')+' '+x.text+' — '+when(x.created)).join('\n'):T('لا توجد مهام بعد.','No tasks yet.'));return}
   if(cmd==='مهمة'||cmd==='task'){if(!arg){say(T('اكتب: `/مهمة نص المهمة`','Write: `/task text`'));return}const r=JSON.parse(A.agentAddTask(arg)||'{}');say(r.err?'⚠️ '+r.err:(r.service&&r.service!=='ok'?'⚠️ '+T('حُفظت المهمة لكن تعذّر تشغيل الخدمة: ','Saved, but the service could not start: ')+r.service:'✅ '+T('أُضيفت المهمة وبدأ التنفيذ في الخلفية. سيصلك إشعار عند انتهائها. اكتب `/مهام` لمتابعتها.','Task added and running in the background. You will get a notification. Type `/tasks` to follow it.')));return}
   if(cmd==='المعرفة'||cmd==='knowledge'){const kn=JSON.parse(A.agentKnowledgeList()||'[]');say(kn.length?'## '+T('ما تعلّمه الوكيل','What the agent learned')+' ('+kn.length+')\n\n'+kn.map((k,i)=>(i+1)+'. **'+k.title+'** — '+k.sources+' '+T('مصدر','sources')+' · '+k.chars+' '+T('حرف','chars')+' · '+when(k.ts)).join('\n'):T('لم يحفظ الوكيل أي معرفة بعد. أضف مهمة بـ `/مهمة`.','Nothing learned yet. Add a task with `/task`.'));return}
   if(cmd==='ملفات'||cmd==='files'){const fs=JSON.parse(A.agentFiles()||'[]');say(fs.length?'## '+T('الملفات المحفوظة','Saved files')+' ('+fs.length+')\n\n'+fs.map(f=>'- `'+f.name+'` — '+fmtB(f.bytes)).join('\n')+'\n\n'+T('المكان: مجلد التطبيق الخاص (لا يصل إليه غيره).','Location: the app\'s private folder.'):T('لا توجد ملفات بعد.','No files yet.'));return}
   if(cmd==='حفظ'||cmd==='save'){if(!arg){say(T('اكتب: `/حفظ النص الذي تريد حفظه`','Write: `/save text`'));return}say(A.agentSaveNote(arg)?'✅ '+T('حُفظت في معرفة الوكيل، وسيستخدمها في الإجابة عن أسئلة مشابهة.','Saved to the agent\'s knowledge; it will use it for similar questions.'):'⚠️ '+T('تعذّر الحفظ','Could not save'));return}
   if(cmd==='تصدير'||cmd==='export'){const md=A.agentExport();const r=await K.files.saveNamed('bawsala-agent-export.md',md);say('✅ '+T('صُدّرت كل المهام والمعرفة إلى ملف `bawsala-agent-export.md` ('+fmtB(md.length)+'). ابحث عنه في مجلد تنزيلات بوصلة.','Exported all tasks and knowledge to `bawsala-agent-export.md` ('+fmtB(md.length)+') in Bawsala downloads.')+(r&&String(r).indexOf('err')===0?'\n\n⚠️ '+r:''));return}
   if(cmd.replace(/[\u064B-\u0652]/g,'')==='ابن'||cmd==='build'){if(!arg){say(T('اكتب: `/ابنِ وصف ما تريد بناءه`','Write: `/build what you want`'));return}
    const r=await buildAndTest(arg,say);
    if(!r.code){say('## '+T('تعذّر البناء','Build failed')+'\n\n'+r.log.join('\n\n')+'\n\n'+T('لم ينتج النموذج صفحة صالحة (تحقق من مفتاح النموذج والاتصال).','The model did not produce a valid page (check the model key and connection).'));return}
    const nm=(arg.replace(/[^\p{L}\p{N} ]/gu,'').trim().split(/\s+/).slice(0,3).join(' ')||'App');
    let saved='';try{const sv=await K.files.saveNamed(nm.replace(/\s+/g,'-')+'.html',r.code);saved=sv&&String(sv).indexOf('err')===0?'\n\n⚠️ '+sv:''}catch(e){}
    say('## '+(r.errs.length?'⚠️ '+T('بُني لكن بقيت مشاكل','Built, but problems remain'):'✅ '+T('بُني واجتاز اختباره','Built and passed its test'))+'\n\n'+r.log.join('\n\n')+(r.errs.length?'\n\n'+T('لم أصلح كل الأخطاء في 3 محاولات. جرّب وصفاً أبسط أو نموذجاً أقوى.','Not everything was fixed in 3 attempts. Try a simpler brief or a stronger model.'):'')+'\n\n```app:'+nm+'\n'+r.code+'\n```'+saved);return}
   say(T('أمر غير معروف: `/'+cmd+'`. اكتب `/مساعدة` لقائمة الأوامر.','Unknown command. Type `/help`.'))
  }catch(e){say('⚠️ '+String(e))}})();
  return true}
};
})();

document.addEventListener('click',e=>{const b=e.target.closest&&e.target.closest('[data-act=agent]');if(b){K.home.closeSheet();K.agent.open()}});
