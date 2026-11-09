/* Login flow: welcome -> password / sign-up / reset (local accounts) */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);
const L=K.login={view:'welcome',agree:true,busy:false,code:'',email:''};
const esc=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
L.mount=function(){
 $('login').innerHTML='<div class="lg"><div class="lg-top"><button class="lg-back" id="lgBack"></button><button class="lg-cu" id="lgCu"></button></div><div class="lg-body" id="lgBody"></div></div>';
 $('lgCu').textContent=K.t('l_contact');$('lgCu').onclick=()=>K.legal.open('contact');
 $('lgBack').innerHTML=K.icon('chevron-right',24);
 $('lgBack').onclick=()=>L.go({reset2:'reset1',reset1:'password',signup:'password',password:'welcome'}[L.view]||'welcome');
 L.go('welcome');
};
function inp(id,type,ph,extra){return '<label class="linp"><input id="'+id+'" type="'+type+'" placeholder="'+esc(ph)+'" autocomplete="off" '+(type==='email'?'inputmode="email" dir="ltr"':'')+'>'+(extra||'')+'</label>'}
const eye='<button type="button" class="leye">'+''+'</button>';
function title(t){return '<h1 class="lg-t">'+t+'</h1>'}
function err(){return '<div class="lg-err" id="lgErr"></div>'}
function val(id){const e=$(id);return e?e.value:''}
function fail(k){const e=$('lgErr');if(e)e.textContent=K.t(k)==k?k:K.t(k);const b=$('lgBody');b.classList.remove('shake');void b.offsetWidth;b.classList.add('shake')}
L.go=function(v){
 L.view=v;const b=$('lgBody');$('lgBack').style.visibility=v==='welcome'?'hidden':'visible';
 const E=K.icon('eye',22);let h='';
 if(v==='welcome'){
  h='<div class="lg-logo"><div class="lg-mk">'+K.LOGO+'</div><div class="lg-wm">'+K.t('app')+'</div><div class="lg-tag">'+K.t('l_tagline')+' ✨</div></div>'
   +'<div class="lg-btns"><button class="lbtn" id="bG">'+K.G_SVG.replace('width="26" height="26"','width="22" height="22"')+'<span>'+K.t('l_cont_google')+'</span></button>'
   +'<button class="lbtn" id="bP">'+K.icon('lock',22)+'<span>'+K.t('l_cont_pw')+'</span></button>'
   +'<button class="lbtn" id="bS"><span>'+K.t('l_cont_guest')+'</span></button>'
   +'<div class="lg-terms" id="terms"><span class="ck '+(L.agree?'on':'')+'" id="ck">'+K.icon('check',15,3.4)+'</span><span>'+K.t('l_terms_a')+' <b id="tT">'+K.t('l_terms')+'</b> '+K.t('l_and')+' <b id="tP">'+K.t('l_privacy')+'</b> '+K.t('l_terms_z',K.t('app'))+'</span></div></div>'
   +'<button class="lg-lang" id="lgLang">'+K.t('l_lang')+'</button>';
 }else if(v==='password'){
  h=title(K.t('l_pw_title'))+inp('fE','email',K.t('l_email'))+inp('fP','password',K.t('l_pass'),'<button type="button" class="leye" data-eye>'+E+'</button>')
   +'<button class="lg-link" id="fg">'+K.t('l_forgot')+'</button>'+err()+'<button class="lbtn p" id="go">'+K.t('l_login')+'</button><button class="lg-alt" id="alt">'+K.t('l_to_signup')+'</button>';
 }else if(v==='signup'){
  h=title(K.t('l_signup'))+inp('fN','text',K.t('l_name'))+inp('fE','email',K.t('l_email'))+inp('fP','password',K.t('l_pass'),'<button type="button" class="leye" data-eye>'+E+'</button>')+inp('fP2','password',K.t('l_pass2'))
   +err()+'<button class="lbtn p" id="go">'+K.t('l_signup')+'</button><button class="lg-alt" id="alt">'+K.t('l_to_login')+'</button>';
 }else if(v==='reset1'){
  h=title(K.t('l_reset_title'))+inp('fE','email',K.t('l_email'))+inp('fC','text',K.t('l_code'),'<span class="lsep"></span><button type="button" class="lsend" id="send">'+K.t('l_send_code')+'</button>')+err()+'<button class="lbtn p" id="go">'+K.t('l_continue')+'</button>';
 }else{
  h=title(K.t('l_newpass_title'))+inp('fP','password',K.t('l_pass'),'<button type="button" class="leye" data-eye>'+E+'</button>')+inp('fP2','password',K.t('l_pass2'))+err()+'<button class="lbtn p" id="go">'+K.t('l_save')+'</button>';
 }
 b.className='lg-body '+v;b.innerHTML=h;K.emojifyEl(b);
 b.querySelectorAll('input').forEach(i=>i.addEventListener('focus',()=>setTimeout(()=>{try{i.scrollIntoView({block:'center',behavior:'smooth'})}catch(e){}},320)));
 b.querySelectorAll('.linp,.lbtn,.lg-link,.lg-alt,.lg-title,h1').forEach((e,i)=>{e.style.animation='fadeUp .45s '+(i*55)+'ms var(--ease) both'});
 b.querySelectorAll('[data-eye]').forEach(x=>x.onclick=()=>{const i=x.parentNode.querySelector('input');const s=i.type==='password';i.type=s?'text':'password';x.innerHTML=K.icon(s?'eye-off':'eye',22)});
 b.querySelectorAll('input').forEach(i=>i.addEventListener('keydown',e=>{if(e.key==='Enter'){const g=$('go');g&&g.click()}}));
 bind(v);
};
function needAgree(){if(L.agree)return false;const t=$('terms');t.classList.remove('shake');void t.offsetWidth;t.classList.add('shake');K.toast(K.t('l_agree_first'));return true}
function bind(v){
 if(v==='welcome'){
  $('ck').onclick=()=>{L.agree=!L.agree;$('ck').classList.toggle('on',L.agree)};
  $('tT').onclick=e=>{e.stopPropagation();K.legal.open('terms')};$('tP').onclick=e=>{e.stopPropagation();K.legal.open('privacy')};
  $('bP').onclick=()=>{if(!needAgree())L.go('password')};
  $('bS').onclick=()=>{if(needAgree())return;K.native.guest();K.app.toHome()};
  $('bG').onclick=()=>{if(needAgree())return;L.google()};
  $('lgLang').onclick=()=>{K.pref.set('lang',K.lang()==='ar'?'en':'ar');K.app.apply();L.mount()};
 }else if(v==='password'){
  $('go').onclick=()=>{const r=K.native.login(val('fE'),val('fP'));if(r&&r.err)fail(r.err);else K.app.toHome()};
  $('alt').onclick=()=>L.go('signup');$('fg').onclick=()=>L.go('reset1');
 }else if(v==='signup'){
  $('go').onclick=()=>{const r=K.native.signup(val('fN'),val('fE'),val('fP'),val('fP2'));if(r&&r.err)fail(r.err);else K.app.toHome()};
  $('alt').onclick=()=>L.go('password');
 }else if(v==='reset1'){
  $('send').onclick=()=>{const e=val('fE').trim();if(!e){fail('e_email');return}if(!K.native.hasAccount(e)){fail('e_nouser');return}
   L.email=e;L.code=String(Math.floor(100000+Math.random()*900000));$('lgErr').textContent='';K.ui.info(K.t('l_send_code'),K.t('l_code_demo',L.code))};
  $('go').onclick=()=>{if(!L.code){fail('e_email');return}if(val('fC').trim()!==L.code){fail('l_code_bad');return}L.go('reset2')};
 }else{
  $('go').onclick=()=>{const r=K.native.resetPassword(L.email,val('fP'),val('fP2'));if(r&&r.err)fail(r.err);else{L.code='';K.toast(K.t('l_reset_ok'));L.go('password')}};
 }
}
L.google=async function(){
 if(L.busy)return;L.busy=true;
 const btn=$('bG'),ic=btn.querySelector('svg');if(ic)ic.style.display='none';const sp=document.createElement('i');sp.className='gsp';btn.insertBefore(sp,btn.firstChild);btn.classList.add('busy');
 await new Promise(r=>setTimeout(r,450));
 const r=await K.gsheet.pick();
 sp.remove();if(ic)ic.style.display='';btn.classList.remove('busy');L.busy=false;
 if(!r||r.err){K.gsheet.done();if(r&&r.err&&r.err!=='cancel')K.toast(r.err);return}
 K.native.setPref('name',r.name||r.email);K.native.setPref('user_email',(r.email||'').toLowerCase());K.native.setPref('user_google','1');K.native.setPref('logged','1');K.app.toHome();K.gsheet.done();
};
})();
