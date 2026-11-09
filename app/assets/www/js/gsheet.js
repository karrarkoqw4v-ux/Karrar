/* "Choose an account" sheet for Google sign-in, styled after Google's own sheet */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const COL=['#2e7d32','#c2185b','#6a1b9a','#ef6c00','#00838f','#4527a0','#ad1457','#1565c0','#558b2f','#d84315'];
const nm=e=>{const l=(e.split('@')[0]||e).replace(/[0-9]+/g,' ').replace(/[._-]+/g,' ').trim();return l?l.split(/\s+/).map(w=>w[0].toUpperCase()+w.slice(1)).join(' '):e};
const hue=e=>{let h=0;for(const c of e)h=(h*31+c.charCodeAt(0))>>>0;return COL[h%COL.length]};
const known=()=>{try{return JSON.parse(K.pref.get('g_known','[]'))}catch(e){return[]}};
const remember=a=>{const k=known().filter(x=>x.email!==a.email);k.unshift({name:a.name,email:a.email});K.pref.set('g_known',JSON.stringify(k.slice(0,6)))};
const ADD='<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 21v-1a6 6 0 0 0-12 0v1"/><circle cx="9" cy="8" r="4"/><path d="M19 8v6M16 11h6"/></svg>';
K.gsheet={
 /* resolves {name,email} or {err:'cancel'}; keeps a black loading screen up after a choice until done() */
 async pick(){
  if(K.native.real){ /* real device: Google's own account sheet lists the accounts that are really on the phone */
   const r=await K.native.google();
   if(!r||r.err)return r||{err:'cancel'};
   K.gsheet.loading();await new Promise(x=>setTimeout(x,1100));return r}
  let vis=[];try{vis=K.native.googleAccounts()||[]}catch(e){}
  const list=[];[...vis,...known()].forEach(a=>{if(a.email&&!list.some(x=>x.email.toLowerCase()===a.email.toLowerCase()))list.push({name:a.name||nm(a.email),email:a.email})});
  return new Promise(res=>{
   const w=document.createElement('div');w.id='gsheet';w.className='gs';w.dir=K.lang()==='ar'?'rtl':'ltr';
   w.innerHTML='<div class="gs-bd"></div><div class="gs-sh"><i class="gs-grab"></i><div class="gs-ic">'+K.G_SVG.replace(/width="\d+" height="\d+"/,'width="24" height="24"')+'</div><div class="gs-t">'+T('اختيار حساب','Choose an account')+'</div><div class="gs-s">'+T('للمتابعة إلى '+K.t('app'),'to continue to '+K.t('app_latin'))+'</div>'
    +'<div class="gs-list">'+list.map((a,i)=>'<button class="gs-row" data-i="'+i+'" style="--i:'+i+'"><div class="tx"><b>'+esc(a.name)+'</b><small dir="ltr">'+esc(a.email)+'</small></div><span class="av" style="background:'+hue(a.email)+'">'+esc((a.name[0]||'?').toUpperCase())+'</span></button>').join('')
    +'<button class="gs-row add" id="gsAdd" style="--i:'+list.length+'"><div class="tx"><b>'+T('إضافة حساب آخر','Add another account')+'</b></div><span class="av plus">'+ADD+'</span></button></div></div>';
   document.body.appendChild(w);
   let fin=false;
   const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),260)};
   const choose=a=>{if(fin)return;fin=true;remember(a);close();K.gsheet.loading();setTimeout(()=>res(a),1400)};
   w.querySelector('.gs-bd').onclick=()=>{if(fin)return;fin=true;close();res({err:'cancel'})};
   K.gsheet.cancel=()=>{if(fin)return false;fin=true;close();res({err:'cancel'});return true};
   w.querySelectorAll('.gs-row[data-i]').forEach(b=>b.onclick=()=>{b.classList.add('tap');setTimeout(()=>choose(list[+b.dataset.i]),120)});
   w.querySelector('#gsAdd').onclick=async()=>{
    if(fin)return;fin=true;close();
    const r=await K.native.google();
    if(!r||r.err){res(r||{err:'cancel'});return}
    remember({name:r.name||nm(r.email),email:r.email});K.gsheet.loading();setTimeout(()=>res(r),1200)};
  });
 },
 loading(){if($('gload'))return;const l=document.createElement('div');l.id='gload';l.className='gload';l.innerHTML='<i></i>';document.body.appendChild(l)},
 done(){const l=$('gload');if(l){l.classList.add('out');setTimeout(()=>l.remove(),350)}},
 closeIfOpen(){return K.gsheet.cancel?K.gsheet.cancel():false}
};
})();
