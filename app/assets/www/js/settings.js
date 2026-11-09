/* Settings: main page + account / data / personalization / font-size sub pages (reference layout) */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);
const esc=s=>String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const PRESETS=[['DeepSeek','https://api.deepseek.com','deepseek-chat','deepseek-reasoner'],['OpenAI','https://api.openai.com/v1','gpt-4o-mini','o3-mini'],['OpenRouter','https://openrouter.ai/api/v1','deepseek/deepseek-chat','deepseek/deepseek-r1'],['Groq','https://api.groq.com/openai/v1','llama-3.3-70b-versatile','deepseek-r1-distill-llama-70b'],['Ollama (local)','http://127.0.0.1:11434/v1','llama3.2','deepseek-r1']];
const G_SVG='<svg viewBox="0 0 48 48" width="26" height="26"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>';
K.G_SVG=G_SVG;
const stack=[];
const S=K.settings={};
function mask(k){if(!k)return K.t('s_not_set');return k.length<=8?'••••••••':k.slice(0,3)+'••••••••'+k.slice(-4)}
function maskEmail(e){const a=e.indexOf('@');return a<5?e:e.slice(0,4)+'••••••'+e.slice(a-2)}
function titleOf(p){return {main:K.t('settings'),account:K.t('s_acc'),data:K.t('s_data'),custom:K.t('s_custom'),font:K.t('s_font'),apps:K.t('connected_apps'),github:K.t('gh_title')}[p]}

/* ----- builders ----- */
function sec(parent,label){if(label){const t=document.createElement('div');t.className='sec-t';t.textContent=label;parent.appendChild(t)}const c=document.createElement('div');c.className='card';parent.appendChild(c);return c}
function row(card,o){
 const b=document.createElement(o.onclick?'button':'div');b.className='row'+(o.danger?' danger':'');
 b.innerHTML='<span class="ri">'+(o.html||K.icon(o.icon,26))+'</span><span class="rt">'+esc(o.title)+(o.sub?'<small>'+esc(o.sub)+'</small>':'')+'</span>'+(o.badge?o.badge:'')+(o.value!=null&&o.value!==''?'<span class="rv">'+esc(o.value)+'</span>':'')+(o.onclick?'<span class="rc chev-fwd">'+K.icon('chevron-left',18)+'</span>':'');
 if(o.onclick)b.onclick=()=>o.onclick(b);card.appendChild(b);return b}
function toggle(card,o){
 const d=document.createElement('div');d.className='row';
 d.innerHTML='<span class="ri">'+K.icon(o.icon,26)+'</span><span class="rt">'+esc(o.title)+(o.sub?'<small>'+esc(o.sub)+'</small>':'')+'</span><span class="sw"></span>';
 const sw=d.querySelector('.sw');sw.classList.toggle('on',K.pref.bool(o.key,o.def));
 d.onclick=()=>{const on=!sw.classList.contains('on');sw.classList.toggle('on',on);K.pref.setBool(o.key,on);o.after&&o.after(on)};
 card.appendChild(d);return d}
function note(p,t){const n=document.createElement('div');n.className='note';n.textContent=t;p.appendChild(n)}

/* ----- navigation ----- */
S.open=function(p){
 const el=document.createElement('div');el.className='page';el.dataset.p=p;
 el.innerHTML='<div class="phead"><button class="back">'+K.icon('chevron-right',24).replace('class="ic"','class="ic flip-ltr"')+'</button><span>'+esc(titleOf(p))+'</span></div>';
 el.querySelector('.back').onclick=()=>S.back();
 $('pages').appendChild(el);stack.push(el);build(el,p);K.app.bars();
};
S.back=function(){const el=stack.pop();if(!el)return false;el.classList.add('out');setTimeout(()=>el.remove(),240);K.app.bars();return true};
S.depth=()=>stack.length;
S.rerender=function(){stack.forEach(el=>{const p=el.dataset.p;const sc=el.querySelector('.pbody');const top=sc?sc.scrollTop:0;
 el.querySelector('.phead span').textContent=titleOf(p);const old=el.querySelector('.pbody,.fp');if(old)old.remove();const old2=el.querySelector('.fsheet');if(old2)old2.remove();
 el.style.animation='none';build(el,p);const nb=el.querySelector('.pbody');if(nb)nb.scrollTop=top})};
function after(p){K.app.apply();S.rerender();if(p&&p.remount)K.app.remount()}
function build(el,p){
 if(p==='font'){fontPage(el);return}
 const body=document.createElement('div');body.className='pbody stagger';el.appendChild(body);
 ({main:mainPage,account:accountPage,data:dataPage,custom:customPage,apps:appsPage,github:githubPage})[p](body);
 K.emojifyEl(body);
}

function mainPage(b){
 let c=sec(b,K.t('s_profile'));
 row(c,{icon:'user',title:K.t('s_acc'),onclick:()=>S.open('account')});
 row(c,{icon:'database',title:K.t('s_data'),onclick:()=>S.open('data')});
 c=sec(b,K.t('s_link'));
 row(c,{icon:'plug',title:K.t('connected_apps'),value:K.gh.tok()?K.t('s_connected'):'',onclick:()=>S.open('apps')});
 c=sec(b,K.t('s_app'));
 row(c,{icon:'globe',title:K.t('s_lang'),value:K.lang()==='ar'?'العربية':'English',onclick:async()=>{const i=await K.ui.choose(K.t('s_lang'),['العربية','English'],K.lang()==='ar'?0:1);if(i!=null){K.pref.set('lang',i===0?'ar':'en');after({remount:true})}}});
 const thv=K.pref.get('theme','dark');
 row(c,{icon:'moon',title:K.t('s_theme'),value:thv==='system'?K.t('s_th_sys'):thv==='light'?K.t('s_light'):K.t('s_dark'),onclick:async()=>{const keys=['dark','light','system'];const i=await K.ui.choose(K.t('s_theme'),[K.t('s_dark'),K.t('s_light'),K.t('s_th_sys')],keys.indexOf(thv));if(i!=null){K.pref.set('theme',keys[i]);after({remount:true})}}});
 row(c,{icon:'a-large-small',title:K.t('s_font'),onclick:()=>S.open('font')});
 const FF=['noto','plex','cairo','almarai'];
 row(c,{icon:'sparkles',title:K.t('s_fontface'),value:K.t('f_'+K.pref.get('font_f','noto')).replace(/ \(.*\)/,''),onclick:async()=>{const i=await K.ui.choose(K.t('s_fontface'),FF.map(f=>K.t('f_'+f)),Math.max(0,FF.indexOf(K.pref.get('font_f','noto'))));if(i!=null){K.pref.set('font_f',FF[i]);after()}}});
 row(c,{icon:'pencil',title:K.t('s_weight'),value:K.t('s_w'+K.pref.get('font_w','1')),onclick:async()=>{const i=await K.ui.choose(K.t('s_weight'),[K.t('s_w0'),K.t('s_w1'),K.t('s_w2')],+K.pref.get('font_w','1'));if(i!=null){K.pref.set('font_w',String(i));after()}}});
 toggle(c,{icon:'sparkles',title:K.t('s_motion'),sub:K.t('s_motion_s'),key:'reduce_motion',def:false,after:()=>K.app.apply()});
 row(c,{icon:'wand-sparkles',title:K.t('s_custom'),onclick:()=>S.open('custom')});
 c=sec(b,K.t('s_audio'));
 row(c,{icon:'mic',title:K.t('s_main_lang'),value:K.pref.get('voice_lang','ar')==='en'?'English':'العربية',onclick:async()=>{const i=await K.ui.choose(K.t('s_main_lang'),['العربية','English'],K.pref.get('voice_lang','ar')==='en'?1:0);if(i!=null){K.pref.set('voice_lang',i===0?'ar':'en');S.rerender()}}});
 const RATES=['0.75','1.0','1.25','1.5'],RN=()=>[K.t('s_slow'),K.t('s_normal'),K.t('s_fast'),K.t('s_vfast')];
 row(c,{icon:'audio-lines',title:K.t('s_rate'),value:RN()[Math.max(0,RATES.indexOf(K.pref.get('tts_rate','1.0')))],onclick:async()=>{const i=await K.ui.choose(K.t('s_rate'),RN(),Math.max(0,RATES.indexOf(K.pref.get('tts_rate','1.0'))));if(i!=null){K.pref.set('tts_rate',RATES[i]);S.rerender()}}});
 toggle(c,{icon:'volume-2',title:K.t('s_sound'),sub:K.t('s_sound_sub'),key:'sound',def:false,after:()=>K.home.paintSound()});
 note(b,K.t('s_audio_note'));
 c=sec(b,K.t('s_privacy'));
 row(c,{icon:'lock',title:K.t('s_lock'),value:K.lock.has()?K.t('s_lock_on'):K.t('s_lock_off'),onclick:async()=>{
  if(K.lock.has()){const i=await K.ui.choose(K.t('s_lock'),[K.t('s_lock_change'),K.t('s_lock_disable')]);if(i==null)return;
   K.lock.show('verify',ok=>{if(!ok)return;if(i===0)K.lock.show('set',()=>S.rerender());else{K.pref.set('pin_hash','');S.rerender()}})}
  else K.lock.show('set',()=>S.rerender())}});
 const ADK=['0','7','30','90'];
 row(c,{icon:'clock',title:K.t('s_autodel'),value:K.t('s_ad'+K.pref.get('auto_del','0')),onclick:async()=>{const i=await K.ui.choose(K.t('s_autodel'),ADK.map(k=>K.t('s_ad'+k)),Math.max(0,ADK.indexOf(K.pref.get('auto_del','0'))));if(i!=null){K.pref.set('auto_del',ADK[i]);K.db.load();S.rerender()}}});
 c=sec(b,K.t('s_about'));
 row(c,{icon:'info',title:K.t('s_update'),value:'1.0(1)',onclick:()=>K.toast(K.t('s_uptodate'))});
 row(c,{icon:'file-text',title:K.t('s_terms'),onclick:()=>K.ui.info(K.t('s_terms'),K.t('s_terms_body'))});
 c=sec(b,'');c.style.marginTop='22px';
 row(c,{icon:'circle-help',title:K.t('s_help'),onclick:()=>K.ui.info(K.t('s_help'),K.t('s_help_body'))});
 c=sec(b,'');c.style.marginTop='22px';
 row(c,{icon:'log-out',title:K.t('s_logout'),onclick:async()=>{if(await K.ui.confirm(K.t('s_logout_q'))){K.native.logout();K.app.toLogin()}}});
 const f=document.createElement('div');f.className='foot';f.textContent=K.t('s_footer');b.appendChild(f);
}
function accountPage(b){
 const email=K.pref.get('user_email','');
 let c=sec(b,K.t('s_profile'));
 row(c,{icon:'user',title:K.t('s_name'),value:K.pref.get('name',''),onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_name'),value:K.pref.get('name','')});if(v){K.pref.set('name',v);S.rerender()}}});
 row(c,{icon:'mail',title:K.t('s_email'),value:email?maskEmail(email):K.t('guest')});
 if(K.pref.bool('user_google',false))row(c,{html:G_SVG,title:'Google',value:K.t('s_connected')});
 c=sec(b,K.t('s_security'));
 row(c,{icon:'smartphone',title:K.t('s_devices'),onclick:()=>K.ui.info(K.t('s_devices'),'• '+K.t('s_this_device')+' ('+K.native.model()+')')});
 const d=document.createElement('button');d.className='delete-acc';d.textContent=K.t('s_del_acc');
 d.onclick=async()=>{if(await K.ui.confirm(K.t('s_del_acc_q'),true)){K.native.deleteAccount(email);localStorage.removeItem('convs_'+(email||'guest'));K.native.logout();K.app.toLogin()}};b.appendChild(d);
}
function dataPage(b){
 const c=sec(b,K.t('s_ctl_hist'));
 toggle(c,{icon:'database',title:K.t('s_save_hist'),key:'save_hist',def:true,after:()=>K.db.save()});
 row(c,{icon:'share',title:K.t('s_export'),onclick:()=>{K.native.share(K.db.convs.map(cv=>'# '+cv.title+'\n'+cv.msgs.map(m=>(m.role==='user'?'User: ':'AI: ')+m.c).join('\n\n')).join('\n\n'))}});
 row(c,{icon:'trash-2',title:K.t('s_clear'),danger:true,onclick:async()=>{if(await K.ui.confirm(K.t('s_clear_q'),true)){K.db.convs.length=0;K.db.save();K.home.newChat();K.toast('✓')}}});
}
 function customPage(b){
 let c0=sec(b,K.t('s_you'));
 row(c0,{icon:'user',title:K.t('s_nick'),value:K.pref.get('nickname','')||K.t('s_not_set'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_nick'),value:K.pref.get('nickname','')});if(v!=null){K.pref.set('nickname',v);S.rerender()}}});
 row(c0,{icon:'file-text',title:K.t('s_aboutme'),value:K.pref.get('about_me','')||K.t('s_not_set'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_aboutme'),value:K.pref.get('about_me',''),hint:K.t('s_about_h'),multi:true});if(v!=null){K.pref.set('about_me',v);S.rerender()}}});
 row(c0,{icon:'sparkles',title:K.t('s_style'),value:K.t('s_st'+K.pref.get('reply_style','1')),onclick:async()=>{const i=await K.ui.choose(K.t('s_style'),[K.t('s_st0'),K.t('s_st1'),K.t('s_st2')],+K.pref.get('reply_style','1'));if(i!=null){K.pref.set('reply_style',String(i));S.rerender()}}});
 row(c0,{icon:'languages',title:K.t('s_rlang'),value:K.t('s_rl_'+K.pref.get('reply_lang','auto')),onclick:async()=>{const k=['auto','ar','en'];const i=await K.ui.choose(K.t('s_rlang'),k.map(x=>K.t('s_rl_'+x)),k.indexOf(K.pref.get('reply_lang','auto')));if(i!=null){K.pref.set('reply_lang',k[i]);S.rerender()}}});
 let cur=K.t('s_custom_p');const base=(K.pref.get('base_url','https://api.deepseek.com')||'').replace(/\/+$/,'');
 PRESETS.forEach(p=>{if(p[1]===base)cur=p[0]});
 let c=sec(b,K.t('s_connect'));
 row(c,{icon:'link',title:K.t('s_provider'),value:cur,onclick:async()=>{const i=await K.ui.choose(K.t('s_provider'),PRESETS.map(p=>p[0]));if(i!=null){const p=PRESETS[i];K.pref.set('base_url',p[1]);K.pref.set('model',p[2]);K.pref.set('think_model',p[3]);S.rerender()}}});
 row(c,{icon:'key',title:K.t('s_key'),value:mask(K.pref.get('api_key','')),onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_key'),value:K.pref.get('api_key',''),hint:'sk-...',secret:true});if(v!=null){K.pref.set('api_key',v);S.rerender()}}});
 row(c,{icon:'link',title:K.t('s_url'),value:base,onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_url'),value:base,hint:'https://api.deepseek.com'});if(v){K.pref.set('base_url',v);S.rerender()}}});
 const t=row(c,{icon:'check',title:K.t('s_test'),onclick:async()=>{const rv=t.querySelector('.rt');let sm=rv.querySelector('small');if(!sm){sm=document.createElement('small');rv.appendChild(sm)}
   sm.style.color='';sm.textContent=K.t('s_testing');const r=await K.native.test();sm.style.color=r==null?'var(--green)':'var(--danger)';sm.textContent=r==null?K.t('s_ok'):r}});
 c=sec(b,K.t('s_ai'));
 row(c,{icon:'message-square',title:K.t('s_model'),value:K.pref.get('model','deepseek-chat'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_model'),value:K.pref.get('model','deepseek-chat')});if(v){K.pref.set('model',v);S.rerender()}}});
 row(c,{icon:'atom',title:K.t('s_think_model'),value:K.pref.get('think_model','deepseek-reasoner'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_think_model'),value:K.pref.get('think_model','deepseek-reasoner')});if(v){K.pref.set('think_model',v);S.rerender()}}});
 row(c,{icon:'sparkles',title:K.t('s_system'),value:K.pref.get('system','')?K.pref.get('system','').replace(/\n/g,' '):K.t('s_none'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('s_system'),value:K.pref.get('system',''),multi:true});if(v!=null){K.pref.set('system',v);S.rerender()}}});
 const temps=['0.2','0.5','0.7','1.0','1.3'];
 row(c,{icon:'settings',title:K.t('s_temp'),value:K.pref.get('temp','0.7'),onclick:async()=>{const i=await K.ui.choose(K.t('s_temp'),temps,temps.indexOf(K.pref.get('temp','0.7')));if(i!=null){K.pref.set('temp',temps[i]);S.rerender()}}});
 toggle(c,{icon:'atom',title:K.t('s_def_think'),key:'def_think',def:false});
 toggle(c,{icon:'globe',title:K.t('s_def_search'),sub:K.t('s_search_src'),key:'def_search',def:false});
 c=sec(b,K.t('s_app'));
 toggle(c,{icon:'sparkles',title:K.t('s_stream'),key:'stream',def:true});
 toggle(c,{icon:'smartphone',title:K.t('s_haptic'),key:'haptic',def:true});
 toggle(c,{icon:'wand-sparkles',title:K.t('s_fx'),key:'fx',def:false,after:()=>K.app.apply()});
}
const SOON='';
function appIc(bg,icon){return '<span class="appic" style="background:'+bg+'">'+K.icon(icon,20)+'</span>'}
function appsPage(b){
 const n=document.createElement('div');n.className='note';n.style.paddingTop='4px';n.textContent=K.t('apps_sub');b.appendChild(n);
 let c=sec(b,K.t('apps_live'));
 row(c,{html:K.brand('github'),title:K.t('app_github'),sub:K.t('app_github_s'),value:K.gh.tok()?K.t('s_connected'):'',onclick:()=>S.open('github')});
 c=sec(b,K.t('apps_soon'));
 const bd='<i class="badge">'+K.t('soon')+'</i>';
 [['googledrive','app_drive'],['gmail','app_gmail'],['googlecalendar','app_cal'],['googledocs','app_docs'],['googlesheets','app_sheets'],['notion','app_notion'],['gitlab','app_gitlab'],['figma','app_figma'],['dropbox','app_dropbox'],['trello','app_trello'],['discord','app_discord'],['telegram','app_telegram']].forEach(a=>{
  row(c,{html:K.brand(a[0]),title:K.t(a[1]),sub:K.t(a[1]+'_s'),badge:bd,onclick:()=>K.toast(K.t(a[1])+' — '+K.t('soon'))});
 });
 row(c,{html:appIc('#6b5cff','plug'),title:K.t('app_api'),sub:K.t('app_api_s'),badge:bd,onclick:()=>K.toast(K.t('app_api')+' — '+K.t('soon'))});
}
async function githubPage(b){
 const connected=!!K.gh.tok();
 if(!connected){
  let c=sec(b,K.t('gh_title'));
  row(c,{html:K.brand('github'),title:K.t('gh_token'),value:K.t('s_not_set'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('gh_token'),hint:'ghp_...',secret:true});if(v){K.pref.set('gh_token',v);S.rerender()}}});
  note(b,K.t('gh_help'));return;
 }
 let c=sec(b,K.t('gh_title'));
 const who=row(c,{html:K.brand('github'),title:K.t('gh_connect'),value:'…'});
 row(c,{icon:'unplug',title:K.t('gh_disconnect'),danger:true,onclick:async()=>{K.pref.set('gh_token','');K.pref.set('gh_sel','[]');K.home&&K.home.paintPills&&K.home.paintPills();S.rerender()}});
 const t=document.createElement('div');t.className='sec-t';t.textContent=K.t('gh_repos');b.appendChild(t);
 const list=document.createElement('div');list.className='card';b.appendChild(list);
 for(let i=0;i<4;i++){const sk=document.createElement('div');sk.className='sk';sk.style.margin='10px';list.appendChild(sk)}
 note(b,K.t('gh_hint'));
 try{
  const u=await K.gh.user();who.querySelector('.rt').firstChild.textContent=K.t('gh_connected',u.login);who.querySelector('.rv')&&who.querySelector('.rv').remove();
  const repos=await K.gh.repos();list.innerHTML='';
  if(!repos.length){list.innerHTML='<div class="row"><span class="rt" style="color:var(--text2)">'+K.t('gh_none')+'</span></div>';return}
  repos.forEach(r=>{
   const on=K.gh.sel().includes(r.full_name);
   const rw=row(list,{icon:r.private?'lock':'folder-git-2',title:r.name,sub:r.description||r.full_name,value:on?K.t('gh_attached'):''});
   rw.classList.toggle('picked',on);
   rw.onclick=()=>{const now=K.gh.toggle(r.full_name);rw.classList.toggle('picked',now);const v=rw.querySelector('.rv');if(now){if(!v){const sp=document.createElement('span');sp.className='rv';sp.textContent=K.t('gh_attached');rw.insertBefore(sp,rw.querySelector('.rc')||null)}}else if(v)v.remove();K.home.paintPills&&K.home.paintPills();K.native.haptic()};
  });
 }catch(e){list.innerHTML='<div class="row"><span class="rt" style="color:var(--danger)">'+K.t('gh_err')+'</span></div>'}
}
function fontPage(el){
 const prev=document.createElement('div');prev.className='fp-prev';
 prev.innerHTML='<div class="bubble">'+K.t('s_preview')+'</div><p>'+K.t('s_p1')+'</p><p>'+K.t('s_p2')+'</p>';el.appendChild(prev);
 const sh=document.createElement('div');sh.className='fsheet';
 sh.innerHTML='<div class="frow"><span>'+K.t('s_follow')+'</span><span class="sw"></span></div><div class="labels"><b>A</b><span id="flab"></span><b>A</b></div><input class="slider" type="range" min="0" max="6" step="1"><div class="ticks">'+'<i></i>'.repeat(7)+'</div>';
 el.appendChild(sh);
 const sw=sh.querySelector('.sw'),sl=sh.querySelector('.slider'),lab=sh.querySelector('#flab');
 sw.classList.toggle('on',K.pref.bool('font_sys',false));sl.value=K.pref.get('font_i','2');
 const upd=()=>{const sys=K.pref.bool('font_sys',false);sh.classList.toggle('dis',sys);sl.disabled=sys;const i=+sl.value;lab.textContent=sys?K.t('s_follow'):(i===2?K.t('s_default'):i<2?K.t('s_small'):K.t('s_large'));K.app.apply()};
 sw.parentNode.onclick=()=>{const on=!sw.classList.contains('on');sw.classList.toggle('on',on);K.pref.setBool('font_sys',on);upd()};
 sl.oninput=()=>{K.pref.set('font_i',sl.value);upd()};upd();
}
})();
