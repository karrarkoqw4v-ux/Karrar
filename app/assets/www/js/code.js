/* Code workspace: sessions, GitHub connection sheet, server picker + diagnostics (modelled on cloud coding apps) */
window.K=window.K||{};
(function(){
K.icons.activity='<path d="M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2"/>';
K.icons.server='<rect width="20" height="8" x="2" y="2" rx="2" ry="2"/><rect width="20" height="8" x="2" y="14" rx="2" ry="2"/><line x1="6" x2="6.01" y1="6" y2="6"/><line x1="6" x2="6.01" y1="18" y2="18"/>';
K.icons.wrench='<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>';
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const GH=(px,c)=>{const b=K.brands.github;return '<svg viewBox="'+b.vb+'" width="'+px+'" height="'+px+'">'+b.b.replace(/fill="#161614"/g,'fill="'+(c||'currentColor')+'"')+'</svg>'};
const em=c=>'<img class="cd-em" src="emoji/'+c+'.svg" alt="">';
const P={get repo(){return K.pref.get('code_repo','')},set repo(v){K.pref.set('code_repo',v)},get env(){return K.pref.get('code_env','default')},set env(v){K.pref.set('code_env',v)}};
const ago=ts=>{const s=Math.max(1,(Date.now()-ts)/1000);return s<3600?Math.max(1,Math.round(s/60))+'m':s<86400?Math.round(s/3600)+'h':Math.round(s/86400)+'d'};
const envName=e=>e==='cloud'?T('السيرفر السحابي','Cloud server'):T('السيرفر الافتراضي','Default server');
const envShort=e=>e==='cloud'?T('سحابي','Cloud'):T('افتراضي','Default');
let sheets=[],q='';

/* ---------- bottom sheet ---------- */
function sheet(html,cls){
 const w=document.createElement('div');w.className='cd-layer';
 w.innerHTML='<div class="cd-bd"></div><div class="cd-sheet '+(cls||'')+'"><i class="cd-grab"></i>'+html+'</div>';
 $('code').appendChild(w);sheets.push(w);
 const close=()=>{const i=sheets.indexOf(w);if(i<0)return;sheets.splice(i,1);w.classList.add('out');setTimeout(()=>w.remove(),220)};
 w.querySelector('.cd-bd').onclick=close;w.close=close;K.emojifyEl&&K.emojifyEl(w);return w;
}

/* ---------- main screen ---------- */
K.code={
 isOpen(){return !!$('code')},
 back(){if(!$('code'))return false;if(sheets.length){sheets[sheets.length-1].close();return true}K.code.close();return true},
 close(){const d=$('code');if(!d)return;sheets=[];d.classList.add('out');setTimeout(()=>d.remove(),240)},
 open(){
  if($('code'))$('code').remove();sheets=[];q='';
  const d=document.createElement('div');d.id='code';d.className='cd';d.dir=K.lang()==='ar'?'rtl':'ltr';
  d.innerHTML='<div class="cd-main" id="cdMain"></div>';document.body.appendChild(d);K.code.paint();
 },
 paint(){
  const d=$('cdMain');if(!d)return;
  const list=K.db.convs.filter(c=>c.code&&c.msgs.length&&(!q||c.title.toLowerCase().includes(q))).sort((a,b)=>b.ts-a.ts);
  const gh=!!K.gh.tok();
  d.innerHTML='<div class="cd-top"><button class="cd-ib" id="cdBack">'+K.icon('chevron-right',24)+'</button><div class="cd-title">'+T('برمجة التطبيقات','Code')+'</div><span class="cd-ib ph"></span></div>'
   +'<div class="cd-body">'
   +'<div class="cd-sect">'+T('البداية','Get started')+'</div>'
   +'<div class="cd-tiles">'
   +'<button class="cd-tile" id="tConn"><span class="ti" style="--c:#24292f">'+GH(22,'#fff')+'</span><b>'+T('الاتصال','Connect')+'</b><small>'+(gh?T('متصل بـ GitHub','GitHub connected'):T('اربط GitHub','Link GitHub'))+'</small></button>'
   +'<button class="cd-tile" id="tNew"><span class="ti" style="--c:#8b5cf6">'+K.icon('wand-sparkles',22)+'</span><b>'+T('الإبداع','Create')+'</b><small>'+T('ابدأ جلسة جديدة','Start a session')+'</small></button>'
   +'<button class="cd-tile" id="tSet"><span class="ti" style="--c:#0ea5e9">'+K.icon('wrench',22)+'</span><b>'+T('تجهيز التطوير','Setup')+'</b><small>'+envShort(P.env)+'</small></button>'
   +'</div>'
   +'<div class="cd-sect row"><span>'+T('الجلسات','Sessions')+'</span><span class="cd-n">'+list.length+'</span></div>'
   +(list.length?list.map((c,i)=>'<button class="cd-ses" data-i="'+i+'" style="--i:'+i+'"><span class="dot"></span><div class="tx"><b>'+esc(c.title||T('جلسة','Session'))+'</b><small>'+(c.repo?esc(c.repo):T('بدون مستودع','No repository'))+' '+K.icon('cloud',14)+'</small></div><time>'+ago(c.ts)+'</time></button>').join('')
    :'<div class="cd-empty">'+em('1f680')+'<b>'+T('لا توجد جلسات بعد','No sessions yet')+'</b><span>'+T('اضغط «جلسة جديدة» لتبدأ بناء تطبيقك','Tap “New session” to start building your app')+'</span></div>')
   +'</div>'
   +'<div class="cd-fab"><button class="cd-new" id="cdNew">'+T('جلسة جديدة','New session')+K.icon('plus',22,2.6)+'</button><button class="cd-ib sr" id="cdSr">'+K.icon('search',22)+'</button></div>';
  K.emojifyEl&&K.emojifyEl(d);
  $('cdBack').onclick=()=>K.code.close();
  $('tConn').onclick=()=>K.code.github();$('tNew').onclick=$('cdNew').onclick=()=>K.code.newSession();$('tSet').onclick=()=>K.code.server();
  $('cdSr').onclick=async()=>{const v=await K.ui.prompt({title:T('بحث في الجلسات','Search sessions'),value:q});if(v!==null){q=v.toLowerCase();K.code.paint()}};
  d.querySelectorAll('.cd-ses').forEach(b=>b.onclick=()=>{const c=list[+b.dataset.i];K.code.close();K.home.openConv(c)});
 },

 /* ---------- GitHub connect sheet ---------- */
 github(after){
  const ok=!!K.gh.tok();
  const w=sheet('<div class="cd-art"><div class="screen"><span class="gm">'+GH(54,'#fff')+'</span>'
   +'<span class="f f1">'+em('1f4c1')+'</span><span class="f f2">'+em('1f4c4')+'</span><span class="f f3">'+em('1f680')+'</span><span class="f f4">'+em('2728')+'</span></div></div>'
   +'<h3 class="cd-h">'+(ok?T('GitHub متصل','GitHub is connected'):T('قم بتوصيل مشروعك','Connect your project'))+'</h3>'
   +'<p class="cd-p">'+(ok?T('حسابك مرتبط. يمكنك اختيار مستودع لجلساتك أو قطع الاتصال.','Your account is linked. Pick a repository for your sessions or disconnect.')
     :T('يقوم التطبيق برفع مشاريعك وملفاتك وتحديثاتك على GitHub. اعلم أن GitHub هو الأفضل لتتبّع المشاريع وتحسين تحديثات تطبيقاتك، فقم بالاتصال لتبدأ.','The app uploads your projects, files and updates to GitHub. GitHub is the best place to track projects and improve your app updates, so connect to get started.'))+'</p>'
   +(ok?'':'<p class="cd-terms">'+T('بالمتابعة فإنك توافق على ','By continuing you accept the ')+'<u id="cdTerms">'+T('الشروط','terms')+'</u></p>')
   +'<button class="cd-gh" id="cdGh"><span class="gi">'+GH(20,'#000')+'</span><span>'+(ok?T('اختيار مستودع','Choose repository'):T('تسجيل الدخول بحساب GitHub','Sign in with GitHub'))+'</span></button>'
   +(ok?'<button class="cd-sec danger" id="cdOff">'+T('قطع الاتصال','Disconnect')+'</button>':'<button class="cd-sec" id="cdTok">'+T('استخدام رمز وصول (متقدّم)','Use an access token (advanced)')+'</button>')
   +'<button class="cd-cancel" id="cdX">'+T('إلغاء ورجوع','Cancel & back')+'</button>','gh');
  w.querySelector('#cdX').onclick=w.close;
  const tm=w.querySelector('#cdTerms');if(tm)tm.onclick=()=>K.legal.open('terms');
  const off=w.querySelector('#cdOff');if(off)off.onclick=()=>{K.pref.set('gh_token','');K.pref.set('gh_sel','[]');P.repo='';w.close();K.code.paint()};
  w.querySelector('#cdGh').onclick=async()=>{
   if(ok){w.close();K.code.repos(after);return}
   w.close();K.code.deviceLogin(after)};
  const tk=w.querySelector('#cdTok');if(tk)tk.onclick=async()=>{
   if(ok){w.close();K.code.repos(after);return}
   const btn=w.querySelector('#cdGh'),lab=btn.querySelector('span:last-child'),orig=lab.textContent;
   const wait=t=>{btn.classList.add('wait');btn.querySelector('.gi').innerHTML='<i class="sp"></i>';lab.textContent=t};
   const rest=()=>{btn.classList.remove('wait');btn.querySelector('.gi').innerHTML=GH(20,'#000');lab.textContent=orig};
   wait(T('انتظر…','Please wait…'));
   await new Promise(r=>setTimeout(r,700));
   rest();
   const v=await K.ui.prompt({title:T('رمز وصول GitHub','GitHub access token'),hint:'ghp_…  /  github_pat_…',secret:true});
   if(!v)return;
   wait(T('جارٍ التحقق… انتظر','Verifying… please wait'));
   K.pref.set('gh_token',v);
   try{const u=await K.gh.user();K.toast(T('تم الاتصال: ','Connected: ')+u.login);w.close();K.code.paint();K.code.repos(after)}
   catch(e){rest();K.pref.set('gh_token','');K.ui.info(T('تعذّر الاتصال','Connection failed'),T('الرمز غير صالح أو لا يوجد اتصال بالإنترنت. أنشئ رمز وصول شخصي من GitHub ← Settings ← Developer settings ← Personal access tokens.','The token is invalid or you are offline. Create a personal access token in GitHub → Settings → Developer settings → Personal access tokens.'))}
  };
 },
 /* GitHub OAuth device flow: no password or token pasted; the user approves on github.com with a short code */
 async deviceLogin(after){
  let cid=K.pref.get('gh_client_id','').trim();
  if(!cid){
   const go=await K.ui.confirm(T('لتسجيل الدخول بحساب GitHub أنشئ مرة واحدة «OAuth App» من github.com/settings/developers وفعّل «Enable Device Flow»، ثم الصق الـClient ID هنا (معرّف عام وليس سرّاً). هل تريد فتح صفحة الإنشاء؟','To sign in with GitHub, create an “OAuth App” once at github.com/settings/developers, enable “Device Flow”, then paste its Client ID here (a public identifier, not a secret). Open the creation page?'));
   if(go)K.native.open('https://github.com/settings/applications/new');
   const v=await K.ui.prompt({title:'GitHub OAuth Client ID',hint:'Ov23li…'});if(!v)return;
   cid=v.trim();K.pref.set('gh_client_id',cid)}
  if(!/^(Ov23li|Iv1\.|Iv23li)/i.test(cid)){K.pref.set('gh_client_id','');K.ui.info(T('الـClient ID غير صحيح','Invalid Client ID'),T('يبدأ المعرّف الصحيح بـ Ov23li (من OAuth App في GitHub) — وليس مفتاح جوجل ولا رمز ghp_. أعد المحاولة.','A valid GitHub Client ID starts with Ov23li (from a GitHub OAuth App) — not a Google key or a ghp_ token. Try again.'));return}
  const hdr={'Accept':'application/json','Content-Type':'application/x-www-form-urlencoded'};
  const r0=await K.native.http('POST','https://github.com/login/device/code',hdr,'client_id='+encodeURIComponent(cid)+'&scope='+encodeURIComponent('repo read:user'));
  let d={};try{d=JSON.parse(r0.body)}catch(e){}
  if(!d.user_code){K.ui.info(T('تعذّر بدء تسجيل الدخول','Could not start sign-in'),(d.error_description||d.error||r0.err||'HTTP '+r0.code)+(d.error==='device_flow_disabled'?'\n'+T('فعّل «Enable Device Flow» في إعدادات تطبيق OAuth.','Enable “Device Flow” in the OAuth App settings.'):''));if(d.error==='incorrect_client_credentials')K.pref.set('gh_client_id','');return}
  let alive=true;
  const w=sheet('<div class="cd-art"><div class="screen"><span class="gm">'+GH(54,'#fff')+'</span></div></div><h3 class="cd-h">'+T('أدخل هذا الرمز في GitHub','Enter this code on GitHub')+'</h3>'
   +'<div class="cd-code" dir="ltr">'+esc(d.user_code)+'</div>'
   +'<p class="cd-p">'+T('اضغط «فتح GitHub» وأدخل الرمز ثم وافق على الصلاحيات. يتصل التطبيق تلقائياً بعد الموافقة.','Tap “Open GitHub”, enter the code and approve. The app connects automatically afterwards.')+'</p>'
   +'<div class="cd-wt"><i class="sp"></i><span id="cdSt">'+T('بانتظار موافقتك…','Waiting for your approval…')+'</span></div>'
   +'<button class="cd-gh" id="cdOpen"><span class="gi">'+GH(20,'#000')+'</span><span>'+T('فتح GitHub','Open GitHub')+'</span></button>'
   +'<button class="cd-sec" id="cdCp">'+T('نسخ الرمز','Copy code')+'</button><button class="cd-cancel" id="cdX">'+T('إلغاء','Cancel')+'</button>','gh');
  const stop=()=>{alive=false;w.close()};
  w.querySelector('#cdX').onclick=stop;w.querySelector('.cd-bd').onclick=stop;
  w.querySelector('#cdCp').onclick=()=>K.native.copy(d.user_code);
  w.querySelector('#cdOpen').onclick=()=>{K.native.copy(d.user_code);K.native.open(d.verification_uri||'https://github.com/login/device')};
  let iv=(d.interval||5)*1000;const t0=Date.now(),ttl=(d.expires_in||900)*1000;
  while(alive&&Date.now()-t0<ttl){
   await new Promise(x=>setTimeout(x,iv));if(!alive)return;
   const r=await K.native.http('POST','https://github.com/login/oauth/access_token',hdr,'client_id='+encodeURIComponent(cid)+'&device_code='+encodeURIComponent(d.device_code)+'&grant_type='+encodeURIComponent('urn:ietf:params:oauth:grant-type:device_code'));
   let t={};try{t=JSON.parse(r.body)}catch(e){}
   if(t.access_token){alive=false;K.pref.set('gh_token',t.access_token);K.pref.set('gh_scope',t.scope||'');w.close();
    try{const u=await K.gh.user();K.toast(T('تم تسجيل الدخول: ','Signed in: ')+u.login)}catch(e){K.toast(T('تم تسجيل الدخول','Signed in'))}
    K.code.paint();K.code.repos(after);return}
   if(t.error==='slow_down')iv+=5000;
   else if(t.error==='expired_token'||t.error==='access_denied'){alive=false;w.close();K.toast(t.error==='access_denied'?T('رُفضت الموافقة','Approval was denied'):T('انتهت صلاحية الرمز، أعد المحاولة','The code expired, try again'));return}
  }
  if(alive){w.close();K.toast(T('انتهت المهلة','Timed out'))}
 },
 repos(after){
  const w=sheet('<h3 class="cd-h">'+T('اختر مستودعاً','Choose a repository')+'</h3><div class="cd-rl" id="cdRl"><div class="sk"></div><div class="sk"></div><div class="sk"></div></div><button class="cd-cancel" id="cdX">'+T('رجوع','Back')+'</button>');
  w.querySelector('#cdX').onclick=w.close;
  K.gh.repos().then(rs=>{
   const l=w.querySelector('#cdRl');if(!rs.length){l.innerHTML='<div class="cd-p">'+T('لا توجد مستودعات','No repositories')+'</div>';return}
   l.innerHTML=rs.map((r,i)=>'<button class="cd-r'+(P.repo===r.full_name?' on':'')+'" data-i="'+i+'">'+GH(18)+'<div><b>'+esc(r.name)+'</b><small>'+esc(r.description||r.full_name)+'</small></div>'+(r.private?K.icon('lock',15):'')+'</button>').join('');
   l.querySelectorAll('.cd-r').forEach(b=>b.onclick=()=>{P.repo=rs[+b.dataset.i].full_name;w.close();K.code.paint();after&&after()});
  }).catch(()=>{w.querySelector('#cdRl').innerHTML='<div class="cd-p" style="color:var(--danger)">'+T('تعذّر جلب المستودعات','Could not load repositories')+'</div>'});
 },

 /* ---------- servers + diagnostics ---------- */
 server(after){
  const row=(k,ic,t,s)=>'<button class="cd-opt'+(P.env===k?' on':'')+'" data-k="'+k+'"><span class="oi">'+K.icon(ic,22)+'</span><div><b>'+t+'</b><small>'+s+'</small></div><i class="rd"></i></button>';
  const w=sheet('<h3 class="cd-h">'+T('الخادم','Server')+'</h3>'
   +row('cloud','cloud',envName('cloud'),T('يعمل عبر مزوّد الذكاء المتصل بالإنترنت','Runs through your connected online AI provider'))
   +row('default','server',envName('default'),T('الإعداد الافتراضي للتطبيق','The app’s default setup'))
   +'<button class="cd-opt diag" id="cdDg"><span class="oi" style="--c:#34c759">'+K.icon('activity',22)+'</span><div><b>'+T('تشخيصات','Diagnostics')+'</b><small>'+T('افحص الاتصال والمفاتيح','Check connection and keys')+'</small></div>'+K.icon('chevron-left',18)+'</button>'
   +'<button class="cd-cancel" id="cdX">'+T('إغلاق','Close')+'</button>');
  w.querySelector('#cdX').onclick=w.close;
  w.querySelectorAll('.cd-opt[data-k]').forEach(b=>b.onclick=()=>{P.env=b.dataset.k;w.close();K.code.paint();after&&after()});
  w.querySelector('#cdDg').onclick=()=>{w.close();K.code.diag()};
 },
 diag(){
  const chk=(ok,t,s)=>'<div class="cd-ck '+(ok?'ok':'no')+'"><span>'+K.icon(ok?'circle-check':'triangle-alert',20)+'</span><div><b>'+t+'</b><small>'+s+'</small></div></div>';
  const net=navigator.onLine!==false,key=K.native.hasKey(),gh=!!K.gh.tok();
  const w=sheet('<h3 class="cd-h">'+T('تشخيصات','Diagnostics')+'</h3>'
   +'<div class="cd-grp"><div class="gt">'+K.icon('cloud',18)+envName('cloud')+'</div>'
   +chk(net,T('الإنترنت','Internet'),net?T('متصل','Online'):T('غير متصل','Offline'))
   +chk(key,T('مفتاح API','API key'),key?T('موجود','Set'):T('غير موجود — أضفه من الإعدادات','Missing — add it in Settings'))
   +chk(gh,'GitHub',gh?T('متصل','Connected'):T('غير متصل','Not connected'))
   +'</div><div class="cd-grp"><div class="gt">'+K.icon('server',18)+envName('default')+'</div>'
   +chk(true,T('تخزين المحادثات','Chat storage'),T('على جهازك','On your device'))
   +chk(key,T('وضع الردود','Reply mode'),key?T('ردود حقيقية','Real replies'):T('وضع تجريبي (بدون مفتاح)','Demo mode (no key)'))
   +'</div><button class="cd-gh" id="cdTest"><span>'+T('اختبار الاتصال','Test connection')+'</span></button><button class="cd-cancel" id="cdX">'+T('رجوع','Back')+'</button>');
  w.querySelector('#cdX').onclick=()=>{w.close();K.code.server()};
  w.querySelector('#cdTest').onclick=async function(){
   if(!key){K.toast(T('أضف مفتاح API أولاً','Add an API key first'));return}
   this.querySelector('span').textContent='…';
   try{const r=await K.native.test();this.querySelector('span').textContent=(r&&r.ok===false)?T('فشل الاختبار','Test failed'):T('الاتصال يعمل ✓','Connection works ✓')}
   catch(e){this.querySelector('span').textContent=T('فشل الاختبار','Test failed')}
  };
 },

 /* ---------- new session ---------- */
 newSession(){
  const w=sheet('<h3 class="cd-h">'+T('جلسة جديدة','New session')+'</h3>'
   +'<textarea class="cd-ta" id="cdTa" rows="3" placeholder="'+T('ماذا تريد أن تبني؟ مثال: تطبيق مهام بواجهة داكنة','What do you want to build? e.g. a to-do app with a dark UI')+'"></textarea>'
   +'<div class="cd-chips"><button class="cd-chip" id="cdRepo"></button><button class="cd-chip" id="cdEnv"></button><button class="cd-chip" id="cdStk"></button></div>'
   +'<div class="cd-hint" id="cdHint"></div>'
   +'<button class="cd-go" id="cdGo">'+T('ابدأ الجلسة','Start session')+'</button>'
   +'<button class="cd-cancel" id="cdX">'+T('إلغاء العملية','Cancel')+'</button>','ns');
  const ta=w.querySelector('#cdTa'),go=w.querySelector('#cdGo');
  const upd=()=>{
   const gh=!!K.gh.tok(),repo=P.repo,txt=ta.value.trim();
   w.querySelector('#cdRepo').innerHTML='<span>'+(repo?esc(repo.split('/').pop()):T('اختر مستودعاً','Pick repo'))+'</span>'+GH(18);
   {const st=K.STACKS.find(x=>x[0]===K.pref.get('dev_stack','web'))||K.STACKS[0];w.querySelector('#cdStk').innerHTML='<span>'+esc(st[1].split(' — ')[0])+'</span>'+K.icon('code-xml',18)}
   w.querySelector('#cdEnv').innerHTML='<span>'+envShort(P.env)+'</span>'+K.icon('cloud',18);
   const ready=gh&&!!repo&&!!txt;go.disabled=!ready;
   w.querySelector('#cdHint').textContent=!gh?T('اربط GitHub أولاً لتفعيل الزر','Connect GitHub first to enable the button'):!repo?T('اختر مستودعاً لمتابعة الجلسة','Pick a repository to continue'):!txt?T('اكتب ما تريد بناءه','Describe what you want to build'):'';
  };
  ta.oninput=upd;upd();
  w.querySelector('#cdX').onclick=w.close;
  w.querySelector('#cdRepo').onclick=()=>{K.gh.tok()?K.code.repos(upd):K.code.github(upd)};
  w.querySelector('#cdEnv').onclick=()=>K.code.server(upd);
  w.querySelector('#cdStk').onclick=async()=>{const i=await K.ui.choose(T('لغة / تقنية المشروع','Project language / stack'),K.STACKS.map(x=>x[1]),Math.max(0,K.STACKS.findIndex(x=>x[0]===K.pref.get('dev_stack','web'))));if(i!=null){K.pref.set('dev_stack',K.STACKS[i][0]);upd()}};
  go.onclick=()=>{
   if(go.disabled)return;const txt=ta.value.trim(),repo=P.repo;
   const c=K.db.create();c.code=1;c.repo=repo;c.env=P.env;c.mode='apps';
   K.gh.setSel([repo]);
   w.close();K.code.close();
   const H=K.home;if(K.state.streaming)K.chat.stop();
   K.state.cur=c;K.pref.set('cur',c.id);K.chat.show(c);H.closeDrawer();
   $('ta').value=txt;K.chat.send();
  };
  setTimeout(()=>ta.focus(),350);
 }
};
document.addEventListener('click',e=>{const b=e.target.closest&&e.target.closest('[data-act=code]');if(b){K.home.closeSheet();K.code.open()}});
})();
