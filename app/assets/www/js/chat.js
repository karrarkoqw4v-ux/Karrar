/* Conversation view: messages, streaming, web-search sources, attachments, actions */
window.K=window.K||{};
(function(){
const S=K.state={cur:null,think:false,search:false,streaming:false,atts:[],handle:null,live:null};
const $=id=>document.getElementById(id);
K.LOGO='<svg viewBox="0 0 120 120"><image href="img/logo.png" x="0" y="0" width="120" height="120" preserveAspectRatio="xMidYMid meet"/></svg>';
const esc=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const host=u=>{try{return new URL(u).hostname.replace(/^www\./,'')}catch(e){return u}};
function fav(s,big){const h=host(s.url),l=(h[0]||'?').toUpperCase();let n=0;for(const c of h)n=(n*31+c.charCodeAt(0))%360;
 return '<span class="fav" style="background:hsl('+n+' 55% 42%)">'+esc(l)+'<img alt="" src="https://www.google.com/s2/favicons?domain='+esc(h)+'&sz=64" onerror="this.remove()"></span>'}
function card(s,i){return '<div class="scard" data-url="'+esc(s.url)+'" data-i="'+(i+1)+'" style="animation-delay:'+(i*60)+'ms">'+fav(s)+'<div class="b"><div class="site">'+esc(host(s.url))+'</div><div class="ti">'+esc(s.title)+'</div><div class="sn">'+esc(s.snippet||'')+'</div></div><span class="no">'+(i+1)+'</span></div>'}

function AIView(m,o){
 this.m=m;this.last=!!o.last;this.live=!!o.live;this.done=!o.live;this.open=false;this.thinking=false;this.status='';
 const el=this.el=document.createElement('div');el.className='msg ai'+(this.live?' live':' done');el._view=this;
 el.innerHTML='<div class="ai-head"><span class="logo">'+K.LOGO+'</span></div><div class="src"></div><div class="status shimmer hidden"></div><div class="dots hidden"><i></i><i></i><i></i></div>'
  +'<div class="think hidden"><button class="think-h">'+K.icon('atom',17)+'<span></span><span class="chev">'+K.icon('chevron-down',16)+'</span></button><div class="think-b"><div><p></p></div></div></div>'
  +'<div class="md"></div><div class="errc hidden"></div><div class="gen hidden"></div><div class="demo-note hidden">'+K.icon('info',15)+'<span>'+K.t('demo_badge')+'</span></div>'
  +'<div class="acts"><button data-a="copy">'+K.icon('copy',19)+'</button><button data-a="regen" class="'+(this.last?'':'hidden')+'">'+K.icon('refresh-cw',19)+'</button><button data-a="up">'+K.icon('thumbs-up',19)+'</button><button data-a="down">'+K.icon('thumbs-down',19)+'</button><button data-a="share">'+K.icon('share',19)+'</button><button data-a="speak">'+K.icon('volume-2',19)+'</button></div>';
 const self=this;
 el.querySelector('.think-h').onclick=function(){self.open=!self.open;self.render()};
 this.renderSources();this.render();
}
AIView.prototype.q=function(s){return this.el.querySelector(s)};
setInterval(function(){document.querySelectorAll('.think-h>span').forEach(function(e){const t=e.querySelector('.tm');if(t&&e._s)t.textContent=Math.round((Date.now()-e._s)/1000)+'s'});document.querySelectorAll('.status:not(.hidden)').forEach(function(e){const t=e.querySelector('.tm');if(t&&e._s)t.textContent=Math.max(0,Math.round((Date.now()-e._s)/1000))+'s'})},1000);
AIView.prototype.setStatus=function(t){this.status=t||'';this.render()};
AIView.prototype.dirty=function(){if(this._q)return;this._q=1;const s=this;requestAnimationFrame(function(){s._q=0;s.render();if(K.chat.nearBottom)K.chat.scrollEnd()})};
AIView.prototype.renderSources=function(){
 const src=this.q('.src'),a=this.m.src||[];
 if(this.searching){src.innerHTML='<div class="sk"></div><div class="sk" style="height:46px;opacity:.7"></div>';return}
 if(!a.length){src.innerHTML='';return}
 const was=src.classList.contains('open');
 src.innerHTML='<button class="src-chip"><span class="fav-stack">'+a.slice(0,3).map(s=>fav(s)).join('')+'</span><span>'+K.t('searched',a.length)+'</span><span class="chev">'+K.icon('chevron-down',16)+'</span></button><div class="src-list"><div>'+a.map(card).join('')+'</div></div>';
 src.classList.toggle('open',was);src.querySelector('.src-chip').onclick=function(){src.classList.toggle('open')};
};
AIView.prototype.render=function(){
 const m=this.m,hasT=!!m.t,live=!this.done;
 const th=this.q('.think');th.classList.toggle('hidden',!hasT);
 if(hasT){this.q('.think-b p').textContent=m.t;th.classList.toggle('open',this.open);th.classList.toggle('live',this.thinking);
  {const ls=th.querySelector('.think-h>span');if(this.thinking){if(!ls._d){ls._d=1;ls._s=Date.now();ls.innerHTML='<span class="td"><i></i><i></i><i></i></span><span class="tt"></span><span class="tm">0s</span>';ls.querySelector('.tt').textContent=K.t('thinking')}th.classList.add('tk')}else{ls._d=0;ls.textContent=K.t('thought_for',Math.max(1,m.s||1))}}}
 const st=this.q('.status');st.classList.toggle('hidden',!this.status);if(this.status){if(st._t!==this.status){st._t=this.status;st._s=Date.now();st.innerHTML='<span class="td"><i></i><i></i><i></i></span><span class="tt"></span><span class="tm"></span>';st.querySelector('.tt').textContent=this.status}}else st._t='';
 this.q('.dots').classList.toggle('hidden',!!(m.c||m.t||m.gen||this.status||!live));
 const gn=this.q('.gen');gn.classList.toggle('hidden',!m.gen);if(m.gen)gn.innerHTML=K.imggen.html(m.gen);
 const hasSrc=(m.src||[]).length>0;
 this.q('.md').innerHTML=K.mdEmoji(m.c||'',hasSrc)+(live&&m.c?'<span class="cursor"></span>':'');
 {const ec=this.q('.errc');ec.classList.toggle('hidden',!m.err);if(m.err&&ec._k!==m.err.m){ec._k=m.err.m;const ar=K.lang()==='ar',net=/timeout|timed out|UnknownHost|Unable to resolve|ECONN|failed to connect|Network|SocketException|SSL|no providers/i.test(m.err.m);
  ec.innerHTML='<span class="ei"><svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 8.8a15 15 0 0 1 4.2-2.6M22 8.8a15 15 0 0 0-9-3.7"/><path d="M5 12.9a10 10 0 0 1 3.5-2.1M19 12.9a10 10 0 0 0-2.6-1.9"/><path d="M8.5 16.4a5 5 0 0 1 7 0"/><path d="M12 20h.01"/><path d="m2 2 20 20"/></svg></span><div class="et"><b>'+(net?(ar?'بوصلة غير متاحة حالياً':'Bawsala is unavailable right now'):(ar?'تعذّر الحصول على رد':'Could not get a reply'))+'</b><span>'+(net?(ar?'يُرجى التحقق من الاتصال بالإنترنت ثم إعادة المحاولة.':'Check your connection and try again.'):'')+'</span><details><summary>'+(ar?'التفاصيل':'Details')+'</summary><pre></pre></details><button class="eretry" data-eretry="1">'+(ar?'إعادة المحاولة':'Try again')+'</button></div>';
  ec.querySelector('pre').textContent=String(m.err.m).slice(0,600)+(m.err.hint?'\n'+m.err.hint.replace(/^\n+/,''):'')}}
 this.el.classList.toggle('done',!live);this.el.classList.toggle('live',live);
 this.q('.demo-note').classList.toggle('hidden',!(m.demo&&!live));
 this.q('[data-a=up]').classList.toggle('on',m.v===1);this.q('[data-a=down]').classList.toggle('on',m.v===-1);
 this.q('[data-a=regen]').classList.toggle('hidden',!(this.last&&!live&&!m.gen));
};

/* ---------------- public API ---------------- */
const C=K.chat={nearBottom:true};
C.mount=function(){
 const msgs=$('msgs'),sc=$('scroll');
 sc.addEventListener('scroll',function(){C.nearBottom=sc.scrollTop+sc.clientHeight>=sc.scrollHeight-140});
 msgs.addEventListener('click',function(e){
  const t=e.target;
  const av=t.closest('[data-appv]');if(av){K.apps.open(av.dataset.appv);return}
  const as=t.closest('[data-apps]');if(as){K.apps.save(as.dataset.apps);return}
  const er=t.closest('[data-eretry]');if(er){const vv=er.closest('.msg.ai')._view;if(vv&&!S.streaming){S.cur.msgs.pop();vv.el.remove();C.start()}return}
  const dcb=t.closest('[data-dcc],[data-dcs]');if(dcb){const raw=decodeURIComponent(dcb.closest('.docc').querySelector('.dc-body').dataset.raw||'');if(dcb.dataset.dcc!==undefined){K.native.copy(raw);K.toast(K.lang()==='ar'?'تم النسخ':'Copied')}else K.native.share(raw);return}
  const ab=t.closest('[data-appb]');if(ab){K.apps.browser(ab.dataset.appb);return}
  const ag=t.closest('[data-appg]');if(ag){K.apps.push(ag.dataset.appg);return}
  const ac=t.closest('[data-appc]');if(ac){K.apps.copy(ac.dataset.appc);return}
  const gb=t.closest('[data-g]');if(gb){K.imggen.act(gb.closest('.msg.ai')._view,gb.dataset.g);return}
  const sv=t.closest('[data-save]');if(sv){const cb=sv.closest('.codebox');K.files.saveCode(cb.querySelector('.ch>span').textContent,cb.querySelector('pre').innerText);return}
  const dl=t.closest('[data-fid]');if(dl){K.files.save(dl.dataset.fid,dl);return}
  const cp=t.closest('[data-copy]');if(cp){const pre=cp.closest('.codebox').querySelector('pre');K.native.copy(pre.innerText);if(!cp._t){const sp=cp.querySelector('span'),old=sp.textContent,ic=cp.querySelector('svg');cp.classList.add('done');sp.textContent=K.t('copied');cp._t=setTimeout(function(){cp.classList.remove('done');sp.textContent=old;cp._t=0},1600)}return}
  const a=t.closest('[data-a]');if(a){const v=a.closest('.msg.ai')._view;C.action(v,a.dataset.a);return}
  const ci=t.closest('.cite');if(ci){const v=ci.closest('.msg.ai')._view;const src=v.q('.src');src.classList.add('open');
   setTimeout(function(){const c=src.querySelectorAll('.scard')[+ci.dataset.i-1];if(c){c.scrollIntoView({block:'center',behavior:'smooth'});c.classList.remove('flash');void c.offsetWidth;c.classList.add('flash')}},120);return}
  const sc2=t.closest('.scard');if(sc2){K.native.open(sc2.dataset.url);return}
  const ln=t.closest('a[data-url]');if(ln){e.preventDefault();K.native.open(ln.dataset.url);return}
  const im=t.closest('.att-img');if(im){K.ui.lightbox(im.src)}
 });
};
C.topScroll=function(d){const sc=$('scroll');S.pinTop=d;$('msgs').style.paddingBottom=Math.round(sc.clientHeight*.7)+'px';requestAnimationFrame(function(){sc.scrollTo({top:Math.max(0,sc.scrollTop+d.getBoundingClientRect().top-sc.getBoundingClientRect().top-8),behavior:'smooth'})})};
C.scrollEnd=function(smooth){const sc=$('scroll');sc.scrollTo({top:sc.scrollHeight,behavior:smooth?'smooth':'auto'})};
C.addUser=function(m){
 $('empty').classList.add('hidden');
 const d=document.createElement('div');d.className='msg user';
 let h='';
 if((m.imgs&&m.imgs.length)||(m.files&&m.files.length)){h+='<div class="att-row">'+(m.imgs||[]).map(s=>'<img class="att-img" src="'+s+'">').join('')+(m.files||[]).map(f=>K.files.card(f)).join('')+'</div>'}
 if(m.c)h+='<div class="bubble">'+K.emojify(esc(m.c))+'</div>';
 h+='<div class="uacts"><button data-u="copy" title="'+T2('نسخ','Copy')+'">'+K.icon('copy',16)+'</button><button data-u="edit" title="'+T2('تعديل','Edit')+'">'+K.icon('pencil',16)+'</button><button data-u="retry" title="'+T2('إعادة المحاولة','Retry')+'">'+K.icon('refresh-cw',16)+'</button></div>';
 d.innerHTML=h;d._m=m;$('msgs').appendChild(d);
 d.querySelectorAll('[data-u]').forEach(b=>b.onclick=e=>{e.stopPropagation();C.userAction(d,b.dataset.u)});return d};
function T2(a,e){return K.lang()==='ar'?a:e}
C.userAction=function(d,a){
 const m=d._m,conv=S.cur;if(!m||!conv)return;
 if(a==='copy'){K.native.copy(m.c||'');K.toast(T2('تم النسخ','Copied'));return}
 const i=conv.msgs.indexOf(m);
 if(S.streaming)C.stop();
 S.queue=[];
 if(i<0){/* still queued, not sent yet */ if(a==='edit'){d.remove();const ta=$('ta');ta.value=m.c||'';ta.dispatchEvent(new Event('input'));ta.focus()}return}
 /* drop this message's reply and everything after it */
 const all=[...$('msgs').children],at=all.indexOf(d);
 all.slice(a==='edit'?at:at+1).forEach(x=>x.remove());
 conv.msgs.splice(a==='edit'?i:i+1);
 K.db.save();
 if(a==='edit'){const ta=$('ta');ta.value=m.c||'';ta.dispatchEvent(new Event('input'));ta.focus();K.home.refreshComposer()}
 else{C.start()}
};
C.show=function(conv){
 K.state.cur=conv;const box=$('msgs');box.innerHTML='';box.style.paddingBottom='';
 const lastAi=conv.msgs.map(m=>m.role).lastIndexOf('assistant');
 conv.msgs.forEach(function(m,i){if(m.role==='user')C.addUser(m);else{const v=new AIView(m,{last:i===lastAi,live:false});box.appendChild(v.el)}});
 $('empty').classList.toggle('hidden',conv.msgs.length>0);
 if(K.home&&K.home.paintPills)K.home.paintPills();
 const tt=$('title');if(conv.title)tt.textContent=conv.title;else{tt.innerHTML='<button class="tbrand" id="tBrand"><b>Bawsala</b>'+K.icon('chevron-down',15,2.6)+'</button>';$('tBrand').onclick=function(){K.models.open()}}C.scrollEnd();
};
function toApi(msgs){return msgs.filter(function(m){return !m.gen}).map(m=>({role:m.role==='assistant'?'assistant':'user',content:((m.ft?m.ft+'\n\n':'')+(m.c||'')),images:m._b64||[]})).filter(x=>x.content||x.images.length)}
function buildCtx(a){if(!a.length)return '';return 'Web search results (cite them in your answer as [1], [2] when you use them):\n'+a.map((s,i)=>(i+1)+'. '+s.title+' - '+(s.snippet||'')+' ('+s.url+')').join('\n')}
C.send=async function(){
 if(K.guard.locked()){K.guard.showLock();return}
 const ta=$('ta'),text=ta.value.trim(),atts=S.atts.slice();
 if(!text&&!atts.length)return;
 if(S.imgMode&&!S.img){K.imggen.sizes();return}
 K.native.haptic();
 if(S.streaming&&!text&&!atts.length){C.stop();return}
 const files=atts.filter(a=>a.kind==='file');
 const m={role:'user',c:text,imgs:atts.filter(a=>a.kind==='img').map(a=>a.th),files:files.map(f=>({n:f.name,size:f.size,pages:f.pages})),ft:files.map(f=>'[File: '+f.name+']\n```\n'+f.text+'\n```').join('\n\n')};
 m._b64=atts.filter(a=>a.kind==='img'&&a.b64).map(a=>a.b64);
 ta.value='';ta.style.height='';S.atts=[];K.home.refreshComposer();K.home.closeSheet();
 if(S.streaming){/* queue: runs right after the current answer */ (S.queue=S.queue||[]).push(m);const qd=C.addUser(m);qd.classList.add('queued');K.toast(T2('أُضيفت للدور','Queued'));C.scrollEnd(true);return}
 if(K.brief&&K.brief.need(text,S.cur)){S.cur.briefed=true;const bf=await K.brief.ask(text);if(bf)m.brief=bf}
 const conv=S.cur;conv.msgs.push(m);
 if(!conv.title){conv.title=(text||'🖼').replace(/\s+/g,' ').slice(0,40);K.fx.type($('title'),conv.title,34)}
 conv.ts=Date.now();if(K.db.convs.indexOf(conv)<0)K.db.convs.unshift(conv);
 K.pref.set('cur',conv.id);const ud=C.addUser(m);C.topScroll(ud);if(K.guard.test(text+' '+(m.ft||'')))C.localReply(conv,K.guard.hit());else if(S.imgMode)K.imggen.start(conv,m);else if(C.isDevQ(text)&&!atts.length)C.devReply(conv);else C.start();
};
C.isDevQ=function(t){t=(t||'').trim();if(!t||t.length>140)return false;
 const who=/(مطور|المطور|مبرمج|صاحب|مالك|مسؤول|المسؤول|من\s+(صنع|سوى|برمج|طور|عمل|انشأ|أنشأ)|مين\s+(صنع|سوى|برمج|طور|عمل)|تواصل|حسابات|developer|owner|admin|who\s+(made|built|created|developed|owns|runs)|contact)/i;
 return who.test(t)&&/(التطبيق|تطبيق|بوصلة|bawsala|\bapp\b|الذكاء)/i.test(t)};
C.localReply=function(conv,md){
 const ai={role:'assistant',c:md,t:'',s:0,src:[],v:0};conv.msgs.push(ai);
 const v=new AIView(ai,{last:true,live:false});$('msgs').appendChild(v.el);K.db.save();C.scrollEnd(true)};
C.devReply=function(conv){
 const ar=K.lang()==='ar';
 const md=ar?'## مطوّر «بوصلة» والمسؤول عن التطبيق\nالمسؤول عن التطبيق وتطويره: **كروري** (Karrouri)\n\n### طرق التواصل\n- 📧 البريد الإلكتروني: karrarkooqwtoi@gmail.com\n- 🎵 TikTok: [@vwv2e](https://www.tiktok.com/@vwv2e)\n- 📸 Instagram: [@10s.i2](https://www.instagram.com/10s.i2)\n- 🐦 X (تويتر): [@Compassv4](https://x.com/Compassv4)\n\nيمكنك أيضاً فتح صفحة **اتصل بنا** من شاشة الدخول أو من الإعدادات ← المساعدة، وفيها الحسابات والشروط وأحكام الاستخدام.'
  :'## Bawsala developer & app owner\nThe person responsible for the app and its development: **Karrouri** (كروري)\n\n### Contact\n- 📧 Email: karrarkooqwtoi@gmail.com\n- 🎵 TikTok: [@vwv2e](https://www.tiktok.com/@vwv2e)\n- 📸 Instagram: [@10s.i2](https://www.instagram.com/10s.i2)\n- 🐦 X (Twitter): [@Compassv4](https://x.com/Compassv4)\n\nYou can also open **Contact us** from the login screen or Settings → Help for the accounts, terms and conditions.';
 const ai={role:'assistant',c:md,t:'',s:0,src:[],v:0};conv.msgs.push(ai);
 const v=new AIView(ai,{last:true,live:false});$('msgs').appendChild(v.el);K.db.save();C.scrollEnd(true)};
C.start=function(){
 const conv=S.cur;
 conv.msgs.forEach(()=>0);
 document.querySelectorAll('.msg.ai [data-a=regen]').forEach(b=>b.classList.add('hidden'));
 const ai={role:'assistant',c:'',t:'',s:0,src:[],v:0,demo:!K.native.hasKey()};conv.msgs.push(ai);
 const view=new AIView(ai,{last:true,live:true});$('msgs').appendChild(view.el);S.live=view;S.streaming=true;K.home.refreshComposer();
 const hist=toApi(conv.msgs.slice(0,-1));
 const lastUser=[...conv.msgs].reverse().find(m=>m.role==='user');
 const lu0=(lastUser&&lastUser.c)||'';
 const thinkNow=S.think||K.pref.get('think_level','auto')==='always'||K.intent.think(lu0);
 const run=async function(ctx){
  const pr=K.persona();ctx=(pr?pr+(ctx?'\n\n':''):'')+(ctx||'');
  const sk=K.skills.context(lu0);if(sk)ctx+=(ctx?'\n\n':'')+sk;
  {const dr=K.skills.rules(lu0);if(dr)ctx+=(ctx?'\n\n':'')+dr}
  try{const A=window.Android;if(A&&A.agentKnowledge&&K.pref.bool('agent_kn',true)){const kn=JSON.parse(A.agentKnowledge(lu0)||'[]');if(kn.length){ai.kn=kn.map(x=>x.title);ctx+=(ctx?'\n\n':'')+'KNOWLEDGE saved earlier by the user\'s task agent (official sources). Use it when relevant, and say you used it:\n'+kn.map((x,i)=>'['+(i+1)+'] '+x.title+': '+x.text).join('\n\n')}}}catch(e){}
  {const lp=K.langpack&&K.langpack.hint(lu0);if(lp)ctx+=(ctx?'\n\n':'')+lp}
  if(lastUser&&lastUser.brief)ctx+=(ctx?'\n\n':'')+lastUser.brief
  if(K.intent.write(lu0)||(S.cur&&S.cur.stu&&K.intent.write(lu0)))ctx+=(ctx?'\n\n':'')+'FORMAT: when you write a finished piece for the user (composition, essay, letter, speech, report, summary, book chapter, worked solution), put the piece itself inside a block that starts with a line :::doc and ends with a line :::, with a first line "# Title" inside. Keep any short intro or closing note outside the block. Do not use the block for casual chat or code.';
  const dl=K.dialect&&K.dialect.hint?K.dialect.hint(lu0):'';if(dl)ctx+=(ctx?'\n\n':'')+dl;
  try{const tn=await K.team.run(lu0,view);if(tn)ctx+='\n\n'+tn}catch(e){}
  if(view.finished)return;
  const T0=Date.now(),lu=lu0;
  /* calm pacing: think 4-10 s depending on the question, then reveal the answer gently */
  let want=3600+Math.min(5400,lu.trim().split(/\s+/).filter(Boolean).length*230);
  if(/(اكتب|برمج|كود|اشرح|شرح|قارن|حلل|code|write|explain|compare|build)/i.test(lu))want+=1800;
  if(lastUser&&((lastUser.files&&lastUser.files.length)||(lastUser.imgs&&lastUser.imgs.length)))want+=1500;
  want=Math.min(10000,want);
  const hasAtt=!!(lastUser&&((lastUser.files&&lastUser.files.length)||(lastUser.imgs&&lastUser.imgs.length)));
  if(!thinkNow)want=Math.min(want,1400);else want=Math.max(want,6000);
  let q='',doneEv=null,tm=0,flushed=false,hid='';
  view.setStatus(thinkNow?K.t('thinking'):(K.lang()==='ar'?'لحظة من فضلك…':'One moment…'));
  const pump=function(){tm=0;if(view.finished)return;
   const left=want-(Date.now()-T0);
   if(!flushed&&left>0){tm=setTimeout(pump,Math.min(left,250));return}
   if(!flushed){flushed=true;ai.s=Math.max(1,Math.round((Date.now()-T0)/1000));view.open=false;view.thinking=false;view.status='';view.dirty()}
   if(q){const n=2+Math.floor(q.length/70);ai.c+=q.slice(0,n);q=q.slice(n);view.dirty();if(S.voiceMode&&K.voice.active)K.voice.onStream(ai.c);tm=setTimeout(pump,34);return}
   if(doneEv){doneEv=null;if(!ai.c&&hid&&!ai.t)ai.c=hid;C.finish(view)}
  };
  S.handle=K.native.chat(hist,thinkNow,ctx,function(ev){
   if(view.finished)return;
   if(ev.t==='think'){if(!thinkNow){hid+=ev.d;return}ai.t+=ev.d;view.open=true;view.thinking=true;view.status='';view.dirty()}
   else if(ev.t==='text'){q+=ev.d;if(!tm)pump()}
   else if(ev.t==='done'){doneEv=ev;if(!tm)pump()}
   else if(ev.t==='err'){clearTimeout(tm);tm=0;flushed=true;ai.c+=q;q='';ai.err={m:ev.m,hint:K.errHint(ev.m)};ai.s=ai.s||1;view.open=false;view.thinking=false;view.status='';C.finish(view)}
  });
 };
 const withRepos=function(ctx){
  const repos=K.gh.tok()?K.gh.sel():[];if(!repos.length)return Promise.resolve(ctx);
  view.setStatus(K.t('gh_reading'));
  return K.gh.context(repos).then(function(t){view.setStatus('');return (ctx?ctx+'\n\n':'')+t}).catch(function(){view.setStatus('');return ctx});
 };
 if(S.search||K.intent.search(lu0)){
  view.searching=true;view.renderSources();view.setStatus(K.t('searching'));
  K.native.search(lastUser.c||'').then(function(res){
   if(view.finished)return;view.searching=false;ai.src=Array.isArray(res)?res:[];view.setStatus('');view.renderSources();view.render();
   return withRepos(buildCtx(ai.src)).then(function(c){if(!view.finished)run(c)});
  });
 }else withRepos('').then(function(c){if(!view.finished)run(c)});
};
C.finish=function(view){
 if(view.finished)return;view.finished=true;view.done=true;view.thinking=false;S.streaming=false;S.live=null;S.handle=null;
 if(!view.m.c&&view.m.t){view.m.c=view.m.t;view.m.t='';view.m.s=view.m.s||1}
 if(view.m.kn&&view.m.kn.length&&view.m.c&&view.m.c!=='—')view.m.c+='\n\n📚 '+(K.lang()==='ar'?'من معرفة الوكيل: ':'From the agent\'s knowledge: ')+view.m.kn.join('، ');
 if(!view.m.c&&!view.m.err)view.m.c='—';
 S.think=false;S.search=false;if(K.home&&K.home.paintPills)K.home.paintPills();
 view.status='';view.render();K.db.save();K.home.refreshComposer();
 if(S.voiceMode&&K.voice.active)K.voice.onReply(view.m.c);else if(K.pref.bool('sound',false))K.native.speak(view.m.c);
 if(S.queue&&S.queue.length){const nm=S.queue.shift(),conv=S.cur;conv.msgs.push(nm);document.querySelectorAll('.msg.user.queued').forEach((x,k)=>{if(x._m===nm)x.classList.remove('queued')});conv.ts=Date.now();K.db.save();setTimeout(()=>C.start(),350)}
};
C.AIView=AIView;
K.errHint=function(m){m=String(m||'');const ar=K.lang()==='ar';let h='';
 if(/HTTP 401|Missing Authentication|invalid.*api.?key|incorrect api key/i.test(m))h=ar?'المفتاح غير موجود أو غير صالح. افتح الإعدادات ← التطبيقات الموصولة وأضف مفتاح API ثم اضغط «اختبار الاتصال».':'The API key is missing or invalid. Open Settings → Connected apps, add the key and run “Test connection”.';
 else if(/decommission|deprecat|no longer supported|model.*not found|does not exist|HTTP 404/i.test(m))h=ar?'اسم النموذج لم يعد متاحاً عند هذا المزوّد. غيّره من الإعدادات ← التطبيقات الموصولة ← النموذج (وإذا كنت في وضع «تفكير» غيّر نموذج التفكير أيضاً).':'This model is no longer available from the provider. Change it in Settings → Connected apps → Model (and the thinking model if you use Think).';
 else if(/HTTP 429|quota|rate.?limit/i.test(m))h=ar?'وصلت لحدّ الطلبات أو الرصيد. انتظر قليلاً أو بدّل المزوّد.':'You hit the rate or credit limit. Wait a bit or switch provider.';
 else if(/HTTP 402|insufficient|credit|billing/i.test(m))h=ar?'الحساب يحتاج رصيداً عند هذا المزوّد. جرّب مزوّداً بخطة مجانية.':'This provider account needs credit. Try a provider with a free tier.';
 return h?'\n\n💡 '+h:''};
C.stop=function(){if(S.handle)S.handle.cancel();K.native.stopSpeak();if(S.live){if(!S.live.m.s&&S.live.m.t)S.live.m.s=1;C.finish(S.live)}};
C.action=function(v,a){
 if(a==='copy'){K.native.copy(v.m.c)}
 else if(a==='share'){K.native.share(v.m.c)}
 else if(a==='speak'){K.chatui.speak(v.m.c)}
 else if(a==='up'){v.m.v=v.m.v===1?0:1;v.render();K.db.save()}
 else if(a==='down'){v.m.v=v.m.v===-1?0:-1;v.render();K.db.save()}
 else if(a==='regen'){if(S.streaming)return;const conv=S.cur;conv.msgs.pop();v.el.remove();C.start()}
 K.native.haptic();
};
})();
