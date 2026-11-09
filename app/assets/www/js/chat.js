/* Conversation view: messages, streaming, web-search sources, attachments, actions */
window.K=window.K||{};
(function(){
const S=K.state={cur:null,think:false,search:false,streaming:false,atts:[],handle:null,live:null};
const $=id=>document.getElementById(id);
K.LOGO='<svg viewBox="0 0 120 120"><defs><linearGradient id="lp1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#e9c99a"/><stop offset="1" stop-color="#a97a46"/></linearGradient><linearGradient id="lp2" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f6e2c2"/><stop offset="1" stop-color="#c39a64"/></linearGradient><linearGradient id="lp3" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff6e6"/><stop offset="1" stop-color="#d8b784"/></linearGradient></defs><ellipse class="s1" cx="60" cy="88" rx="38" ry="15" fill="url(#lp1)"/><ellipse class="s2" cx="59" cy="66" rx="28" ry="12" fill="url(#lp2)"/><ellipse class="s3" cx="60" cy="46" rx="19" ry="9.5" fill="url(#lp3)"/></svg>';
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
  +'<div class="gen hidden"></div><div class="md"></div><div class="demo-note hidden">'+K.icon('info',15)+'<span>'+K.t('demo_badge')+'</span></div>'
  +'<div class="acts"><button data-a="copy">'+K.icon('copy',19)+'</button><button data-a="regen" class="'+(this.last?'':'hidden')+'">'+K.icon('refresh-cw',19)+'</button><button data-a="up">'+K.icon('thumbs-up',19)+'</button><button data-a="down">'+K.icon('thumbs-down',19)+'</button><button data-a="share">'+K.icon('share',19)+'</button></div>';
 const self=this;
 el.querySelector('.think-h').onclick=function(){self.open=!self.open;self.render()};
 this.renderSources();this.render();
}
AIView.prototype.q=function(s){return this.el.querySelector(s)};
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
  th.querySelector('.think-h>span').textContent=this.thinking?K.t('thinking'):K.t('thought_for',Math.max(1,m.s||1))}
 const st=this.q('.status');st.classList.toggle('hidden',!this.status);st.textContent=this.status;
 this.q('.dots').classList.toggle('hidden',!!(m.c||m.t||m.gen||this.status||!live));
 const gn=this.q('.gen');gn.classList.toggle('hidden',!m.gen);if(m.gen)gn.innerHTML=K.imggen.html(m.gen);
 const hasSrc=(m.src||[]).length>0;
 this.q('.md').innerHTML=K.mdEmoji(m.c||'',hasSrc)+(live&&m.c?'<span class="cursor"></span>':'');
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
C.scrollEnd=function(smooth){const sc=$('scroll');sc.scrollTo({top:sc.scrollHeight,behavior:smooth?'smooth':'auto'})};
C.addUser=function(m){
 $('empty').classList.add('hidden');
 const d=document.createElement('div');d.className='msg user';
 let h='';
 if((m.imgs&&m.imgs.length)||(m.files&&m.files.length)){h+='<div class="att-row">'+(m.imgs||[]).map(s=>'<img class="att-img" src="'+s+'">').join('')+(m.files||[]).map(f=>K.files.card(f)).join('')+'</div>'}
 if(m.c)h+='<div class="bubble">'+K.emojify(esc(m.c))+'</div>';
 d.innerHTML=h;$('msgs').appendChild(d);return d};
C.show=function(conv){
 K.state.cur=conv;const box=$('msgs');box.innerHTML='';
 const lastAi=conv.msgs.map(m=>m.role).lastIndexOf('assistant');
 conv.msgs.forEach(function(m,i){if(m.role==='user')C.addUser(m);else{const v=new AIView(m,{last:i===lastAi,live:false});box.appendChild(v.el)}});
 $('empty').classList.toggle('hidden',conv.msgs.length>0);
 if(K.home&&K.home.paintPills)K.home.paintPills();
 const tt=$('title');tt.textContent=conv.title||'';C.scrollEnd();
};
function toApi(msgs){return msgs.map(m=>({role:m.role==='assistant'?'assistant':'user',content:((m.ft?m.ft+'\n\n':'')+(m.c||'')),images:m._b64||[]})).filter(x=>x.content||x.images.length)}
function buildCtx(a){if(!a.length)return '';return 'Web search results (cite them in your answer as [1], [2] when you use them):\n'+a.map((s,i)=>(i+1)+'. '+s.title+' - '+(s.snippet||'')+' ('+s.url+')').join('\n')}
C.send=function(){
 if(S.streaming){C.stop();return}
 const ta=$('ta'),text=ta.value.trim(),atts=S.atts.slice();
 if(!text&&!atts.length)return;
 if(S.imgMode&&!S.img){K.imggen.sizes();return}
 K.native.haptic();
 const files=atts.filter(a=>a.kind==='file');
 const m={role:'user',c:text,imgs:atts.filter(a=>a.kind==='img').map(a=>a.th),files:files.map(f=>({n:f.name,size:f.size,pages:f.pages})),ft:files.map(f=>'[File: '+f.name+']\n```\n'+f.text+'\n```').join('\n\n')};
 m._b64=atts.filter(a=>a.kind==='img'&&a.b64).map(a=>a.b64);
 ta.value='';ta.style.height='';S.atts=[];K.home.refreshComposer();K.home.closeSheet();
 const conv=S.cur;conv.msgs.push(m);
 if(!conv.title){conv.title=(text||'🖼').replace(/\s+/g,' ').slice(0,40);K.fx.type($('title'),conv.title,34)}
 conv.ts=Date.now();if(K.db.convs.indexOf(conv)<0)K.db.convs.unshift(conv);
 K.pref.set('cur',conv.id);C.addUser(m);if(S.imgMode)K.imggen.start(conv,m);else C.start();
};
C.start=function(){
 const conv=S.cur;
 conv.msgs.forEach(()=>0);
 document.querySelectorAll('.msg.ai [data-a=regen]').forEach(b=>b.classList.add('hidden'));
 const ai={role:'assistant',c:'',t:'',s:0,src:[],v:0,demo:!K.native.hasKey()};conv.msgs.push(ai);
 const view=new AIView(ai,{last:true,live:true});$('msgs').appendChild(view.el);S.live=view;S.streaming=true;K.home.refreshComposer();C.scrollEnd(true);
 const hist=toApi(conv.msgs.slice(0,-1));
 const lastUser=[...conv.msgs].reverse().find(m=>m.role==='user');
 const run=function(ctx){
  const pr=K.persona();ctx=(pr?pr+(ctx?'\n\n':''):'')+(ctx||'');
  let t0=0,first=1;view.setStatus(K.t('thinking'));
  S.handle=K.native.chat(hist,S.think,ctx,function(ev){
   if(view.finished)return;
   if(first){first=0;view.status='';}
   if(ev.t==='think'){if(!t0)t0=Date.now();ai.t+=ev.d;view.open=true;view.thinking=true;view.dirty()}
   else if(ev.t==='text'){if(!ai.c&&t0&&!ai.s){ai.s=Math.max(1,Math.round((Date.now()-t0)/1000));view.open=false;view.thinking=false}ai.c+=ev.d;view.dirty();if(S.voiceMode&&K.voice.active)K.voice.onStream(ai.c)}
   else if(ev.t==='done'){if(t0&&!ai.s)ai.s=Math.max(1,Math.round((Date.now()-t0)/1000));C.finish(view)}
   else if(ev.t==='err'){ai.c+=(ai.c?'\n\n':'')+'⚠ '+ev.m;if(t0&&!ai.s)ai.s=1;C.finish(view)}
  });
 };
 const withRepos=function(ctx){
  const repos=K.gh.tok()?K.gh.sel():[];if(!repos.length)return Promise.resolve(ctx);
  view.setStatus(K.t('gh_reading'));
  return K.gh.context(repos).then(function(t){view.setStatus('');return (ctx?ctx+'\n\n':'')+t}).catch(function(){view.setStatus('');return ctx});
 };
 if(S.search){
  view.searching=true;view.renderSources();view.setStatus(K.t('searching'));
  K.native.search(lastUser.c||'').then(function(res){
   if(view.finished)return;view.searching=false;ai.src=Array.isArray(res)?res:[];view.setStatus('');view.renderSources();view.render();
   return withRepos(buildCtx(ai.src)).then(function(c){if(!view.finished)run(c)});
  });
 }else withRepos('').then(function(c){if(!view.finished)run(c)});
};
C.finish=function(view){
 if(view.finished)return;view.finished=true;view.done=true;view.thinking=false;S.streaming=false;S.live=null;S.handle=null;
 if(!view.m.c&&!view.m.t)view.m.c='—';
 view.status='';view.render();K.db.save();K.home.refreshComposer();C.scrollEnd(true);
 if(S.voiceMode&&K.voice.active)K.voice.onReply(view.m.c);else if(K.pref.bool('sound',false))K.native.speak(view.m.c);
};
C.AIView=AIView;
C.stop=function(){if(S.handle)S.handle.cancel();K.native.stopSpeak();if(S.live){if(!S.live.m.s&&S.live.m.t)S.live.m.s=1;C.finish(S.live)}};
C.action=function(v,a){
 if(a==='copy'){K.native.copy(v.m.c)}
 else if(a==='share'){K.native.share(v.m.c)}
 else if(a==='up'){v.m.v=v.m.v===1?0:1;v.render();K.db.save()}
 else if(a==='down'){v.m.v=v.m.v===-1?0:-1;v.render();K.db.save()}
 else if(a==='regen'){if(S.streaming)return;const conv=S.cur;conv.msgs.pop();v.el.remove();C.start()}
 K.native.haptic();
};
})();
