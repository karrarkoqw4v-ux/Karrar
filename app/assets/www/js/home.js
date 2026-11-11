/* Home screen: top bar, composer, attach sheet, drawer (history), context menu, select mode */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);const S=()=>K.state;
const esc=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const H=K.home={sel:[],selMode:false,drawerOpen:false,sheetOpen:false,q:''};

H.mount=function(){
 const root=$('home');root.innerHTML=
 '<div class="home" id="main">'
 +'<header class="topbar"><button class="icon-btn" id="bMenu"><svg class="ic flip-rtl" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 8h16M4 16h10"/></svg></button><div class="title" id="title"></div><button class="icon-btn" id="bSound"></button><button class="icon-btn" id="bNew">'+K.icon('message-circle-plus',22)+'</button></header>'
 +'<div class="scroll chat" id="scroll"><div class="empty" id="empty"><div class="logo-big">'+K.LOGO+'</div><h2>'+K.t('hi')+'</h2><div class="sugg" id="sugg"></div></div><div class="msgs" id="msgs"></div></div>'
 +'<div class="composer-wrap" id="cwrap"><div class="pmenu" id="pmenu"><div class="gal" id="gal"></div><div class="attrow"><button id="aCam">'+K.icon('camera',22)+'<span>'+K.t('camera')+'</span></button><button id="aAlb">'+K.icon('image',22)+'<span>'+K.t('album')+'</span></button><button id="aFile">'+K.icon('paperclip',22)+'<span>'+K.t('file')+'</span></button></div>'
 +'<div class="mlist"><button class="mi" id="mImg">'+K.icon('image',24)+'<span>'+K.t('create_img')+'</span><i class="mk">'+K.icon('check',20,2.6)+'</i></button>'
 +[['code-xml','dev_apps'],['graduation-cap','student_tools'],['bot','agent_tasks']].map(x=>x[1]==='agent_tasks'?'<button class="mi" data-act="agent">'+K.icon(x[0],24)+'<span>'+(K.lang()==='ar'?'وكيل المهام':'Task agent')+'</span></button>':x[1]==='student_tools'?'<button class="mi" data-act="students">'+K.icon(x[0],24)+'<span>'+K.t(x[1])+'</span></button>':x[1]==='dev_apps'?'<button class="mi" data-act="code">'+K.icon(x[0],24)+'<span>'+K.t(x[1])+'</span></button>':'<button class="mi soon">'+K.icon(x[0],24)+'<span>'+K.t(x[1])+'</span>'+(x[2]?'<i class="badge pro">'+K.icon('crown',12,2.4)+'Pro</i>':'')+'<i class="badge">'+K.t('soon')+'</i></button>').join('')
 +'<button class="mi" id="mThink">'+K.icon('atom',24)+'<span>'+K.t('think')+'</span><i class="mk">'+K.icon('check',20,2.6)+'</i></button><button class="mi" id="mSearch">'+K.icon('globe',24)+'<span>'+K.t('web_search')+'</span><i class="mk">'+K.icon('check',20,2.6)+'</i></button><button class="mi" id="mApps">'+K.icon('plug',24)+'<span>'+K.t('connected_apps')+'</span></button></div></div>'
 +'<div class="mchips" id="mchips"></div>'
 +'<div class="bar" id="composer"><div class="previews" id="previews"></div><div class="brow"><button class="bplus" id="bPlus">'+K.icon('plus',27)+'</button><button class="bmodel" id="bModel"></button><textarea id="ta" rows="1" placeholder="'+esc(K.t('hint'))+'"></textarea><button class="bmic" id="bMic">'+K.icon('mic',23)+'</button><button class="bblue" id="bSend"></button></div></div></div>'
 +'<div class="scrim" id="scrim"></div>'
 +'<aside class="drawer" id="drawer"><div class="dhead"><b class="dbrand">Bawsala</b><button class="dcirc" id="dSr">'+K.icon('search',24)+'</button></div><div class="dsearch" id="dsb">'+K.icon('search',22)+'<input id="dq" placeholder="'+esc(K.t('search_chats'))+'"></div><div class="dnav" id="dnav"></div><div class="dproj" id="dproj"></div><div class="dlist" id="dlist"></div>'
 +'<button class="delbar hidden" id="delbar"></button><div class="dprof press" id="dprof"><div class="av"><svg viewBox="0 0 24 24"><circle cx="12" cy="9.4" r="3.9" fill="#8e8e93"/><path d="M4.6 20Q5.5 14 12 14t7.400 6Q16 23 12 23T4.600 20z" fill="#8e8e93"/></svg></div><b id="pname"></b><span class="mo">'+K.icon('ellipsis',26)+'</span></div></aside>';
 K.chat.mount();
 $('bMenu').onclick=()=>H.openDrawer();
 $('bNew').onclick=()=>H.newChat();
 $('bSound').onclick=()=>K.chatui.menu($('bSound'));
 $('mImg').onclick=()=>{H.toggleSheet(false);if(S().imgMode){S().imgMode=false;H.paintPills();$('ta').placeholder=K.t('hint')}else K.imggen.enter();K.native.haptic()};
 $('mThink').onclick=()=>{S().think=!S().think;H.paintPills();H.toggleSheet(false);K.native.haptic()};
 $('mSearch').onclick=()=>{S().search=!S().search;H.paintPills();H.toggleSheet(false);K.native.haptic()};
 $('pmenu').querySelectorAll('.mi.soon').forEach(bn=>bn.onclick=()=>K.toast(bn.querySelector('span').textContent+' — '+K.t('soon')));
 $('mApps').onclick=()=>{H.toggleSheet(false);K.settings.open('apps')};
 $('bPlus').onclick=()=>H.toggleSheet(!H.sheetOpen);
 $('bSend').onclick=()=>H.sendOrVoice();
 $('bMic').onclick=()=>K.dictate.start();
 const ta=$('ta');ta.addEventListener('input',()=>{ta.style.height='auto';const lh=parseFloat(getComputedStyle(ta).lineHeight)||24;const bar=$('composer');
  if(!ta.value){bar.classList.remove('multi')}else if(ta.scrollHeight>lh*1.6)bar.classList.add('multi');
  ta.style.height=Math.min(ta.scrollHeight,150)+'px';H.refreshComposer()});
ta.addEventListener('focus',()=>{H.toggleSheet(false);$('composer').classList.add('multi')});
 ta.addEventListener('blur',()=>setTimeout(()=>{if(document.activeElement!==ta&&!ta.value&&!S().atts.length&&!H.sheetOpen)$('composer').classList.remove('multi')},220));
 ta.addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey&&K.pref.bool('enter_send',false)){e.preventDefault();K.chat.send()}});
 $('aCam').onclick=async()=>{H.toggleSheet(false);const r=await K.native.camera();(r||[]).forEach(i=>H.addAtt({kind:'img',th:i.th,b64:i.b64}))};
 $('aAlb').onclick=async()=>{H.toggleSheet(false);const r=await K.native.pickImages();(r||[]).forEach(i=>H.addAtt({kind:'img',th:i.th,b64:i.b64}))};
 $('aFile').onclick=async()=>{H.toggleSheet(false);const f=await K.native.pickFile();if(!f||!f.name)return;
  if(f.tooBig){K.toast(K.t('f_toobig'));return}
  const rd=document.createElement('div');rd.className='reading';rd.innerHTML='<i></i><span>'+K.t('f_reading')+'</span>';$('previews').appendChild(rd);
  const r=await K.files.parse(f);rd.remove();
  if(r.err){K.toast(K.t(r.err));H.refreshComposer();return}
  H.addAtt({kind:'file',name:f.name,ext:K.files.ext(f.name),size:f.size,text:r.text,pages:r.pages})};
 H.buildProjects();H.buildNav();H.buildSugg();K.models.paint();
 $('bModel').onclick=()=>K.models.open();
 $('bSend').addEventListener('pointerdown',()=>{const b=$('bSend');['','r2'].forEach(c=>{const r=document.createElement('i');r.className='bb-rp '+c;b.appendChild(r);setTimeout(()=>r.remove(),1200)})});
 $('dSr').onclick=()=>{const on=!$('dsb').classList.contains('open');$('dsb').classList.toggle('open',on);$('drawer').classList.toggle('searching',on);if(on)setTimeout(()=>$('dq').focus(),200);else{$('dq').value='';H.q='';H.renderList()}};
 $('scrim').onclick=()=>H.closeDrawer();
 $('dq').oninput=e=>{H.q=e.target.value.trim().toLowerCase();H.renderList()};
 $('dprof').onclick=()=>{H.closeDrawer();K.settings.open('main')};
 $('delbar').onclick=()=>H.deleteSel();
 // swipe from edge to open drawer
 let sx=0,sy=0;root.addEventListener('touchstart',e=>{sx=e.touches[0].clientX;sy=e.touches[0].clientY},{passive:true});
 root.addEventListener('touchend',e=>{const dx=e.changedTouches[0].clientX-sx,dy=e.changedTouches[0].clientY-sy;if(Math.abs(dx)<70||Math.abs(dx)<Math.abs(dy)*2)return;
  const rtl=document.documentElement.dir==='rtl',opening=rtl?dx<0:dx>0,edge=rtl?sx>innerWidth-48:sx<48;
  if(!H.drawerOpen&&opening&&edge)H.openDrawer();else if(H.drawerOpen&&!opening)H.closeDrawer()},{passive:true});
 H.paintSound();H.paintPills();H.refreshComposer();
};
H.paintSound=function(){$('bSound').innerHTML='<svg class="ic" width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="1.9"/><circle cx="12" cy="12" r="1.9"/><circle cx="12" cy="19" r="1.9"/></svg>'};
H.paintPills=function(){
 $('mImg').classList.toggle('on',!!S().imgMode);$('mThink').classList.toggle('on',S().think);$('mSearch').classList.toggle('on',S().search);
 const c=$('mchips');c.classList.toggle('enc',!!(S().cur&&S().cur.enc));c.innerHTML=(S().cur&&S().cur.enc?K.enc.chips(S().cur.enc):'')+(S().imgMode?K.imggen.chips():'')+(S().think?'<button class="mchip" data-m="think">'+K.icon('atom',16)+'<span>'+K.t('think')+'</span><span class="xx">'+K.icon('x',14,2.6)+'</span></button>':'')+(S().search?'<button class="mchip" data-m="search">'+K.icon('globe',16)+'<span>'+K.t('web_search')+'</span><span class="xx">'+K.icon('x',14,2.6)+'</span></button>':'');
 const md=S().cur&&S().cur.mode;if(md&&MODES[md])c.insertAdjacentHTML('afterbegin','<button class="mchip" data-md="1">'+K.icon(MODES[md][0],16)+'<span>'+K.t(MODES[md][1])+'</span><span class="xx">'+K.icon('x',14,2.6)+'</span></button>');
 const repos=K.gh.tok()?K.gh.sel():[];
 c.insertAdjacentHTML('beforeend',repos.map(r=>'<button class="mchip" data-r="'+r.replace(/"/g,'')+'">'+K.icon('git-branch',16)+'<span>'+r.split('/').pop()+'</span><span class="xx">'+K.icon('x',14,2.6)+'</span></button>').join(''));
 c.querySelectorAll('.mchip').forEach(b=>b.onclick=()=>{if(b.dataset.enc){K.enc.run(b.dataset.enc);return}if(b.dataset.img){K.imggen.onChip(b.dataset.img,b);return}if(b.dataset.md){S().cur.mode=null}else if(b.dataset.r){K.gh.toggle(b.dataset.r)}else if(b.dataset.m==='think')S().think=false;else S().search=false;H.paintPills();K.native.haptic()});
};
H.refreshComposer=function(){
 if(K.voice&&K.voice.refreshAtts)setTimeout(K.voice.refreshAtts,0);
 const ta=$('ta'),has=ta.value.trim().length>0||S().atts.length>0,b=$('bSend');
 let icon='audio-lines';if(has)icon='arrow-up';else if(S().streaming)icon='square';
 if(b.dataset.s!==icon){b.dataset.s=icon;b.innerHTML=K.icon(icon,icon==='square'?15:21);b.classList.remove('pulse');void b.offsetWidth;if(icon!=='audio-lines')b.classList.add('pulse')}
 if(!ta.value&&!S().atts.length)$('composer').classList.remove('multi');else if(S().atts.length)$('composer').classList.add('multi');
 const pv=$('previews');pv.innerHTML=S().atts.map((a,i)=>'<div class="pv">'+(a.kind==='img'?'<img src="'+a.th+'">':K.files.card(a,'sm'))+'<button class="x" data-i="'+i+'">'+K.icon('x',13,3)+'</button></div>').join('');
 pv.querySelectorAll('.x').forEach(x=>x.onclick=()=>{S().atts.splice(+x.dataset.i,1);H.refreshComposer();H.markGallery()});
};
H.addAtt=function(a){S().atts.push(a);H.refreshComposer()};
/* empty bar: blue wave button = voice conversation (speak, then the answer is read aloud) */
H.sendOrVoice=function(){
 const has=$('ta').value.trim().length>0||S().atts.length>0;
 if(S().streaming||has){K.chat.send();return}
 K.voice.open();
};
/* ---- attach sheet ---- */
H.toggleSheet=function(open){
 H.sheetOpen=open;$('pmenu').classList.toggle('open',open);$('bPlus').classList.toggle('open',open);
 if(open){document.activeElement&&document.activeElement.blur();H.loadGallery()}
};
H.closeSheet=()=>H.toggleSheet(false);
H.loadGallery=async function(){
 const g=$('gal');if(g.dataset.loaded&&g.children.length>1)return;g.innerHTML='';
 const r=await K.native.gallery();
 if(!r||!r.perm){g.innerHTML='<div class="note">'+K.t('perm_needed')+'</div>';return}
 g.dataset.loaded='1';
 r.items.forEach((it,i)=>{const d=document.createElement('div');d.className='g';d.style.animationDelay=i*40+'ms';d.dataset.uri=it.uri;d.innerHTML='<img src="'+it.th+'"><i>'+K.icon('check',16,3)+'</i>';
  d.onclick=async()=>{const on=d.classList.toggle('sel');
   if(on){const a={kind:'img',th:it.th,b64:'',uri:it.uri};H.addAtt(a);const x=await K.native.imageData(it.uri);if(x&&x.b64)a.b64=x.b64}
   else{S().atts=S().atts.filter(a=>a.uri!==it.uri);H.refreshComposer()}};
  g.appendChild(d)});
};
H.markGallery=function(){document.querySelectorAll('#gal .g').forEach(g=>g.classList.toggle('sel',S().atts.some(a=>a.uri===g.dataset.uri)))};
/* ---- drawer ---- */
const MODES={apps:['code-xml','proj_apps','#2563eb'],biz:['rocket','proj_biz','#16a34a'],web:['globe','proj_web','#ea580c']};
K.MODES=MODES;
H.buildProjects=function(){
 const d=$('dproj');d.innerHTML=''
  +'<button class="pj" data-p="tasks"><span class="pji" style="--c:#f59e0b">'+K.icon('list-checks',20)+'</span><span>'+K.t('proj_task')+'</span></button>'
  +Object.keys(MODES).map(k=>'<button class="pj" data-p="'+k+'"><span class="pji" style="--c:'+MODES[k][2]+'">'+K.icon(MODES[k][0],20)+'</span><span>'+K.t(MODES[k][1])+'</span></button>').join('');
 d.querySelectorAll('.pj').forEach(b=>b.onclick=()=>{const p=b.dataset.p;
  if(p==='tasks'){H.closeDrawer();K.settings.open('tasks')}
  else{H.newChat();S().cur.mode=p;H.paintPills();$('ta').focus()}});
};

H.buildNav=function(){
 const T=(a,e)=>K.lang()==='ar'?a:e;
 const it=[['image',T('الصور','Images'),'img'],['code-xml',T('برمجة التطبيقات','Code'),'code'],['shield-check',T('الأمان والفحص','Security & Scan'),'scan'],['clock',T('المهام المجدولة','Scheduled tasks'),'tasks'],['graduation-cap',T('الطلاب','Students'),'students'],['bot',T('وكيل المهام','Task agent'),'agent'],['plug',T('المكونات الإضافية','Plugins'),'plug']];
 const n=$('dnav');n.innerHTML=it.map((x,i)=>'<button class="dn" data-k="'+x[2]+'" style="--i:'+i+'">'+K.icon(x[0],26)+'<span>'+x[1]+'</span>'+(x[3]?'<i class="badge">'+K.t('soon')+'</i>':'')+'</button>').join('');
 n.querySelectorAll('.dn').forEach(b=>b.onclick=()=>{const k=b.dataset.k;
  if(k==='proj'){$('dproj').classList.toggle('open');b.classList.toggle('open');return}
  if(k==='lib'){K.toast(T('قريباً','Coming soon'));return}
  H.closeDrawer();
  if(k==='img')K.gallery.open();else if(k==='code')K.code.open();else if(k==='scan')K.scan.open();else if(k==='agent')K.agent.open();else if(k==='students')K.students.open();else if(k==='tasks')K.settings.open('tasks');else if(k==='plug')K.settings.open('apps')});
};
H.buildSugg=function(){
 const T=(a,e)=>K.lang()==='ar'?a:e;
 const L=[['1f4c4',T('أرسل لي ملف','Send me a file'),T('أرسل لي ملف notes.txt فيه 5 أفكار لتطبيقات يمكنني بناؤها','Send me a file notes.txt with 5 app ideas I could build')],
  ['1f4e6',T('مشروع zip','A zip project'),T('اعمل لي ملف zip لموقع بسيط فيه index.html وstyle.css وapp.js','Make me a zip of a simple website with index.html, style.css and app.js')],
  ['1f4dd',T('تقرير docx','A docx report'),T('اكتب لي تقريراً قصيراً عن أهمية القراءة بصيغة docx وأرسله كملف','Write a short report on why reading matters as a docx and send it as a file')]];
 const e=$('sugg');e.innerHTML=L.map((x,i)=>'<button class="sg" style="--i:'+i+'"><img src="emoji/'+x[0]+'.svg" alt=""><span>'+x[1]+'</span></button>').join('');
 e.querySelectorAll('.sg').forEach((b,i)=>b.onclick=()=>{if(S().streaming)return;const ta=$('ta');ta.value=L[i][2];ta.dispatchEvent(new Event('input'));K.chat.send()});
};
H.openDrawer=function(){H.renderList();$('pname').textContent=K.pref.get('name','')||K.t('guest');
 H.drawerOpen=true;$('drawer').classList.add('open');$('scrim').classList.add('show');$('main').classList.add('shift');document.activeElement&&document.activeElement.blur()};
H.closeDrawer=function(){if(!H.drawerOpen)return;H.drawerOpen=false;if(H.selMode)H.exitSel();$('drawer').classList.remove('open');$('scrim').classList.remove('show');$('main').classList.remove('shift')};
H.renderList=function(){
 const box=$('dlist');box.innerHTML='';const q=H.q;
 let all=K.db.convs.filter(c=>c.msgs.length&&!c.enc&&!c.stu&&!c.archived&&!c.agentTest&&(!q||c.title.toLowerCase().includes(q)||c.msgs.some(m=>(m.c||'').toLowerCase().includes(q))));
 all.sort((a,b)=>(b.pinned?1:0)-(a.pinned?1:0)||b.ts-a.ts);
 if(!all.length){box.innerHTML='<div class="dempty">'+K.t('no_history')+'</div>';return}
 const now=Date.now();let last='',first=true;const frag=document.createDocumentFragment();let idx=0;
 all.forEach(c=>{const age=now-c.ts;const g=c.pinned?K.t('pinned'):age<864e5?K.t('today'):age<6048e5?K.t('week'):age<2592e6?K.t('month'):K.t('earlier');
  if(g!==last){last=g;const gh=document.createElement('div');gh.className='dgroup';gh.innerHTML='<span>'+g+'</span>'+(first?'<button class="icon-btn" data-ms>'+K.icon('list-checks',22)+'</button>':'<span style="width:36px"></span>');
   if(first){gh.querySelector('[data-ms]').onclick=()=>H.enterSel(null)}first=false;frag.appendChild(gh)}
  const it=document.createElement('div');const sel=H.sel.includes(c);
  it.className='ditem'+(sel?' sel':'')+(c===S().cur&&!H.selMode?' cur':'');it.style.animation='fadeUp .4s '+Math.min(idx++,10)*30+'ms var(--ease) both';
  it.innerHTML=(H.selMode?'<span class="ck">'+(sel?K.icon('check',14,3.5):'')+'</span>':'')+'<span class="t"><b></b><small></small></span>'+(c.code?'<span class="pn cdb">'+K.icon('code-xml',17)+'</span>':'')+(c.locked?'<span class="pn">'+K.icon('lock',16)+'</span>':'')+(c.pinned?'<span class="pn">'+K.icon('pin',17)+'</span>':'');
  it.querySelector('.t b').textContent=c.title||K.t('new_chat');
  {const lm=[...c.msgs].reverse().find(m=>m.role==='assistant'&&m.c)||c.msgs[c.msgs.length-1]||{};const pv=(lm.c||'').replace(/```[\s\S]*?```/g,' ⟨code⟩ ').replace(/[#*`>|_\[\]]/g,'').replace(/\s+/g,' ').trim().slice(0,70);
   const d=new Date(c.ts),tm=age<864e5?d.toLocaleTimeString(K.lang()==='ar'?'ar':'en',{hour:'2-digit',minute:'2-digit'}):d.toLocaleDateString(K.lang()==='ar'?'ar':'en',{day:'numeric',month:'short'});
   it.querySelector('.t small').textContent=tm+(pv?' · '+pv:'')}
  it.onclick=()=>{if(H.selMode){const i=H.sel.indexOf(c);if(i>=0)H.sel.splice(i,1);else H.sel.push(c);H.updSel();H.renderList()}else H.openConv(c)};
  let tm=0,moved=false;it.addEventListener('touchstart',e=>{moved=false;tm=setTimeout(()=>{if(!moved&&!H.selMode){K.native.haptic();H.ctxMenu(it,c)}},480)},{passive:true});
  it.addEventListener('touchmove',()=>{moved=true;clearTimeout(tm)},{passive:true});it.addEventListener('touchend',()=>clearTimeout(tm));
  it.addEventListener('contextmenu',e=>{e.preventDefault();if(!H.selMode)H.ctxMenu(it,c)});
  frag.appendChild(it)});
 box.appendChild(frag);K.emojifyEl(box);
};
H.ctxMenu=function(anchor,c){
 H.closeCtx();const m=document.createElement('div');m.className='ctx';m.id='ctx';
 const items=[['pencil',K.t('rename'),'r'],[c.pinned?'pin-off':'pin',c.pinned?K.t('unpin'):K.t('pin'),'p'],['list-checks',K.t('multi'),'m'],['trash-2',K.t('del'),'d']];
 m.innerHTML=items.map(i=>'<button data-k="'+i[2]+'"><span>'+i[1]+'</span>'+K.icon(i[0],24)+'</button>').join('');
 const r=anchor.getBoundingClientRect(),rtl=document.documentElement.dir==='rtl';
 m.style.top=Math.min(r.bottom+4,innerHeight-260)+'px';m.style[rtl?'right':'left']='24px';
 const bd=document.createElement('div');bd.className='backdrop';bd.style.background='transparent';bd.id='ctxbd';bd.style.zIndex=59;bd.onclick=H.closeCtx;
 document.body.appendChild(bd);document.body.appendChild(m);
 m.onclick=async e=>{const b=e.target.closest('button');if(!b)return;H.closeCtx();const k=b.dataset.k;
  if(k==='r'){const v=await K.ui.prompt({title:K.t('rename'),value:c.title});if(v){c.title=v;K.db.save();H.renderList();if(c===S().cur)K.fx.type($('title'),v,34)}}
  else if(k==='p'){c.pinned=!c.pinned;K.db.save();H.renderList()}
  else if(k==='m')H.enterSel(c);
  else{K.db.remove(c);if(c===S().cur)H.newChat();else H.renderList()}};
};
H.closeCtx=function(){['ctx','ctxbd'].forEach(i=>{const e=$(i);if(e)e.remove()})};
H.enterSel=function(c){H.selMode=true;H.sel=c?[c]:[];H.updSel();H.renderList()};
H.exitSel=function(){H.selMode=false;H.sel=[];H.updSel();H.renderList()};
H.updSel=function(){const b=$('delbar');b.classList.toggle('hidden',!H.selMode);b.textContent=K.t('del')+' ('+H.sel.length+')';$('dprof').classList.toggle('hidden',H.selMode)};
H.deleteSel=function(){if(!H.sel.length){H.exitSel();return}const gone=H.sel.includes(S().cur);H.sel.forEach(c=>{const i=K.db.convs.indexOf(c);if(i>=0)K.db.convs.splice(i,1)});K.db.save();H.exitSel();if(gone)H.newChat()};
H.openConv=function(c){const go=function(){if(S().streaming)K.chat.stop();S().cur=c;K.pref.set('cur',c.id);K.chat.show(c);H.closeDrawer()};if(c.locked&&K.lock.has())K.lock.show('verify',ok=>{if(ok)go()});else go()};
H.newChat=function(){if(S().streaming)K.chat.stop();const c=K.db.create();S().cur=c;K.pref.set('cur',c.id);K.chat.show(c);H.closeDrawer();H.closeSheet()};
H.start=function(){
 S().think=K.pref.bool('def_think',false);S().search=K.pref.bool('def_search',false);H.paintPills();
 K.db.load();let c=K.db.find(K.pref.get('cur',''));if(!c)c=K.db.create();S().cur=c;K.chat.show(c);
};
})();
