/* Settings: main page + account / data / personalization / font-size sub pages (reference layout) */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);
const esc=s=>String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const PRESETS=[['Anthropic Claude (OpenAI-compat)','https://api.anthropic.com/v1','claude-sonnet-5-5','claude-opus-5-5'],['DeepSeek','https://api.deepseek.com','deepseek-chat','deepseek-reasoner'],['OpenAI','https://api.openai.com/v1','gpt-4o-mini','o3-mini'],['Google Gemini (AI Studio)','https://generativelanguage.googleapis.com/v1beta/openai','gemini-flash-latest','gemini-flash-latest'],['OpenRouter (free models)','https://openrouter.ai/api/v1','meta-llama/llama-3.3-70b-instruct:free','deepseek/deepseek-r1:free'],['GitHub Models','https://models.github.ai/inference','openai/gpt-4o-mini','openai/o3-mini'],['Mistral','https://api.mistral.ai/v1','mistral-small-latest','magistral-small-latest'],['Groq','https://api.groq.com/openai/v1','llama-3.3-70b-versatile','openai/gpt-oss-20b'],['Ollama (local)','http://127.0.0.1:11434/v1','llama3.2','deepseek-r1'],['Bawsala LM (my server)','http://192.168.1.10:8000/v1','bawsala','bawsala']];
const G_SVG='<svg viewBox="0 0 48 48" width="26" height="26"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>';
K.G_SVG=G_SVG;
const stack=[];
const S=K.settings={};
function mask(k){if(!k)return K.t('s_not_set');return k.length<=8?'••••••••':k.slice(0,3)+'••••••••'+k.slice(-4)}
function maskEmail(e){const a=e.indexOf('@');return a<5?e:e.slice(0,4)+'••••••'+e.slice(a-2)}
function titleOf(p){const ar=K.lang()==='ar';const hv=HUB().flat().find(h=>h[3]===p);if(hv)return ar?hv[1]:hv[2];return {main:K.t('settings'),account:K.t('s_acc'),data:K.t('s_data'),custom:K.t('s_custom'),font:K.t('s_font'),apps:K.t('connected_apps'),github:K.t('gh_title'),prompts:K.t('t_prompts'),memory:K.t('t_memory'),stats:K.t('t_stats'),licenses:K.t('t_licenses'),oss:(K.lang()==='ar'?'نماذج مفتوحة المصدر':'Open-source models'),tasks:K.t('tk_title')}[p]}

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
 if(p!=='main'&&p!=='font'){const pb=el.querySelector('.pbody');if(pb){pb.classList.add('loading');setTimeout(()=>pb.classList.remove('loading'),320)}}
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
 ({main:mainPage,general:sp_general,notif:sp_notif,sound:sp_sound,ai:sp_ai,security:sp_security,storage:sp_storage,about:sp_about,tools:sp_tools,privacy:sp_privacy,account:accountPage,data:dataPage,custom:customPage,apps:appsPage,github:githubPage,prompts:promptsPage,memory:memoryPage,stats:statsPage,licenses:licensesPage,oss:ossPage,tasks:tasksPage})[p](body);
 K.emojifyEl(body);
}

function sp_general(b){
 let c;
 c=sec(b,K.t('s_app'));
 row(c,{icon:'globe',title:K.t('s_lang'),value:K.lang()==='ar'?'العربية':'English',onclick:async()=>{const i=await K.ui.choose(K.t('s_lang'),['العربية','English'],K.lang()==='ar'?0:1);if(i!=null){K.pref.set('lang',i===0?'ar':'en');after({remount:true})}}});
 const thv=K.pref.get('theme','dark');
 row(c,{icon:'moon',title:K.t('s_theme'),value:thv==='system'?K.t('s_th_sys'):thv==='light'?K.t('s_light'):K.t('s_dark'),onclick:async()=>{const keys=['dark','light','system'];const i=await K.ui.choose(K.t('s_theme'),[K.t('s_dark'),K.t('s_light'),K.t('s_th_sys')],keys.indexOf(thv));if(i!=null){K.pref.set('theme',keys[i]);after({remount:true})}}});
 row(c,{icon:'a-large-small',title:K.t('s_font'),onclick:()=>S.open('font')});
 toggle(c,{icon:'sparkles',title:K.t('s_motion'),sub:K.t('s_motion_s'),key:'reduce_motion',def:false,after:()=>K.app.apply()});
 row(c,{icon:'wand-sparkles',title:K.t('s_custom'),onclick:()=>S.open('custom')});
 toggle(c,{icon:'arrow-up',title:K.t('t_enter'),sub:K.t('t_enter_s'),key:'enter_send',def:false});
}
function sp_sound(b){
 let c;
 c=sec(b,K.t('s_audio'));
 row(c,{icon:'audio-lines',title:(K.lang()==='ar'?'اللهجة والصوت':'Dialect & voice'),sub:(K.lang()==='ar'?'28 لهجة ولغة وأصوات الهاتف':'28 dialects & languages, phone voices'),onclick:()=>K.dialect.open()});
 row(c,{icon:'mic',title:(K.lang()==='ar'?'شكل الإملاء الصوتي':'Dictation style'),value:K.pref.get('dict_style','bar')==='orb'?(K.lang()==='ar'?'كرة':'Orb'):(K.lang()==='ar'?'شريط':'Bar'),onclick:async()=>{const k=await K.ui.choose(K.lang()==='ar'?'شكل الإملاء الصوتي':'Dictation style',K.lang()==='ar'?['شريط (يحلّ محل مربع الرسالة)','كرة نابضة (لوحة من الأسفل)']:['Bar (replaces the message box)','Pulsing orb (bottom sheet)'],K.pref.get('dict_style','bar')==='orb'?1:0);if(k!==null){K.pref.set('dict_style',k===1?'orb':'bar');S.rerender()}}});
 row(c,{icon:'mic',title:K.t('s_main_lang'),value:K.pref.get('voice_lang','ar')==='en'?'English':'العربية',onclick:async()=>{const i=await K.ui.choose(K.t('s_main_lang'),['العربية','English'],K.pref.get('voice_lang','ar')==='en'?1:0);if(i!=null){K.pref.set('voice_lang',i===0?'ar':'en');S.rerender()}}});
 const RATES=['0.75','1.0','1.25','1.5'],RN=()=>[K.t('s_slow'),K.t('s_normal'),K.t('s_fast'),K.t('s_vfast')];
 row(c,{icon:'audio-lines',title:K.t('s_rate'),value:RN()[Math.max(0,RATES.indexOf(K.pref.get('tts_rate','1.0')))],onclick:async()=>{const i=await K.ui.choose(K.t('s_rate'),RN(),Math.max(0,RATES.indexOf(K.pref.get('tts_rate','1.0'))));if(i!=null){K.pref.set('tts_rate',RATES[i]);S.rerender()}}});
 toggle(c,{icon:'volume-2',title:K.t('s_sound'),sub:K.t('s_sound_sub'),key:'sound',def:false,after:()=>K.home.paintSound()});
 note(b,K.t('s_audio_note'));
}
function sp_ai(b){
let c=sec(b,K.t('s_ai'));
 row(c,{icon:'atom',title:(K.lang()==='ar'?'نماذج مفتوحة المصدر':'Open-source models'),sub:(K.lang()==='ar'?'اتصال تلقائي ومفاتيح مجانية':'Auto-connect and free keys'),value:K.pref.bool('auto',true)?(K.lang()==='ar'?'تلقائي':'Auto'):'',onclick:()=>S.open('oss')});
 row(c,{icon:'atom',title:(K.lang()==='ar'?'كتالوج النماذج (43)':'Model catalog (43)'),sub:(K.lang()==='ar'?'Llama وQwen وGemma وMistral وDeepSeek وGPT-OSS…':'Llama, Qwen, Gemma, Mistral, DeepSeek, GPT-OSS…'),onclick:()=>K.catalog.open()});
 toggle(c,{icon:'sparkles',title:(K.lang()==='ar'?'مكتبة المهارات (185)':'Skills library (185)'),sub:(K.lang()==='ar'?'يطبّق المهارة المناسبة لطلبك تلقائياً':'Applies the right skills to your request'),key:'skills',def:true});
 toggle(c,{icon:'atom',title:(K.lang()==='ar'?'فريق الذكاء لبناء التطبيقات':'AI team for app builds'),sub:(K.lang()==='ar'?'معماري ← مصمم ← مسؤول تفاصيل ← مبرمج':'Architect → designer → details → developer'),key:'team',def:true});
 toggle(c,{icon:'atom',title:K.t('t_plan'),sub:K.t('t_plan_s'),key:'plan',def:true});
 const MT=['0','512','1024','2048','4096'];
 row(c,{icon:'a-large-small',title:K.t('t_maxtok'),value:K.pref.get('max_tokens','0')==='0'?K.t('t_auto'):K.pref.get('max_tokens','0'),onclick:async()=>{const i=await K.ui.choose(K.t('t_maxtok'),MT.map(x=>x==='0'?K.t('t_auto'):x),Math.max(0,MT.indexOf(K.pref.get('max_tokens','0'))));if(i!=null){K.pref.set('max_tokens',MT[i]);S.rerender()}}});
}
function sp_tools(b){
let c=sec(b,'');
 row(c,{icon:'bot',title:K.lang()==='ar'?'وكيل المهام (خلفية)':'Task agent (background)',sub:K.lang()==='ar'?'اكتب مهمة فينفّذها ويُشعرك عند الانتهاء':'Write a task; it runs it and notifies you',onclick:()=>K.agent.open()});
 row(c,{icon:'code-xml',title:K.lang()==='ar'?'لغة بناء التطبيقات':'App build language',value:(K.STACKS.find(x=>x[0]===K.pref.get('dev_stack','flutter'))||K.STACKS[0])[1].split(' — ')[0],onclick:async()=>{const i=await K.ui.choose(K.lang()==='ar'?'لغة بناء التطبيقات':'App build language',K.STACKS.map(x=>x[1]),Math.max(0,K.STACKS.findIndex(x=>x[0]===K.pref.get('dev_stack','flutter'))));if(i!=null){K.pref.set('dev_stack',K.STACKS[i][0]);S.rerender()}}});
 row(c,{icon:'sparkles',title:K.t('t_prompts'),sub:K.t('t_prompts_s'),onclick:()=>S.open('prompts')});
 row(c,{icon:'database',title:K.t('t_memory'),sub:K.t('t_memory_s'),onclick:()=>S.open('memory')});
 row(c,{icon:'clock',title:K.t('t_stats'),sub:K.t('t_stats_s'),onclick:()=>S.open('stats')});
 toggle(c,{icon:'eye-off',title:K.t('t_incog'),sub:K.t('t_incog_s'),key:'incognito',def:false});
}
function sp_security(b){
 let c;
 c=sec(b,K.t('s_privacy'));
 row(c,{icon:'lock',title:K.t('s_lock'),value:K.lock.has()?K.t('s_lock_on'):K.t('s_lock_off'),onclick:async()=>{
  if(K.lock.has()){const i=await K.ui.choose(K.t('s_lock'),[K.t('s_lock_change'),K.t('s_lock_disable')]);if(i==null)return;
   K.lock.show('verify',ok=>{if(!ok)return;if(i===0)K.lock.show('set',()=>S.rerender());else{K.pref.set('pin_hash','');S.rerender()}})}
  else K.lock.show('set',()=>S.rerender())}});
 const ADK=['0','7','30','90'];
 row(c,{icon:'clock',title:K.t('s_autodel'),value:K.t('s_ad'+K.pref.get('auto_del','0')),onclick:async()=>{const i=await K.ui.choose(K.t('s_autodel'),ADK.map(k=>K.t('s_ad'+k)),Math.max(0,ADK.indexOf(K.pref.get('auto_del','0'))));if(i!=null){K.pref.set('auto_del',ADK[i]);K.db.load();S.rerender()}}});
 c=sec(b,'');c.style.marginTop='22px';
 row(c,{icon:'log-out',title:K.t('s_logout'),onclick:async()=>{if(await K.ui.confirm(K.t('s_logout_q'))){K.native.logout();K.app.toLogin()}}});
}
function sp_storage(b){
 let c;
 c=sec(b,K.t('t_backup'));
 row(c,{icon:'share',title:K.t('t_export'),onclick:()=>{K.native.share(JSON.stringify({v:1,app:'karrar',convs:K.db.convs,prompts:JSON.parse(K.pref.get('prompts','[]')),memories:JSON.parse(K.pref.get('memories','[]'))}))}});
 row(c,{icon:'database',title:K.t('t_import'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('t_import'),hint:K.t('t_import_h'),multi:true});if(!v)return;try{const o=JSON.parse(v);if(o.app!=='karrar')throw 0;(o.convs||[]).forEach(c=>{if(!K.db.find(c.id))K.db.convs.push(c)});if(o.prompts&&o.prompts.length)K.pref.set('prompts',JSON.stringify(o.prompts));if(o.memories&&o.memories.length)K.pref.set('memories',JSON.stringify(o.memories));K.db.save();K.toast(K.t('t_import_ok'))}catch(e){K.toast(K.t('t_import_bad'))}}});
}
function sp_about(b){
 let c;
 c=sec(b,K.t('s_about'));
 row(c,{icon:'info',title:K.t('s_update'),value:'1.0(1)',onclick:()=>K.toast(K.t('s_uptodate'))});
 row(c,{icon:'info',title:K.t('t_licenses'),sub:K.t('t_licenses_s'),onclick:()=>S.open('licenses')});
 row(c,{icon:'globe',title:(K.lang()==='ar'?'الموقع الرسمي':'Official website'),value:'bawsala.unaux.com',onclick:()=>K.native.open('http://bawsala.unaux.com/')});
 row(c,{icon:'share',title:K.t('t_share'),onclick:()=>K.native.share(K.t('t_share_t'))});
 c=sec(b,'');c.style.marginTop='22px';
 row(c,{icon:'circle-help',title:K.t('s_help'),onclick:()=>K.legal.open('contact')});
}
function sp_privacy(b){
let c=sec(b,'');
row(c,{icon:'file-text',title:K.t('s_terms'),onclick:()=>K.legal.open('terms')});
row(c,{icon:'shield-check',title:(K.lang()==='ar'?'سياسة الخصوصية':'Privacy policy'),onclick:()=>K.native.open('https://karrarkoqw4v-ux.github.io/Karrar/privacy.html')});
row(c,{icon:'file-text',title:(K.lang()==='ar'?'شروط الخدمة (الويب)':'Terms of service (web)'),onclick:()=>K.native.open('https://karrarkoqw4v-ux.github.io/Karrar/terms.html')});
note(b,K.lang()==='ar'?'بياناتك ومحادثاتك تبقى على جهازك.':'Your data and chats stay on your device.');
}

const HUB=()=>{const ar=K.lang()==='ar';return [
 [['settings','عام','General','general'],['smartphone','الإشعارات والاهتزاز','Notifications & haptics','notif'],['audio-lines','الصوت واللهجات','Sound & dialects','sound'],['atom','النماذج والذكاء الاصطناعي','Models & AI','ai'],['graduation-cap','الطلاب','Students','students'],['shield-check','الأمان وتسجيل الدخول','Security & sign-in','security'],['plug','التطبيقات الموصولة','Connected apps','apps'],['wand-sparkles','التخصيص والأدوات','Personalization & tools','tools']],
 [['database','التخزين والنسخ الاحتياطي','Storage & backup','storage'],['lock','مركز الخصوصية','Privacy center','privacy'],['clock','السجل والبيانات','History & data','data'],['circle-help','الإبلاغ عن خطأ','Report a bug','bug'],['info','حول','About','about']]]};
function sp_notif(b){const c=sec(b,'');toggle(c,{icon:'smartphone',title:K.t('s_haptic'),key:'haptic',def:true});toggle(c,{icon:'volume-2',title:K.t('s_sound'),sub:K.t('s_sound_sub'),key:'sound',def:false,after:()=>K.home.paintSound()});note(b,K.lang()==='ar'?'الإشعارات خارج التطبيق غير مفعّلة حالياً.':'Out-of-app notifications are not enabled yet.')}
function hubOpen(k){if(k==='students')K.students.open();else if(k==='bug')K.legal.open('contact');else S.open(k)}
function avatarHTML(sz){const pic=K.pref.get('user_av','')||K.pref.get('user_pic','');const nm=(K.pref.get('name','')||K.pref.get('user_email','')||'B').trim();
 return '<span class="avt" style="width:'+sz+'px;height:'+sz+'px;font-size:'+Math.round(sz*.42)+'px">'+(pic?'<img src="'+esc(pic)+'" referrerpolicy="no-referrer" onerror="this.remove()">':'')+'<b>'+esc(nm[0].toUpperCase())+'</b></span>'}
S.avatarHTML=avatarHTML;
function appearanceRows(c){
 const ar=K.lang()==='ar';
 const AC=[['#4d6bfe',ar?'أزرق':'Blue'],['#8b5cf6',ar?'بنفسجي':'Purple'],['#10b981',ar?'أخضر':'Green'],['#f59e0b',ar?'برتقالي':'Orange'],['#ef4444',ar?'أحمر':'Red'],['#ec4899',ar?'وردي':'Pink']];
 const cur=K.pref.get('accent','#4d6bfe'),ca=AC.find(x=>x[0]===cur)||AC[0],thv=K.pref.get('theme','dark');
 row(c,{icon:'sun',title:K.t('s_theme'),value:thv==='system'?K.t('s_th_sys'):thv==='light'?K.t('s_light'):K.t('s_dark'),onclick:async()=>{const keys=['dark','light','system'];const i=await K.ui.choose(K.t('s_theme'),[K.t('s_dark'),K.t('s_light'),K.t('s_th_sys')],Math.max(0,keys.indexOf(thv)));if(i!=null){K.pref.set('theme',keys[i]);after()}}});
 row(c,{icon:'wand-sparkles',title:ar?'لون التمييز':'Accent color',value:ca[1]+' ●',onclick:async()=>{const i=await K.ui.choose(ar?'لون التمييز':'Accent color',AC.map(x=>x[1]),Math.max(0,AC.indexOf(ca)));if(i!=null){K.pref.set('accent',AC[i][0]);K.app.accent&&K.app.accent();S.rerender()}}}).querySelector('.rv').style.color=ca[0]}
function logoutDialog(){
 const ar=K.lang()==='ar';const w=document.createElement('div');w.className='lgo';
 w.innerHTML='<div class="lgo-b"></div><div class="lgo-d"><h3>'+(ar?'هل تريد تسجيل الخروج؟':'Log out?')+'</h3><p>'+(ar?'ستحتاج إلى تسجيل الدخول مجدداً للوصول إلى حسابك.':'You will need to sign in again to reach your account.')+'</p><button class="lgo-y">'+(ar?'تسجيل الخروج':'Log out')+'</button><button class="lgo-n">'+(ar?'إلغاء':'Cancel')+'</button></div>';
 document.body.appendChild(w);const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),180)};
 w.querySelector('.lgo-b').onclick=close;w.querySelector('.lgo-n').onclick=close;w.querySelector('.lgo-y').onclick=()=>{close();K.native.logout();K.app.toLogin()}}
function mainPage(b){
 const ar=K.lang()==='ar',nm=K.pref.get('name','')||(ar?'ضيف':'Guest'),em=K.pref.get('user_email','');
 const hero=document.createElement('button');hero.className='ahero';hero.innerHTML=avatarHTML(78)+'<i class="pen">'+K.icon('pencil',15)+'</i>';hero.onclick=()=>profileSheet();b.appendChild(hero);
 const hn=document.createElement('div');hn.className='aname';hn.textContent=nm;b.appendChild(hn);
 let c=sec(b,ar?'الحساب':'Account');c.classList.add('hub');
 row(c,{icon:'briefcase',title:ar?'مساحة العمل':'Workspace',value:ar?'شخصي':'Personal'});
 row(c,{icon:'clock',title:ar?'الاستخدام والحدود':'Usage & limits',onclick:()=>S.open('stats')});
 row(c,{icon:'mail',title:K.t('s_email'),sub:em||K.t('guest')});
 if(K.pref.bool('user_google',false))row(c,{html:G_SVG,title:'Google',value:K.t('s_connected')});
 c=sec(b,'');c.classList.add('hub');appearanceRows(c);
 c=sec(b,'');c.classList.add('hub');row(c,{icon:'wand-sparkles',title:ar?'تخصيص بوصلة':'Customize Bawsala',sub:ar?'الأدوات، والذاكرة، والأوامر، والمزيد':'Tools, memory, prompts and more',onclick:()=>S.open('tools')});
 const idx=[];
 (ar?['لون التمييز','المظهر','اللغة','الاستخدام والحدود','ترقية الخطة','الاشتراك','الملف الشخصي','الاسم','صورة الملف الشخصي']:['Accent color','Appearance','Language','Usage & limits','Upgrade plan','Subscription','Profile','Name','Profile picture']).forEach(t=>idx.push({t:t,p:'account',g:ar?'الحساب':'Account'}));
 HUB().forEach(g=>{const c=document.createElement('div');c.className='card hub';b.appendChild(c);
  g.filter(x=>x[3]!=='tools').forEach(x=>{const r=row(c,{icon:x[0],title:ar?x[1]:x[2],onclick:()=>hubOpen(x[3])});if(x[3]==='bug')r.querySelector('.rt').style.color='#7da2ff';idx.push({t:ar?x[1]:x[2],p:x[3],g:''})})});
 {const lc=document.createElement('div');lc.className='card hub lgcard';b.appendChild(lc);const lr=row(lc,{icon:'log-out',title:K.t('s_logout'),danger:true,onclick:logoutDialog});}
 const f=document.createElement('div');f.className='foot';f.textContent=K.t('s_footer');b.appendChild(f);
 /* floating search */
 const sb=document.createElement('div');sb.className='sbar';sb.innerHTML='<button class="sx hidden">'+K.icon('x',20)+'</button><label class="sp">'+K.icon('search',20)+'<input type="text" placeholder="'+(ar?'البحث في الإعدادات':'Search settings')+'" enterkeyhint="search"></label>';
 b.parentNode.appendChild(sb);
 const inp=sb.querySelector('input'),sx=sb.querySelector('.sx');let res=null;
 const pages=['general','notif','sound','ai','security','storage','about','tools','privacy'];
 const deep=()=>{const o=[];pages.forEach(pg=>{const d=document.createElement('div');try{({general:sp_general,notif:sp_notif,sound:sp_sound,ai:sp_ai,security:sp_security,storage:sp_storage,about:sp_about,tools:sp_tools,privacy:sp_privacy})[pg](d)}catch(e){return}
   d.querySelectorAll('.row .rt').forEach(t=>{const tt=t.childNodes[0]?t.childNodes[0].textContent:'';if(tt)o.push({t:tt,p:pg,g:(HUB().flat().find(h=>h[3]===pg)||[])[ar?1:2]||''})})});return idx.concat(o)};
 let all=null;
 const run=()=>{const q=inp.value.trim().toLowerCase();sx.classList.toggle('hidden',!q&&document.activeElement!==inp);if(res){res.remove();res=null}
  b.classList.toggle('searching',!!q);if(!q)return;if(!all)all=deep();
  const hit=all.filter(x=>x.t.toLowerCase().includes(q)).slice(0,30);
  res=document.createElement('div');res.className='card sres';
  res.innerHTML=hit.length?hit.map((x,i)=>'<button class="row" data-i="'+i+'"><span class="rt">'+esc(x.t)+(x.g?'<small>'+esc(x.g)+'</small>':'')+'</span></button>').join(''):'<div class="note">'+(ar?'لا نتائج':'No results')+'</div>';
  res.querySelectorAll('button').forEach(bt=>bt.onclick=()=>{inp.blur();hubOpen(hit[+bt.dataset.i].p)});b.insertBefore(res,b.firstChild)};
 inp.oninput=run;inp.onfocus=()=>{sb.classList.add('on');sx.classList.remove('hidden')};
 sx.onclick=()=>{inp.value='';inp.blur();sb.classList.remove('on');sx.classList.add('hidden');run()};
 inp.onblur=()=>setTimeout(()=>{if(!inp.value)sb.classList.remove('on')},150);
}

function accountPage(b){
 const ar=K.lang()==='ar',email=K.pref.get('user_email','');
 const hero=document.createElement('button');hero.className='ahero';hero.innerHTML=avatarHTML(112)+'<i class="pen">'+K.icon('pencil',17)+'</i>';hero.onclick=()=>profileSheet();b.appendChild(hero);
 const nm=document.createElement('div');nm.className='aname';nm.textContent=K.pref.get('name','')||(ar?'ضيف':'Guest');b.appendChild(nm);
 let c=sec(b,ar?'الحساب':'Account');
 const chev=r=>r;
 chev(row(c,{icon:'clock',title:ar?'الاستخدام والحدود':'Usage & limits',onclick:()=>S.open('stats')}));
 row(c,{icon:'mail',title:K.t('s_email'),sub:email||K.t('guest')});
 if(K.pref.bool('user_google',false))row(c,{html:G_SVG,title:'Google',value:K.t('s_connected')});
 const AC=[['#4d6bfe',ar?'أزرق':'Blue'],['#8b5cf6',ar?'بنفسجي':'Purple'],['#10b981',ar?'أخضر':'Green'],['#f59e0b',ar?'برتقالي':'Orange'],['#ef4444',ar?'أحمر':'Red'],['#ec4899',ar?'وردي':'Pink']];
 const cur=K.pref.get('accent','#4d6bfe'),ca=AC.find(x=>x[0]===cur)||AC[0];
 c=sec(b,'');
 const thv=K.pref.get('theme','dark');
 chev(row(c,{icon:'sun',title:K.t('s_theme'),value:thv==='system'?K.t('s_th_sys'):thv==='light'?K.t('s_light'):K.t('s_dark'),onclick:async()=>{const keys=['dark','light','system'];const i=await K.ui.choose(K.t('s_theme'),[K.t('s_dark'),K.t('s_light'),K.t('s_th_sys')],Math.max(0,keys.indexOf(thv)));if(i!=null){K.pref.set('theme',keys[i]);after()}}}));
 chev(row(c,{icon:'wand-sparkles',title:ar?'لون التمييز':'Accent color',html:'<span class="appic" style="background:'+ca[0]+';width:26px;height:26px;border-radius:50%"></span>',value:ca[1],onclick:async()=>{const i=await K.ui.choose(ar?'لون التمييز':'Accent color',AC.map(x=>x[1]),Math.max(0,AC.indexOf(ca)));if(i!=null){K.pref.set('accent',AC[i][0]);K.app.accent&&K.app.accent();S.rerender()}}}));
 chev(row(c,{icon:'globe',title:K.t('s_lang'),value:ar?'العربية':'English',onclick:async()=>{const i=await K.ui.choose(K.t('s_lang'),['العربية','English'],ar?0:1);if(i!=null){K.pref.set('lang',i===0?'ar':'en');after({remount:true})}}}));
 c=sec(b,K.t('s_security'));
 row(c,{icon:'smartphone',title:K.t('s_devices'),onclick:()=>K.ui.info(K.t('s_devices'),'• '+K.t('s_this_device')+' ('+K.native.model()+')')});
 const d=document.createElement('button');d.className='delete-acc';d.textContent=K.t('s_del_acc');
 d.onclick=async()=>{if(await K.ui.confirm(K.t('s_del_acc_q'),true)){K.native.deleteAccount(email);localStorage.removeItem('convs_'+(email||'guest'));K.native.logout();K.app.toLogin()}};b.appendChild(d);
}
function profileSheet(){
 const ar=K.lang()==='ar';let av=K.pref.get('user_av','');
 const w=document.createElement('div');w.className='psheet';
 w.innerHTML='<div class="pbd"></div><div class="psh"><i class="grab"></i><button class="pavb"><span id="pavh"></span><i class="cam">'+K.icon('camera',18)+'</i></button>'
  +'<input class="pfld" id="pfN" placeholder="'+(ar?'الاسم':'Name')+'" value="'+esc(K.pref.get('name',''))+'"><input class="pfld" id="pfU" dir="ltr" placeholder="'+(ar?'اسم المستخدم':'Username')+'" value="'+esc(K.pref.get('username',''))+'">'
  +'<p>'+(ar?'يساعد ملفك الشخصي الآخرين على التعرّف عليك.':'Your profile helps others recognize you.')+'</p><button class="pbtn" id="pfS">'+(ar?'حفظ الملف الشخصي':'Save profile')+'</button><button class="pcan" id="pfC">'+(ar?'إلغاء':'Cancel')+'</button></div>';
 document.body.appendChild(w);
 const paint=()=>{const pic=av||K.pref.get('user_pic','');const nmv=($('pfN').value||K.pref.get('user_email','')||'B').trim();$('pavh').innerHTML='<span class="avt" style="width:120px;height:120px;font-size:50px">'+(pic?'<img src="'+esc(pic)+'" referrerpolicy="no-referrer">':'')+'<b>'+esc(nmv[0].toUpperCase())+'</b></span>'};paint();
 const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),220)};
 w.querySelector('.pbd').onclick=close;$('pfC').onclick=close;
 $('pfN').oninput=()=>{if(!av)paint()};
 w.querySelector('.pavb').onclick=async()=>{const i=await K.ui.choose(ar?'صورة الملف الشخصي':'Profile picture',[ar?'من الألبوم':'From album',ar?'الكاميرا':'Camera',ar?'إزالة الصورة':'Remove photo']);if(i==null)return;
  if(i===2){av='';paint();return}
  const r=await (i===0?K.native.pickImages():K.native.camera());if(r&&r[0]){const src=r[0].b64?'data:image/jpeg;base64,'+r[0].b64:r[0].th;
   const im=new Image();im.onload=()=>{const cv=document.createElement('canvas');cv.width=cv.height=256;const x=cv.getContext('2d');const m=Math.min(im.width,im.height);x.drawImage(im,(im.width-m)/2,(im.height-m)/2,m,m,0,0,256,256);av=cv.toDataURL('image/jpeg',.85);paint()};im.src=src}};
 $('pfS').onclick=()=>{const n=$('pfN').value.trim();if(n)K.pref.set('name',n);K.pref.set('username',$('pfU').value.trim().replace(/^@/,''));K.pref.set('user_av',av);close();K.toast(ar?'تم حفظ الملف الشخصي':'Profile saved');S.rerender();if(K.home&&K.home.paintProfile)K.home.paintProfile()};
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
 [['drive','googledrive','app_drive'],['gmail','gmail','app_gmail'],['cal','googlecalendar','app_cal'],['docs','googledocs','app_docs'],['sheets','googlesheets','app_sheets']].forEach(a=>row(c,{html:K.brand(a[1]),title:K.t(a[2]),sub:K.t(a[2]+'_s'),value:K.google.connected(a[0])?K.t('s_connected'):'',onclick:()=>K.google.open(a[0])}));
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
const LIC=[['Inter','SIL Open Font License 1.1','rsms.me/inter'],['Noto Sans / Noto Sans Arabic','SIL Open Font License 1.1','Google Fonts'],['IBM Plex Sans Arabic','SIL Open Font License 1.1','IBM'],['Cairo','SIL Open Font License 1.1','Mohamed Gaber, Accademia di Belle Arti di Urbino'],['Almarai','SIL Open Font License 1.1','Boutros Fonts'],['Lucide icons','ISC License','lucide.dev'],['Twemoji','CC-BY 4.0 (graphics) / MIT (code)','Twitter, Inc. and other contributors'],['Iconify "Logos" collection','CC0 1.0 (collection data)','gilbarbara/logos — the logos are trademarks of their owners'],['Simple Icons','CC0 1.0 (icon data)','simpleicons.org — the logos are trademarks of their owners']];
function tasksPage(b){
 const ts=listOf('tasks');const save=()=>K.pref.set('tasks',JSON.stringify(ts));
 let c=sec(b,K.t('tk_title'));
 row(c,{icon:'plus',title:K.t('tk_new'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('tk_new'),hint:K.t('tk_hint')});if(v){ts.unshift({t:v,d:0,ts:Date.now()});save();S.rerender()}}});
 const draw=(label,list)=>{if(!list.length)return;const h=document.createElement('div');h.className='sec-t';h.textContent=label;b.appendChild(h);const cc=document.createElement('div');cc.className='card';b.appendChild(cc);
  list.forEach(t=>{const r=row(cc,{html:'<span class="tk '+(t.d?'on':'')+'">'+K.icon('check',16,3)+'</span>',title:t.t});r.classList.toggle('tdone',!!t.d);
   r.onclick=async()=>{const k=await K.ui.choose(t.t.slice(0,40),[t.d?K.t('tk_open'):K.t('tk_done'),K.t('t_edit'),K.t('del')]);
    if(k===0){t.d=t.d?0:1;save();S.rerender()}
    else if(k===1){const v=await K.ui.prompt({title:K.t('t_edit'),value:t.t});if(v){t.t=v;save();S.rerender()}}
    else if(k===2){ts.splice(ts.indexOf(t),1);save();S.rerender()}}})};
 draw(K.t('tk_open'),ts.filter(t=>!t.d));draw(K.t('tk_done'),ts.filter(t=>t.d));
 if(!ts.length)note(b,K.t('tk_empty'));
}

function ossPage(b){
 const ar=K.lang()==='ar',T=(x,y)=>ar?x:y;
 const n=document.createElement('div');n.className='note';n.style.paddingTop='4px';
 n.textContent=T('يتصل التطبيق تلقائياً بنماذج مفتوحة المصدر (Llama وQwen وGemma وMistral وDeepSeek…). إن فشل نموذج أو مزوّد ينتقل للتالي بصمت دون أن تظهر لك مشكلة.','The app connects automatically to open-source models (Llama, Qwen, Gemma, Mistral, DeepSeek…). If one model or provider fails it silently moves to the next, with no error shown.');b.appendChild(n);
 let c=sec(b,T('الوضع التلقائي','Automatic mode'));
 toggle(c,{icon:'sparkles',title:T('الاتصال التلقائي بنماذج مفتوحة','Auto-connect to open models'),sub:T('بوابة عامة بدون مفتاح كخيار أخير (قد تكون بطيئة أو محدودة)','A public keyless gateway as a last resort (may be slow or limited)'),key:'auto',def:true});
 row(c,{icon:'circle-check',title:T('فحص كل المزوّدين الآن','Check every provider now'),sub:T('يعرض لكل مزوّد: يعمل أو سبب الفشل','Shows for each provider: working, or why it failed'),onclick:async()=>{
  K.toast(T('جارٍ الفحص… قد يستغرق دقيقة','Checking… this can take a minute'));
  let list=[];try{list=await K.native.diag()||[]}catch(e){}
  const okc=list.filter(x=>x.ok).length;
  const txt=list.length?list.map(x=>(x.ok?'✅ ':'❌ ')+x.name+(x.model?' ('+x.model+')':'')+' · '+x.ms+'ms'+(x.ok?'':'\n    '+String(x.msg||'').slice(0,160))).join('\n'):T('لا يوجد مزوّد مضبوط. فعّل «الاتصال التلقائي» أو أضف مفتاحاً.','No provider is configured. Turn on auto-connect or add a key.');
  K.ui.info(okc?T('يعمل '+okc+' من '+list.length,okc+' of '+list.length+' working'):T('لا يعمل أي مزوّد','No provider works'),txt+(okc?'':'\n\n'+T('أضف مفتاح Groq أو OpenRouter أو Gemini المجاني أدناه، فالبوابة العامة قد تكون محجوبة أو متوقفة.','Add a free Groq, OpenRouter or Gemini key below — the public gateway may be blocked or down.')))}}); c=sec(b,T('مفاتيح مجانية (اختياري — تزيد الثبات)','Free keys (optional — more stable)'));
 const L=[['groq','Groq','Llama · GPT-OSS','console.groq.com/keys'],['openrouter','OpenRouter','Llama · DeepSeek · Qwen (:free)','openrouter.ai/keys'],['hf','Hugging Face','Llama · DeepSeek · Qwen','huggingface.co/settings/tokens'],['cerebras','Cerebras','Llama · Qwen','cloud.cerebras.ai'],['mistral','Mistral','Mistral Nemo','console.mistral.ai/api-keys'],['gemini','Google AI Studio','Gemma','aistudio.google.com/apikey'],['github','GitHub Models','Llama · DeepSeek','github.com/settings/tokens']];
 L.forEach(x=>{const has=!!K.pref.get('fk_'+x[0],'');
  row(c,{icon:'key',title:x[1],sub:x[2]+' · '+x[3],value:has?T('مُضاف ✓','Added ✓'):'',onclick:async()=>{
   if(has){const k=await K.ui.choose(x[1],[T('تغيير المفتاح','Change key'),T('حذف المفتاح','Remove key')]);if(k===1){K.pref.set('fk_'+x[0],'');K.pref.set('auto_model_'+x[0],'');S.rerender()}if(k!==0)return}
   const v=await K.ui.prompt({title:x[1]+' — API key',secret:true,hint:'...'});if(v){K.pref.set('fk_'+x[0],v.trim());K.pref.set('auto_model_'+x[0],'');S.rerender()}}})});
 const n2=document.createElement('div');n2.className='note';n2.textContent=T('تُستخدم المفاتيح بالترتيب بعد مزوّدك الأساسي (إن وُجد). المفاتيح تُحفظ على جهازك فقط.','Keys are used in order after your main provider (if any). Keys are stored on your device only.');b.appendChild(n2);
}
function licensesPage(b){
 const c=sec(b,'');c.style.marginTop='6px';
 LIC.forEach(l=>row(c,{icon:'file-text',title:l[0],sub:l[1]+' · '+l[2]}));
 note(b,'Bawsala — 2.0');
}
function statsPage(b){
 const cv=K.db.convs.filter(c=>c.msgs.length),all=[].concat.apply([],cv.map(c=>c.msgs));
 const um=all.filter(m=>m.role==='user').length,am=all.filter(m=>m.role==='assistant').length;
 const words=all.reduce((n,m)=>n+((m.c||'').trim().split(/\s+/).filter(Boolean).length),0);
 const first=cv.length?new Date(Math.min.apply(null,cv.map(c=>c.ts))).toLocaleDateString(K.lang()==='ar'?'ar':'en'):'—';
 const c=sec(b,K.t('t_stats'));
 [['message-square','st_chats',cv.length],['user','st_msgs',um],['sparkles','st_replies',am],['file-text','st_words',words],['pin','st_pinned',cv.filter(x=>x.pinned).length],['clock','st_first',first]].forEach(r=>row(c,{icon:r[0],title:K.t(r[1]),value:String(r[2])}));
}
function listOf(key){try{return JSON.parse(K.pref.get(key,'[]'))}catch(e){return []}}
function memoryPage(b){
 const mem=listOf('memories');const c=sec(b,K.t('t_memory'));
 row(c,{icon:'plus',title:K.t('t_new_mem'),onclick:async()=>{const v=await K.ui.prompt({title:K.t('t_new_mem'),hint:K.t('t_mem_h'),multi:true});if(v){mem.push(v);K.pref.set('memories',JSON.stringify(mem));S.rerender()}}});
 mem.forEach((m,i)=>row(c,{icon:'database',title:m,onclick:async()=>{const k=await K.ui.choose(m.slice(0,40),[K.t('t_edit'),K.t('del')]);if(k===0){const v=await K.ui.prompt({title:K.t('t_edit'),value:m,multi:true});if(v){mem[i]=v;K.pref.set('memories',JSON.stringify(mem));S.rerender()}}else if(k===1){mem.splice(i,1);K.pref.set('memories',JSON.stringify(mem));S.rerender()}}}));
 note(b,K.t('t_memory_s'));
}
function promptsPage(b){
 const ps=listOf('prompts');const c=sec(b,K.t('t_prompts'));
 const save=()=>K.pref.set('prompts',JSON.stringify(ps));
 row(c,{icon:'plus',title:K.t('t_new_prompt'),onclick:async()=>{const t=await K.ui.prompt({title:K.t('t_prompt_title')});if(!t)return;const x=await K.ui.prompt({title:K.t('t_prompt_text'),multi:true});if(x){ps.push({t:t,x:x});save();S.rerender()}}});
 ps.forEach((p,i)=>row(c,{icon:'sparkles',title:p.t,sub:p.x,onclick:async()=>{const k=await K.ui.choose(p.t,[K.t('t_use'),K.t('t_edit'),K.t('del')]);
  if(k===0){while(S.depth())S.back();const ta=document.getElementById('ta');ta.value=p.x;ta.dispatchEvent(new Event('input'));ta.focus()}
  else if(k===1){const t=await K.ui.prompt({title:K.t('t_prompt_title'),value:p.t});if(!t)return;const x=await K.ui.prompt({title:K.t('t_prompt_text'),value:p.x,multi:true});if(x){ps[i]={t:t,x:x};save();S.rerender()}}
  else if(k===2){ps.splice(i,1);save();S.rerender()}}}));
 if(!ps.length)note(b,K.t('t_empty'));
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
