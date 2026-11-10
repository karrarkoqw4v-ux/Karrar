/* Boot, routing, preference application, Android back button */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);
const A=K.app={};
/* status + navigation bar take the colour of whatever screen is showing (no visible bands) */
A.bars=function(){const d=document.documentElement,dark=d.dataset.theme==='dark',vo=K.voice&&K.voice.active;
 const c=vo?'#000000':(K.settings&&K.settings.depth()?(dark?'#1c1c1e':'#f2f2f7'):(dark?'#0f0f10':'#ffffff'));
 K.native.setBars(c,!dark&&!vo)};
A.apply=function(){
 const d=document.documentElement;
 d.dataset.font=K.pref.get('font_f','noto');d.dataset.weight=K.pref.get('font_w','1');d.lang=K.lang();d.dir=K.lang()==='ar'?'rtl':'ltr';let th=K.pref.get('theme','dark');if(th==='system')th=(window.matchMedia&&matchMedia('(prefers-color-scheme: light)').matches)?'light':'dark';d.dataset.theme=th==='light'?'light':'dark';d.dataset.motion=K.pref.bool('reduce_motion',false)?'off':'on';
 let sc=1;if(K.pref.bool('font_sys',false))sc=1;else sc=0.84+0.07*(+K.pref.get('font_i','2'));
 d.style.setProperty('--scale',sc.toFixed(3));
 K.native.setPref('lang',K.lang());
 A.bars();
 const onLogin=!$('login').classList.contains('hidden');
 const m=onLogin?0:(K.pref.bool('fx',false)?2:0);d.classList.toggle('fx-on',m>0);K.fx.start(m);
};
A.remount=function(){
 if(!$('home').classList.contains('hidden')){K.home.mount();K.home.start()}
 else K.login.mount();
};
A.toLogin=function(){while(K.settings.depth())K.settings.back();$('home').classList.add('hidden');$('login').classList.remove('hidden');K.login.mount();K.fx.start(0)};
A.toHome=function(){$('login').classList.add('hidden');$('home').classList.remove('hidden');K.db.load();K.home.mount();K.home.start();K.fx.start(K.pref.bool('fx',false)?2:0);A.apply()};
window.__back=function(){
 if(K.gsheet&&K.gsheet.closeIfOpen())return true;
 if(K.catalog&&K.catalog.closeIfOpen())return true;
 if(K.students&&K.students.close())return true;
 if(K.dialect&&K.dialect.closeIfOpen())return true;
 if(K.apps&&K.apps.close())return true;
 if(K.plans&&K.plans.isOpen()){K.plans.close();return true}
 if(K.enc&&K.enc.back())return true;
 if(K.scan&&(K.scan.runner&&K.scan.runner.active||K.scan.isOpen()||document.getElementById('scanres'))&&K.scan.back())return true;
 if(K.dictate&&K.dictate.active()){K.dictate.stop(false);return true}
 if(K.models&&K.models.closeIfOpen())return true;
 if(K.gallery&&K.gallery.closeIfOpen())return true;
 if(K.imggen&&K.imggen.closeSizes())return true;
 if(K.code&&K.code.back())return true;
 if(K.legal&&K.legal.close())return true;
 if(K.voice&&K.voice.active){K.voice.close();return true}
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
 K.splash.show();K.guard&&K.guard.boot();
 if(K.lock.has())K.lock.show('unlock');
 let hid=0;document.addEventListener('visibilitychange',()=>{if(document.hidden)hid=Date.now();else if(K.lock.has()&&hid&&Date.now()-hid>15000)K.lock.show('unlock')});
};
document.addEventListener('DOMContentLoaded',()=>{A.boot()});
})();
