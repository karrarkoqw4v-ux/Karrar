/* Chat toolbar: ⋮ menu (share, pin, lock, files, search, archive, delete) + read-aloud bar */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const S=()=>K.state;
let bar=null,tick=0,t0=0,cur='',paused=false;
const au=document.createElement('div');au.id='aurora';document.body.insertBefore(au,document.body.firstChild);
setInterval(()=>{const on=!!(S()&&S().streaming);au.classList.toggle('on',on);document.body.classList.toggle('aur',on)},300);
document.addEventListener('click',e=>{if(e.target.closest&&e.target.closest('#title')&&!e.target.closest('#tBrand'))K.models.open()});
K.chatui={
 close(){['cmenu','cmenubd'].forEach(i=>{const e=$(i);if(e)e.remove()})},
 menu(anchor){
  K.chatui.close();const c=S().cur;if(!c)return;
  const items=[['share',T('مشاركة','Share'),'sh'],[c.pinned?'pin-off':'pin',c.pinned?T('إلغاء التثبيت','Unpin'):T('تثبيت','Pin'),'pin'],['lock',c.locked?T('إلغاء قفل الدردشة','Unlock chat'):T('قفل الدردشة','Lock chat'),'lock'],
   ['folder-git-2',T('إضافة إلى المشروع','Add to project'),'proj'],['paperclip',T('الملفات التي تم تحميلها','Uploaded files'),'files'],['search',T('البحث في الدردشة','Search in chat'),'find'],
   [K.pref.bool('sound',false)?'volume-2':'volume-x',T('قراءة الردود تلقائياً: ','Auto read replies: ')+(K.pref.bool('sound',false)?T('مفعّل','On'):T('متوقف','Off')),'snd'],
   ['archive',T('أرشفة','Archive'),'arc'],['trash-2',T('حذف','Delete'),'del']];
  const m=document.createElement('div');m.className='ctx cmenu';m.id='cmenu';
  m.innerHTML='<div class="cm-t">'+(c.title?c.title.replace(/[<>&]/g,''):T('محادثة جديدة','New chat'))+'</div>'+items.map(i=>'<button data-k="'+i[2]+'" class="'+(i[2]==='del'?'danger':'')+'"><span>'+i[1]+'</span>'+K.icon(i[0],22)+'</button>').join('');
  const bd=document.createElement('div');bd.className='backdrop';bd.style.background='transparent';bd.id='cmenubd';bd.style.zIndex=59;bd.onclick=K.chatui.close;
  document.body.appendChild(bd);document.body.appendChild(m);
  m.onclick=async e=>{const b=e.target.closest('button');if(!b)return;K.chatui.close();K.native.haptic();K.chatui.act(b.dataset.k,c)}},
 async act(k,c){
  if(k==='sh'){const txt=c.msgs.map(m=>(m.role==='user'?'🧑 ':'🤖 ')+(m.c||'')).join('\n\n');K.native.share(txt)}
  else if(k==='pin'){c.pinned=!c.pinned;K.db.save();K.home.renderList();K.toast(c.pinned?T('تم التثبيت','Pinned'):T('أُلغي التثبيت','Unpinned'))}
  else if(k==='lock'){
   if(c.locked){c.locked=false;K.db.save();K.home.renderList();K.toast(T('أُلغي القفل','Unlocked'));return}
   const set=()=>{c.locked=true;K.db.save();K.home.renderList();K.toast(T('قُفلت الدردشة — يُطلب الرمز عند فتحها من القائمة','Chat locked'))};
   if(K.lock.has())set();else{K.toast(T('أنشئ رمز قفل أولاً','Create a passcode first'));K.lock.show('set',()=>set())}}
  else if(k==='proj')K.toast(T('المشروعات — قريباً','Projects — coming soon'));
  else if(k==='files'){
   const fl=[];c.msgs.forEach(m=>{(m.files||[]).forEach(f=>fl.push('📄 '+f.n+(f.size?' · '+Math.round(f.size/1024)+' KB':'')));(m.imgs||[]).forEach((x,i)=>fl.push('🖼 '+T('صورة','Image')+' '+(i+1)))});
   K.ui.info(T('الملفات التي تم تحميلها','Uploaded files'),fl.length?fl.join('\n'):T('لا توجد ملفات في هذه المحادثة.','No files in this chat.'))}
  else if(k==='find'){
   const q=await K.ui.prompt({title:T('البحث في الدردشة','Search in chat'),hint:T('اكتب كلمة','Type a word')});if(!q)return;
   const els=[...document.querySelectorAll('#msgs .msg')];document.querySelectorAll('#msgs .hit').forEach(x=>x.classList.remove('hit'));
   const hits=els.filter(e=>e.textContent.toLowerCase().includes(q.toLowerCase()));
   if(!hits.length){K.toast(T('لا نتائج','No results'));return}
   hits.forEach(e=>e.classList.add('hit'));hits[0].scrollIntoView({behavior:'smooth',block:'center'});K.toast(hits.length+' '+T('نتيجة','results'));setTimeout(()=>hits.forEach(e=>e.classList.remove('hit')),4000)}
  else if(k==='snd'){const on=!K.pref.bool('sound',false);K.pref.setBool('sound',on);if(!on)K.native.stopSpeak();K.toast(T('قراءة الردود تلقائياً ','Auto read ')+(on?'✓':'✕'))}
  else if(k==='arc'){c.archived=true;K.db.save();K.home.newChat();K.snack(T('تمت الأرشفة','Archived'),T('تراجع','Undo'),()=>{c.archived=false;K.db.save();K.home.renderList();K.home.openConv(c)})}
  else if(k==='del'){if(await K.ui.confirm(T('حذف هذه المحادثة؟','Delete this chat?'),true)){K.db.remove(c);K.home.newChat()}}
 },
 /* read-aloud bar over the header: X · waveform · timer · pause */
 speak(text){
  K.chatui.stopSpeak(true);cur=String(text||'').replace(/```[\s\S]*?```/g,' ').replace(/[#*`>|_]/g,' ');paused=false;
  const top=document.querySelector('.topbar');if(!top)return;
  bar=document.createElement('div');bar.id='ttsbar';bar.dir='ltr';
  bar.innerHTML='<button class="tx">'+K.icon('x',22)+'</button><div class="wv">'+Array.from({length:34},(_,i)=>'<i style="--d:'+((i*37)%11)/10+'s;--h:'+(8+((i*53)%17))+'px"></i>').join('')+'</div><span class="tc">0:00</span><button class="tp">'+K.icon('square',16)+'</button>';
  document.body.appendChild(bar);
  bar.querySelector('.tx').onclick=()=>K.chatui.stopSpeak();
  bar.querySelector('.tp').onclick=()=>{if(!paused){paused=true;K.native.stopSpeak();bar.classList.add('paused');clearInterval(tick);bar.querySelector('.tp').innerHTML=K.icon('arrow-up',16)}else{paused=false;bar.classList.remove('paused');start()}};
  const start=()=>{t0=Date.now();clearInterval(tick);tick=setInterval(()=>{const s=Math.floor((Date.now()-t0)/1000);bar&&(bar.querySelector('.tc').textContent=Math.floor(s/60)+':'+String(s%60).padStart(2,'0'))},500);bar.querySelector('.tp').innerHTML=K.icon('square',16);
   window.__ttsDone=function(){K.chatui.stopSpeak()};K.native.speak(cur)};
  start()},
 stopSpeak(quiet){clearInterval(tick);if(!quiet)K.native.stopSpeak();if(bar){bar.remove();bar=null}if(!(K.voice&&K.voice.active))window.__ttsDone=null}
};
})();
