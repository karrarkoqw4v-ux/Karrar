/* Apps the assistant builds: ```app:Name blocks become a card with "View" (opens a full device preview), Save and Copy */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const reg={};let n=0;
const COL=['#4d6bfe','#8b5cf6','#ec4899','#f59e0b','#10b981','#0ea5e9','#ef4444','#14b8a6'];
const hue=s=>{let h=0;for(const c of s)h=(h*31+c.charCodeAt(0))>>>0;return COL[h%COL.length]};
const DEV={
 phone:{n:T('هاتف','Phone'),w:390,h:844,r:46,bz:11,ic:'smartphone'},
 tablet:{n:T('تابلت','Tablet'),w:820,h:1180,r:34,bz:16,ic:'file'},
 desk:{n:T('حاسوب','Desktop'),w:1280,h:800,r:14,bz:0,ic:'globe'}};
function parseFiles(code){const parts=code.split(/^=== *(.+?) *===\s*$/m);if(parts.length<3)return null;const f={};for(let i=1;i<parts.length;i+=2)f[parts[i].trim().replace(/^\/+/,'')]=(parts[i+1]||'').replace(/^\n/,'');return f}
function norm(p){const o=[];p.split('/').forEach(x=>{if(x==='..')o.pop();else if(x&&x!=='.')o.push(x)});return o.join('/')}
function inlineFiles(f){const entry=f['index.html']?'index.html':Object.keys(f).find(k=>/\.html?$/i.test(k));if(!entry)return null;const base=entry.replace(/[^/]*$/,'');let h=f[entry];
 h=h.replace(/<link[^>]+href=["']([^"']+\.css)["'][^>]*>/gi,(m,p)=>{const k=norm(base+p);return f[k]!=null?'<style>'+f[k]+'</style>':m});
 h=h.replace(/<script[^>]+src=["']([^"']+\.js)["'][^>]*><\/script>/gi,(m,p)=>{const k=norm(base+p);return f[k]!=null?'<script>'+f[k].replace(/<\/script/gi,'<\\/script')+'<\/script>':m});return h}
function html(code){const f=parseFiles(code);if(f){const h=inlineFiles(f);if(h)return h}return code}
function prep(code0){const code=html(code0);
 let c=code;if(!/<meta[^>]+viewport/i.test(c)){if(/<head[^>]*>/i.test(c))c=c.replace(/<head[^>]*>/i,m=>m+'<meta name="viewport" content="width=device-width,initial-scale=1">');else c='<meta name="viewport" content="width=device-width,initial-scale=1">'+c}
 return c}
K.apps={
 card(name,code){
  name=name.replace(/[<>]/g,'').slice(0,60)||'App';const id='a'+(++n);reg[id]={name,code};const pf=parseFiles(code),nf=pf?Object.keys(pf).length:1;const web=!pf||Object.keys(pf).some(k=>/\.html?$/i.test(k));const lang=pf?T('مشروع','Project'):'';
  const c=hue(name);
  return '<div class="appcard" style="--ac:'+c+'"><span class="ai-tile">'+esc((name.match(/[\p{L}\p{N}]/u)||['A'])[0].toUpperCase())+'</span><div class="an"><b>'+esc(name)+'</b><small>'+(nf>1?T('مشروع · '+nf+' ملفات','Project · '+nf+' files')+(web?'':' · '+T('بدون معاينة','no preview')):T('تطبيق ويب · HTML','Web app · HTML'))+' · '+(new Blob([code]).size/1024).toFixed(1)+' KB</small></div>'
   +'<div class="ab">'+(web?'<button class="view" data-appv="'+id+'">'+K.icon('smartphone',17)+'<span>'+T('عرض','Preview')+'</span></button>':'')+'<button data-apps="'+id+'" title="'+T('حفظ','Save')+'">'+K.icon('arrow-up',17).replace('<svg ','<svg style="transform:rotate(180deg)" ')+'</button><button data-appc="'+id+'" title="'+T('نسخ','Copy')+'">'+K.icon('copy',17)+'</button>'+(web?'<button data-appb="'+id+'" title="'+T('فتح في المتصفح','Open in browser')+'">'+K.icon('globe',17)+'</button>':'')+'<button data-appg="'+id+'" title="GitHub">'+K.icon('git-branch',17)+'</button></div></div>'},
 slug(a){return a.name.replace(/[\\/:*?"<>|\s]+/g,'-').toLowerCase()||'app'},
 async save(id){const a=reg[id];if(!a)return;const multi=!!parseFiles(a.code);
  const r=multi?await K.files.saveNamed(K.apps.slug(a)+'.zip',a.code):await K.files.saveNamed(K.apps.slug(a)+'.html',a.code);
  if(r&&String(r).indexOf('err')===0)K.toast(T('تعذّر الحفظ','Could not save'))},
 async browser(id){const a=reg[id];if(!a)return;const b64=btoa(unescape(encodeURIComponent(prep(a.code))));const r=await K.native.saveFile(K.apps.slug(a)+'.html','text/html',b64);
  if(r&&String(r).indexOf('ok:')===0){const u=String(r).slice(3).split('|')[1];if(u)K.native.openUri(u,'text/html')}else K.toast(T('تعذّر الفتح','Could not open'))},
 async push(id){const a=reg[id];if(!a)return;
  if(!K.gh.tok()){K.toast(T('سجّل الدخول بـ GitHub أولاً','Sign in with GitHub first'));K.code.github();return}
  let repo=K.pref.get('code_repo','');if(!repo){K.toast(T('اختر مستودعاً أولاً','Pick a repository first'));K.code.repos(()=>K.apps.push(id));return}
  const f=parseFiles(a.code)||{'index.html':a.code},dir='apps/'+K.apps.slug(a)+'/',files={};Object.keys(f).forEach(k=>files[dir+k]=f[k]);
  K.toast(T('جارٍ الرفع إلى GitHub…','Pushing to GitHub…'));
  try{const done=await K.gh.push(repo,files,'Bawsala: '+a.name);K.snack(T('تم الرفع: '+done.length+' ملف إلى '+repo,'Pushed '+done.length+' file(s) to '+repo),T('فتح','Open'),()=>K.native.open('https://github.com/'+repo+'/tree/HEAD/apps/'+K.apps.slug(a)))}
  catch(e){K.ui.info(T('تعذّر الرفع','Push failed'),String(e.message)+'\n'+T('تأكد أن الحساب منح صلاحية repo وأن المستودع موجود.','Make sure the account granted the repo scope and the repository exists.'))}},
 copy(id){const a=reg[id];if(a)K.native.copy(a.code)},
 close(){const d=$('apv');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true},
 open(id){
  const a=reg[id];if(!a)return;const old=$('apv');if(old)old.remove();
  const st={dev:'phone',land:false,key:0};
  const d=document.createElement('div');d.id='apv';d.className='apv';d.dir=K.lang()==='ar'?'rtl':'ltr';
  d.innerHTML='<div class="apv-top"><button class="apv-ib" id="apX">'+K.icon('x',22,2.4)+'</button><div class="apv-t"><b>'+esc(a.name)+'</b><small>'+T('معاينة كاملة على جهاز','Full device preview')+'</small></div><button class="apv-ib" id="apB" title="browser">'+K.icon('globe',20)+'</button><button class="apv-ib" id="apS">'+K.icon('arrow-up',20).replace('<svg ','<svg style="transform:rotate(180deg)" ')+'</button></div>'
   +'<div class="apv-devs" id="apDevs">'+Object.keys(DEV).map(k=>'<button data-d="'+k+'">'+K.icon(DEV[k].ic,16)+'<span>'+DEV[k].n+'</span></button>').join('')+'<i class="sp"></i><button id="apRot" title="'+T('تدوير','Rotate')+'">'+K.icon('smartphone',16).replace('<svg ','<svg style="transform:rotate(90deg)" ')+'</button><button id="apRe" title="'+T('إعادة تحميل','Reload')+'">'+K.icon('refresh-cw',16)+'<span>'+T('تشغيل','Run')+'</span></button></div>'
   +'<div class="apv-stage" id="apStage"><div class="apv-dev" id="apDev"></div></div>';
  document.body.appendChild(d);
  const paint=()=>{
   const D=DEV[st.dev],W=st.land&&st.dev!=='desk'?D.h:D.w,H=st.land&&st.dev!=='desk'?D.w:D.h,bz=D.bz,sw=W+bz*2,sh=H+bz*2+(st.dev==='desk'?44:0);
   const stg=$('apStage'),k=Math.min((stg.clientWidth-16)/sw,(stg.clientHeight-16)/sh,1.2);
   const dev=$('apDev');dev.className='apv-dev '+st.dev+(st.land?' land':'');dev.style.cssText='width:'+sw+'px;height:'+sh+'px;border-radius:'+(D.r+bz)+'px;left:50%;top:50%;transform:translate(-50%,-50%) scale('+k.toFixed(3)+');padding:'+bz+'px';
   const chrome=st.dev==='desk'?'<div class="chrome"><i></i><i></i><i></i><span>'+esc(a.name.toLowerCase().replace(/\s+/g,'-'))+'.app</span></div>':'';
   const phone=st.dev==='phone'&&!st.land?'<div class="sbar"><b>9:41</b><span class="hole"></span><em><svg viewBox="0 0 24 24" width="14" height="14" fill="#fff"><path d="M2 20h3v-4H2zM7 20h3v-8H7zM12 20h3V8h-3zM17 20h3V3h-3z"/></svg><svg viewBox="0 0 24 24" width="22" height="14"><rect x="2" y="6" width="17" height="11" rx="3" fill="none" stroke="#fff" stroke-width="1.6"/><rect x="4" y="8" width="11" height="7" rx="1.5" fill="#fff"/><rect x="20" y="9.5" width="2" height="4" rx="1" fill="#fff"/></svg></em></div>':'';
   dev.innerHTML=chrome+'<div class="scr" style="border-radius:'+D.r+'px">'+phone+'<iframe id="apFr" sandbox="allow-scripts allow-forms allow-modals allow-pointer-lock" referrerpolicy="no-referrer"></iframe>'+(st.dev==='phone'&&!st.land?'<i class="home"></i>':'')+'</div>';
   $('apFr').srcdoc=prep(a.code);
   d.querySelectorAll('#apDevs [data-d]').forEach(b=>b.classList.toggle('on',b.dataset.d===st.dev));$('apRot').style.display=st.dev==='desk'?'none':''};
  $('apX').onclick=()=>K.apps.close();$('apS').onclick=()=>K.apps.save(id);$('apB').onclick=()=>K.apps.browser(id);
  d.querySelectorAll('#apDevs [data-d]').forEach(b=>b.onclick=()=>{st.dev=b.dataset.d;st.land=false;paint()});
  $('apRot').onclick=()=>{st.land=!st.land;paint()};$('apRe').onclick=()=>{const f=$('apFr');if(f)f.srcdoc=prep(a.code)};
  window.addEventListener('resize',()=>{if($('apv'))paint()});
  setTimeout(paint,30);setTimeout(paint,350)}
};
})();
