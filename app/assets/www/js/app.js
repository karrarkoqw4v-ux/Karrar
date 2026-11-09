/* Boot, routing, preference application, Android back button */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);
const A=K.app={};
A.apply=function(){
 const d=document.documentElement;
 d.lang=K.lang();d.dir=K.lang()==='ar'?'rtl':'ltr';d.dataset.theme=K.pref.get('theme','dark')==='light'?'light':'dark';
 let sc=1;if(K.pref.bool('font_sys',false))sc=1;else sc=0.84+0.07*(+K.pref.get('font_i','2'));
 d.style.setProperty('--scale',sc.toFixed(3));
 K.native.setPref('lang',K.lang());
 K.native.setBars(d.dataset.theme==='dark'?'#0f0f10':'#ffffff',d.dataset.theme!=='dark');
 const onLogin=!$('login').classList.contains('hidden');
 const m=onLogin?2:(K.pref.bool('fx',false)?2:0);d.classList.toggle('fx-on',m>0);K.fx.start(m);
};
A.remount=function(){
 if(!$('home').classList.contains('hidden')){K.home.mount();K.home.start()}
 else K.login.mount();
};
A.toLogin=function(){while(K.settings.depth())K.settings.back();$('home').classList.add('hidden');$('login').classList.remove('hidden');K.login.signup=false;K.login.mount();K.fx.start(2)};
A.toHome=function(){$('login').classList.add('hidden');$('home').classList.remove('hidden');K.db.load();K.home.mount();K.home.start();K.fx.start(K.pref.bool('fx',false)?2:0);A.apply()};
window.__back=function(){
 if(K.ui.hasOpen()){K.ui.close();return true}
 if(document.getElementById('ctx')){K.home.closeCtx();return true}
 if(K.settings.depth()){K.settings.back();return true}
 if(!$('home').classList.contains('hidden')){
  if(K.home.sheetOpen){K.home.closeSheet();return true}
  if(K.home.selMode){K.home.exitSel();return true}
  if(K.home.drawerOpen){K.home.closeDrawer();return true}}
 return false;
};
A.boot=function(){
 const def=K.native.defaults();Object.keys(def).forEach(k=>{if(K.native.getPref(k,null)===null)K.native.setPref(k,def[k])});
 A.apply();
 if(K.pref.bool('logged',false))A.toHome();else A.toLogin();
 setTimeout(()=>{const s=document.getElementById('splash');if(s)s.remove()},60);
};
document.addEventListener('DOMContentLoaded',()=>{A.boot()});
})();
