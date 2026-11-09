/* Login / sign-up screen (white Google button, aurora + falling characters, typewriter title) */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);
const L=K.login={signup:false,busy:false};
L.mount=function(){
 const root=$('login');root.innerHTML='<div class="login"><div class="inner" id="lin"></div></div>';L.render(true)};
L.render=function(first){
 const su=L.signup;const inner=$('lin');
 inner.innerHTML=
 '<button class="lang-btn press" id="lLang">'+K.t('l_lang')+'</button>'
 +'<div class="logo-wrap"><div class="glow"></div>'+K.LOGO+'</div>'
 +'<h1 class="grad-text" id="lTitle"></h1><div class="sub" id="lSub">'+K.t(su?'l_sub_up':'l_sub')+'</div>'
 +'<button class="gbtn" id="lG">'+K.G_SVG.replace('width="26" height="26"','width="22" height="22"')+'<span>'+K.t('l_google')+'</span></button>'
 +'<div class="or">'+K.t('l_or')+'</div><div id="lForm"></div><div class="errmsg" id="lErr"></div>'
 +'<button class="pbtn" id="lGo"><span>'+K.t(su?'l_signup':'l_login')+'</span></button>'
 +'<button class="lnk" id="lSw">'+K.t(su?'l_to_login':'l_to_signup')+'</button><button class="gst" id="lGuest">'+K.t('l_guest')+'</button>';
 const fields=[];if(su)fields.push(['n','user',K.t('l_name'),'text']);fields.push(['e','mail',K.t('l_email'),'email']);fields.push(['p','key',K.t('l_pass'),'password']);if(su)fields.push(['p2','check',K.t('l_pass2'),'password']);
 $('lForm').innerHTML=fields.map((f,i)=>'<label class="fld" data-f="'+f[0]+'" style="animation-delay:'+(i*70)+'ms">'+K.icon(f[1],21)+'<input name="'+f[0]+'" type="'+f[3]+'" placeholder="'+K.t('l_'+({n:'name',e:'email',p:'pass',p2:'pass2'})[f[0]])+'" autocomplete="off" '+(f[0]==='e'?'inputmode="email" dir="ltr"':'')+'>'+(f[3]==='password'?'<button type="button" class="eye">'+K.t('l_show')+'</button>':'')+'</label>').join('');
 inner.querySelectorAll('.eye').forEach(b=>b.onclick=()=>{const i=b.parentNode.querySelector('input');const show=i.type==='password';i.type=show?'text':'password';b.textContent=K.t(show?'l_hide':'l_show')});
 K.fx.type($('lTitle'),K.t(su?'l_signup':'l_welcome'),55);
 $('lLang').onclick=()=>{K.pref.set('lang',K.lang()==='ar'?'en':'ar');K.app.apply();L.render()};
 $('lSw').onclick=()=>{L.signup=!L.signup;L.render()};
 $('lGuest').onclick=()=>{K.native.guest();K.app.toHome()};
 $('lGo').onclick=()=>L.submit();
 $('lG').onclick=()=>L.google();
 inner.querySelectorAll('input').forEach(i=>i.addEventListener('keydown',e=>{if(e.key==='Enter')L.submit()}));
 K.emojifyEl(inner);
};
const val=n=>{const e=document.querySelector('#lForm input[name='+n+']');return e?e.value:''};
L.error=function(key){const m=$('lErr');m.textContent=K.t(key);const f=$('lForm');f.classList.remove('shake');void f.offsetWidth;f.classList.add('shake')};
L.submit=function(){
 if(L.busy)return;$('lErr').textContent='';
 const r=L.signup?K.native.signup(val('n'),val('e'),val('p'),val('p2')):K.native.login(val('e'),val('p'));
 if(r&&r.err){L.error(r.err);return}K.app.toHome();
};
L.google=function(){
 if(L.busy)return;L.busy=true;$('lErr').textContent='';
 K.native.google().then(function(r){L.busy=false;$('lSub').textContent=K.t(L.signup?'l_sub_up':'l_sub');
  if(!r||r.err){if(r&&r.err!=='cancel')$('lErr').textContent=r.err;return}
  K.native.setPref('name',r.name||r.email);K.native.setPref('user_email',(r.email||'').toLowerCase());K.native.setPref('user_google','1');K.native.setPref('logged','1');K.app.toHome()});
};
})();
