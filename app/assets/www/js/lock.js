/* App lock: 4-digit PIN (stored as a salted SHA-256 hash on the device) */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);
async function hash(p){const b=new TextEncoder().encode('karrar-lock:'+p);const h=await crypto.subtle.digest('SHA-256',b);return Array.from(new Uint8Array(h)).map(x=>x.toString(16).padStart(2,'0')).join('')}
K.lock={
 has(){return !!K.pref.get('pin_hash','')},
 /* mode: unlock | verify | set. cb(ok) is called when finished (verify/set). */
 show(mode,cb){
  const old=$('lock');if(old)old.remove();
  const d=document.createElement('div');d.id='lock';d.className='lock';
  const keys=[1,2,3,4,5,6,7,8,9,'',0,'<'];
  d.innerHTML='<div class="lk-logo">'+K.LOGO+'</div><div class="lk-t" id="lkT"></div><div class="lk-dots" id="lkD"><i></i><i></i><i></i><i></i></div><div class="lk-e" id="lkE"></div><div class="lk-pad">'+keys.map(k=>k===''?'<span></span>':'<button data-k="'+k+'">'+(k==='<'?K.icon('chevron-left',24):k)+'</button>').join('')+'</div>'+(mode==='unlock'?'':'<button class="lk-c" id="lkC">'+K.t('cancel')+'</button>');
  document.body.appendChild(d);
  let v='',first='',stage=mode==='set'?'new':'enter';
  const title=()=>{$('lkT').textContent=stage==='new'?K.t('l_new_pin'):stage==='confirm'?K.t('l_confirm_pin'):K.t('l_enter_pin')};title();
  const dots=()=>{[...$('lkD').children].forEach((x,i)=>x.classList.toggle('on',i<v.length))};
  const fail=m=>{$('lkE').textContent=m;$('lkD').classList.remove('shake');void $('lkD').offsetWidth;$('lkD').classList.add('shake');v='';setTimeout(dots,260);K.native.haptic()};
  const done=ok=>{d.classList.add('out');setTimeout(()=>d.remove(),260);cb&&cb(ok)};
  d.querySelectorAll('[data-k]').forEach(b=>b.onclick=async()=>{
   K.native.haptic();$('lkE').textContent='';const k=b.dataset.k;
   if(k==='<'){v=v.slice(0,-1);dots();return}
   if(v.length>=4)return;v+=k;dots();if(v.length<4)return;
   await new Promise(r=>setTimeout(r,120));
   if(stage==='enter'){const ok=(await hash(v))===K.pref.get('pin_hash','');if(ok)done(true);else fail(K.t('l_wrong_pin'))}
   else if(stage==='new'){first=v;v='';stage='confirm';title();dots()}
   else{if(v===first){K.pref.set('pin_hash',await hash(v));K.toast(K.t('l_pin_saved'));done(true)}else{stage='new';title();fail(K.t('l_pin_mismatch'))}}
  });
  const c=$('lkC');if(c)c.onclick=()=>done(false);
 }
};
})();
