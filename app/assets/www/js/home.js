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
 +'<div class="scroll chat" id="scroll"><div class="empty" id="empty"><div class="logo-big">'+K.LOGO+'</div><h2>'+K.t('hi')+'</h2></div><div class="msgs" id="msgs"></div></div>'
 +'<div class="composer-wrap" id="cwrap"><div class="pmenu" id="pmenu"><div class="gal" id="gal"></div><div class="attrow"><button id="aCam">'+K.icon('camera',22)+'<span>'+K.t('camera')+'</span></button><button id="aAlb">'+K.icon('image',22)+'<span>'+K.t('album')+'</span></button><button id="aFile">'+K.icon('paperclip',22)+'<span>'+K.t('file')+'</span></button></div>'
 +'<div class="mlist"><button class="mi soon" id="mImg">'+K.icon('image',24)+'<span>'+K.t('create_img')+'</span><i class="badge">'+K.t('soon')+'</i></button><button class="mi" id="mThink">'+K.icon('atom',24)+'<span>'+K.t('think')+'</span><i class="mk">'+K.icon('check',20,2.6)+'</i></button><button class="mi" id="mSearch">'+K.icon('globe',24)+'<span>'+K.t('web_search')+'</span><i class="mk">'+K.icon('check',20,2.6)+'</i></button></div></div>'
 +'<div class="mchips" id="mchips"></div>'
 +'<div class="bar" id="composer"><div class="previews" id="previews"></div><div class="brow"><button class="bplus" id="bPlus">'+K.icon('plus',27)+'</button><textarea id="ta" rows="1" placeholder="'+esc(K.t('hint'))+'"></textarea><button class="bmic" id="bMic">'+K.icon('mic',23)+'</button><button class="bblue" id="bSend"></button></div></div></div>'
 +'<div class="scrim" id="scrim"></div>'
 +'<aside class="drawer" id="drawer"><div class="dsearch">'+K.icon('search',24)+'<input id="dq" placeholder="'+esc(K.t('search_chats'))+'"></div><div class="dlist" id="dlist"></div>'
 +'<button class="delbar hidden" id="delbar"></button><div class="dprof press" id="dprof"><div class="av"><svg viewBox="0 0 24 24"><circle cx="12" cy="9.4" r="3.9" fill="#8e8e93"/><path d="M4.6 20Q5.5 14 12 14t7.400 6Q16 23 12 23T4.600 20z" fill="#8e8e93"/></svg></div><b id="pname"></b><span class="mo">'+K.icon('ellipsis',26)+'</span></div></aside>';
 K.chat.mount();
 $('bMenu').onclick=()=>H.openDrawer();
 $('bNew').onclick=()=>H.newChat();
 $('bSound').onclick=()=>{const on=!K.pref.bool('sound',false);K.pref.setBool('sound',on);if(!on)K.native.stopSpeak();H.paintSound();K.toast(K.t('s_sound')+(on?' ✓':' ✕'))};
 $('mThink').onclick=()=>{S().think=!S().think;H.paintPills();H.toggleSheet(false);K.native.haptic()};
 $('mSearch').onclick=()=>{S().search=!S().search;H.paintPills();H.toggleSheet(false);K.native.haptic()};
 $('mImg').onclick=()=>K.toast(K.t('create_img')+' — '+K.t('soon'));
 $('bPlus').onclick=()=>H.toggleSheet(!H.sheetOpen);
 $('bSend').onclick=()=>H.sendOrVoice();
 $('bMic').onclick=async()=>{const t=await K.native.voice();if(t){const ta=$('ta');ta.value+=(ta.value?' ':'')+t;ta.dispatchEvent(new Event('input'))}else if(K.native.real)K.toast(K.t('voice_fail'))};
 const ta=$('ta');ta.addEventListener('input',()=>{ta.style.height='auto';const lh=parseFloat(getComputedStyle(ta).lineHeight)||24;const bar=$('composer');
  if(!ta.value){bar.classList.remove('multi')}else if(ta.scrollHeight>lh*1.6)bar.classList.add('multi');
  ta.style.height=Math.min(ta.scrollHeight,150)+'px';H.refreshComposer()});
 ta.addEventListener('focus',()=>H.toggleSheet(false));
 $('aCam').onclick=async()=>{H.toggleSheet(false);const r=await K.native.camera();(r||[]).forEach(i=>H.addAtt({kind:'img',th:i.th,b64:i.b64}))};
 $('aAlb').onclick=async()=>{H.toggleSheet(false);const r=await K.native.pickImages();(r||[]).forEach(i=>H.addAtt({kind:'img',th:i.th,b64:i.b64}))};
 $('aFile').onclick=async()=>{H.toggleSheet(false);const f=await K.native.pickFile();if(f&&f.name)H.addAtt({kind:'file',name:f.name,text:f.text||''})};
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
H.paintSound=function(){$('bSound').innerHTML=K.icon(K.pref.bool('sound',false)?'volume-2':'volume-x',22)};
H.paintPills=function(){
 $('mThink').classList.toggle('on',S().think);$('mSearch').classList.toggle('on',S().search);
 const c=$('mchips');c.innerHTML=(S().think?'<button class="mchip" data-m="think">'+K.icon('atom',16)+'<span>'+K.t('think')+'</span><span class="xx">'+K.icon('x',14,2.6)+'</span></button>':'')+(S().search?'<button class="mchip" data-m="search">'+K.icon('globe',16)+'<span>'+K.t('web_search')+'</span><span class="xx">'+K.icon('x',14,2.6)+'</span></button>':'');
 c.querySelectorAll('.mchip').forEach(b=>b.onclick=()=>{if(b.dataset.m==='think')S().think=false;else S().search=false;H.paintPills();K.native.haptic()});
};
H.refreshComposer=function(){
 const ta=$('ta'),has=ta.value.trim().length>0||S().atts.length>0,b=$('bSend');
 let icon='audio-lines';if(S().streaming)icon='square';else if(has)icon='arrow-up';
 if(b.dataset.s!==icon){b.dataset.s=icon;b.innerHTML=K.icon(icon,icon==='square'?15:21);b.classList.remove('pulse');void b.offsetWidth;if(icon!=='audio-lines')b.classList.add('pulse')}
 if(!ta.value)$('composer').classList.remove('multi');
 const pv=$('previews');pv.innerHTML=S().atts.map((a,i)=>'<div class="pv">'+(a.kind==='img'?'<img src="'+a.th+'">':'<div class="fchip">'+K.icon('file-text',22)+'<span>'+esc(a.name)+'</span></div>')+'<button class="x" data-i="'+i+'">'+K.icon('x',13,3)+'</button></div>').join('');
 pv.querySelectorAll('.x').forEach(x=>x.onclick=()=>{S().atts.splice(+x.dataset.i,1);H.refreshComposer();H.markGallery()});
};
H.addAtt=function(a){S().atts.push(a);H.refreshComposer()};
/* empty bar: blue wave button = voice conversation (speak, then the answer is read aloud) */
H.sendOrVoice=async function(){
 const has=$('ta').value.trim().length>0||S().atts.length>0;
 if(S().streaming||has){K.chat.send();return}
 const t=await K.native.voice();
 if(t){$('ta').value=t;S().voiceReply=true;$('ta').dispatchEvent(new Event('input'));K.chat.send()}else if(K.native.real)K.toast(K.t('voice_fail'));
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
H.openDrawer=function(){H.renderList();$('pname').textContent=K.pref.get('name','')||K.t('guest');
 H.drawerOpen=true;$('drawer').classList.add('open');$('scrim').classList.add('show');$('main').classList.add('shift');document.activeElement&&document.activeElement.blur()};
H.closeDrawer=function(){if(!H.drawerOpen)return;H.drawerOpen=false;if(H.selMode)H.exitSel();$('drawer').classList.remove('open');$('scrim').classList.remove('show');$('main').classList.remove('shift')};
H.renderList=function(){
 const box=$('dlist');box.innerHTML='';const q=H.q;
 let all=K.db.convs.filter(c=>c.msgs.length&&(!q||c.title.toLowerCase().includes(q)||c.msgs.some(m=>(m.c||'').toLowerCase().includes(q))));
 all.sort((a,b)=>(b.pinned?1:0)-(a.pinned?1:0)||b.ts-a.ts);
 if(!all.length){box.innerHTML='<div class="dempty">'+K.t('no_history')+'</div>';return}
 const now=Date.now();let last='',first=true;const frag=document.createDocumentFragment();let idx=0;
 all.forEach(c=>{const age=now-c.ts;const g=c.pinned?K.t('pinned'):age<864e5?K.t('today'):age<6048e5?K.t('week'):age<2592e6?K.t('month'):K.t('earlier');
  if(g!==last){last=g;const gh=document.createElement('div');gh.className='dgroup';gh.innerHTML='<span>'+g+'</span>'+(first?'<button class="icon-btn" data-ms>'+K.icon('list-checks',22)+'</button>':'<span style="width:36px"></span>');
   if(first){gh.querySelector('[data-ms]').onclick=()=>H.enterSel(null)}first=false;frag.appendChild(gh)}
  const it=document.createElement('div');const sel=H.sel.includes(c);
  it.className='ditem'+(sel?' sel':'')+(c===S().cur&&!H.selMode?' cur':'');it.style.animation='fadeUp .4s '+Math.min(idx++,10)*30+'ms var(--ease) both';
  it.innerHTML=(H.selMode?'<span class="ck">'+(sel?K.icon('check',14,3.5):'')+'</span>':'')+'<span class="t"></span>'+(c.pinned?'<span class="pn">'+K.icon('pin',17)+'</span>':'');
  it.querySelector('.t').textContent=c.title||K.t('new_chat');
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
H.openConv=function(c){if(S().streaming)K.chat.stop();S().cur=c;K.pref.set('cur',c.id);K.chat.show(c);H.closeDrawer()};
H.newChat=function(){if(S().streaming)K.chat.stop();const c=K.db.create();S().cur=c;K.pref.set('cur',c.id);K.chat.show(c);H.closeDrawer();H.closeSheet()};
H.start=function(){
 S().think=K.pref.bool('def_think',false);S().search=K.pref.bool('def_search',false);H.paintPills();
 K.db.load();let c=K.db.find(K.pref.get('cur',''));if(!c)c=K.db.create();S().cur=c;K.chat.show(c);
};
})();
