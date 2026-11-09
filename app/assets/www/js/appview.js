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
function prep(code){
 let c=code;if(!/<meta[^>]+viewport/i.test(c)){if(/<head[^>]*>/i.test(c))c=c.replace(/<head[^>]*>/i,m=>m+'<meta name="viewport" content="width=device-width,initial-scale=1">');else c='<meta name="viewport" content="width=device-width,initial-scale=1">'+c}
 return c}
K.apps={
 card(name,code){
  name=name.replace(/[<>]/g,'').slice(0,60)||'App';const id='a'+(++n);reg[id]={name,code};
  const c=hue(name);
  return '<div class="appcard" style="--ac:'+c+'"><span class="ai-tile">'+esc((name.match(/[\p{L}\p{N}]/u)||['A'])[0].toUpperCase())+'</span><div class="an"><b>'+esc(name)+'</b><small>'+T('تطبيق ويب · HTML','Web app · HTML')+' · '+(new Blob([code]).size/1024).toFixed(1)+' KB</small></div>'
   +'<div class="ab"><button class="view" data-appv="'+id+'">'+K.icon('smartphone',17)+'<span>'+T('عرض','Preview')+'</span></button><button data-apps="'+id+'" title="'+T('حفظ','Save')+'">'+K.icon('arrow-up',17).replace('<svg ','<svg style="transform:rotate(180deg)" ')+'</button><button data-appc="'+id+'" title="'+T('نسخ','Copy')+'">'+K.icon('copy',17)+'</button></div></div>'},
 async save(id){const a=reg[id];if(!a)return;const name=a.name.replace(/[\\/:*?"<>|\s]+/g,'-').toLowerCase()+'.html';
  const b64=btoa(unescape(encodeURIComponent(a.code)));const r=await K.native.saveFile(name,'text/html',b64);K.toast(r&&String(r).indexOf('err')===0?T('تعذّر الحفظ','Could not save'):T('تم الحفظ في التنزيلات/Bawsala','Saved to Downloads/Bawsala'))},
 copy(id){const a=reg[id];if(a)K.native.copy(a.code)},
 close(){const d=$('apv');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true},
 open(id){
  const a=reg[id];if(!a)return;const old=$('apv');if(old)old.remove();
  const st={dev:'phone',land:false,key:0};
  const d=document.createElement('div');d.id='apv';d.className='apv';d.dir=K.lang()==='ar'?'rtl':'ltr';
  d.innerHTML='<div class="apv-top"><button class="apv-ib" id="apX">'+K.icon('x',22,2.4)+'</button><div class="apv-t"><b>'+esc(a.name)+'</b><small>'+T('معاينة كاملة على جهاز','Full device preview')+'</small></div><button class="apv-ib" id="apS">'+K.icon('arrow-up',20).replace('<svg ','<svg style="transform:rotate(180deg)" ')+'</button></div>'
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
  $('apX').onclick=()=>K.apps.close();$('apS').onclick=()=>K.apps.save(id);
  d.querySelectorAll('#apDevs [data-d]').forEach(b=>b.onclick=()=>{st.dev=b.dataset.d;st.land=false;paint()});
  $('apRot').onclick=()=>{st.land=!st.land;paint()};$('apRe').onclick=()=>{const f=$('apFr');if(f)f.srcdoc=prep(a.code)};
  window.addEventListener('resize',()=>{if($('apv'))paint()});
  setTimeout(paint,30);setTimeout(paint,350)}
};
})();
