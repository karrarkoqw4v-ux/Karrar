/* Live voice screen (Gemini-Live style): listen (live recognition) -> send -> think -> speak the answer -> listen again.
   Black stage, colourful sparkle + greeting, a multi-colour glow rising from the bottom, round control row with a glowing pill. */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);const S=()=>K.state;
const V=K.voice={active:false,muted:false,state:'listening',live:null,partial:'',lvl:0,heard:false};
const T=(a,e)=>K.lang()==='ar'?a:e;
/* markdown -> speakable plain text (code blocks are skipped) */
function plain(t){return String(t||'').replace(/```[\s\S]*?(```|$)/g,' ').replace(/`([^`]*)`/g,'$1').replace(/\[\d{1,2}\]/g,'').replace(/^\s*#{1,6}\s*/gm,'').replace(/^\s*>\s?/gm,'').replace(/^\s*[-*•]\s+/gm,'').replace(/\*\*?([^*]+)\*\*?/g,'$1').replace(/\|/g,' ').replace(/\s{2,}/g,' ').trim()}
V.plain=plain;
const STAR='<svg viewBox="0 0 64 64" width="84" height="84"><defs><linearGradient id="vg" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#ffb300"/><stop offset=".33" stop-color="#34c759"/><stop offset=".66" stop-color="#4d8dff"/><stop offset="1" stop-color="#ff453a"/></linearGradient></defs><path fill="url(#vg)" d="M32 3C34 19 45 30 61 32 45 34 34 45 32 61 30 45 19 34 3 32 19 30 30 19 32 3Z"/></svg>';
const CAM='<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="6.5" width="13" height="11" rx="3.5"/><path d="M20.5 9.5v5"/></svg>';
const CC='<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="4"/><path d="M8 10.5h8M8 14.5h5"/></svg>';
const MON='<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="12" rx="3"/><path d="M8 20h8M12 16v4"/><path d="M10 10l2-2 2 2M12 8v4" stroke-width="1.8"/></svg>';
const DOTS='<svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/></svg>';
/* bottom glow: blue / red / green / yellow light rising from the lower edge, driven by the voice level */
const COL=[[66,133,244],[234,67,53],[52,199,89],[251,188,5],[66,133,244],[168,85,247]];
let fxRaf=0,fxT0=0;
function fxStart(){
 const cv=$('vfx');if(!cv)return;const k=.5;let W=cv.width=Math.round(innerWidth*k),H=cv.height=Math.round(innerHeight*k);const cx=cv.getContext('2d');
 fxT0=performance.now();
 (function frame(ts){
  if(!V.active)return;
  const t=(ts-fxT0)/1000,sp=V.state==='speaking',th=V.state==='thinking';
  const base=V.muted?.25:(V.heard?.7:.5),boost=base+V.lvl*.8+(sp?.25+.2*Math.sin(t*5):0)+(th?.18*Math.sin(t*3):0);
  cx.clearRect(0,0,W,H);
  const g=cx.createLinearGradient(0,H,0,H*.5);g.addColorStop(0,'rgba(30,64,190,'+(.62*Math.min(1,boost)).toFixed(3)+')');g.addColorStop(.5,'rgba(12,28,100,'+(.28*Math.min(1,boost)).toFixed(3)+')');g.addColorStop(1,'rgba(0,0,0,0)');cx.fillStyle=g;cx.fillRect(0,0,W,H);
  cx.globalCompositeOperation='screen';cx.filter='blur(22px)';
  for(let i=0;i<6;i++){
   const c=COL[i],x=W*(i+.5)/6+Math.sin(t*.55+i*1.7)*W*.09,r=H*(.17+.05*Math.sin(t*.8+i*1.3))*(.5+Math.min(1.3,boost)*.7),y=H+H*.02;
   const rg=cx.createRadialGradient(x,y,0,x,y,r);rg.addColorStop(0,'rgba('+c+','+(.34*Math.min(1,boost)).toFixed(3)+')');rg.addColorStop(.6,'rgba('+c+','+(.1*Math.min(1,boost)).toFixed(3)+')');rg.addColorStop(1,'rgba('+c+',0)');
   cx.fillStyle=rg;cx.beginPath();cx.arc(x,y,r,0,6.3);cx.fill()}
  cx.filter='none';cx.globalCompositeOperation='source-over';
  fxRaf=requestAnimationFrame(frame)})(performance.now());
}
function fxStop(){cancelAnimationFrame(fxRaf)}
function build(){
 if($('voice'))return;const d=document.createElement('div');d.id='voice';d.className='voice';
 d.innerHTML='<canvas class="vfx" id="vfx"></canvas>'
 +'<div class="vtopL"><div class="vpg"><button id="vSet">'+DOTS+'</button><button id="vTr">'+CC+'</button></div><button class="vrot" id="vRot">'+K.icon('refresh-cw',22)+'</button></div>'
 +'<div class="stage"><div class="vstar" id="vStar">'+K.LOGO.replace(/lp(\d)/g,'vlp$1')+'</div><div class="vgreet" id="vGreet"></div><div class="vtext" id="vText"></div></div>'
 +'<div class="vtrans" id="vTrans"></div>'
 +'<div class="vbar"><button class="vc vx" id="vX">'+K.icon('x',24,2.2)+'</button><button class="vc vmic" id="vMic"></button><div class="vpill" id="vPill"><i></i><i></i><i></i><i></i></div><button class="vc vcam" id="vCam">'+CAM+'</button><button class="vc vplus" id="vPlus">'+K.icon('plus',26,2.2)+'</button></div>'
 +'<div class="vsheet" id="vSheet"><div class="vsbd"></div><div class="vsh"><i class="vgr"></i><div class="vtiles">'
 +[['vScr',T('مشاركة الشاشة','Share screen'),'#4d6bfe,#8b5cf6',MON],['vAlb',T('الصور','Photos'),'#10b981,#22d3ee',K.icon('image',28)],['vFile',T('الملفات','Files'),'#f59e0b,#ef4444',K.icon('paperclip',28)],['vCm',T('الكاميرا','Camera'),'#ec4899,#f43f5e',K.icon('camera',28)],['vImg',T('إنشاء صورة','Create image'),'#8b5cf6,#d946ef',K.icon('sparkles',28)],['vShare',T('مشاركة الرد','Share reply'),'#0ea5e9,#3b82f6',K.icon('share',28)]].map(x=>'<button class="vtile" id="'+x[0]+'"><span class="vti" style="background:linear-gradient(145deg,'+x[2]+')">'+x[3]+'</span><span>'+x[1]+'</span></button>').join('')
 +'</div></div></div>';
 document.body.appendChild(d);
 $('vX').onclick=()=>V.close();$('vMic').onclick=()=>V.toggleMute();
 $('vSet').onclick=async()=>{const i=await K.ui.choose(K.t('v_lang'),['العربية','English'],K.pref.get('voice_lang','ar')==='en'?1:0);if(i!=null){K.pref.set('voice_lang',i===0?'ar':'en');if(!V.muted&&V.state==='listening'){stopLive();startLive()}}};
 $('vTr').onclick=()=>{const t=$('vTrans');if(t.classList.contains('show')){t.classList.remove('show');return}
  const c=S().cur;t.innerHTML=(c?c.msgs.slice(-6):[]).map(m=>'<p><b>'+(m.role==='user'?K.t('v_you'):K.t('v_ai'))+'</b>'+String(m.c||'').replace(/&/g,'&amp;').replace(/</g,'&lt;')+'</p>').join('')||'<p>—</p>';t.classList.add('show')};
 $('vRot').onclick=()=>{K.native.haptic();$('aCam').click()};
 $('vCam').onclick=()=>{K.native.haptic();if(S().atts.length){S().atts=[];K.home.refreshComposer();camState()}else{$('aCam').click()}};
 $('vPlus').onclick=()=>{K.native.haptic();$('vSheet').classList.add('show')};
 $('vSheet').querySelector('.vsbd').onclick=()=>$('vSheet').classList.remove('show');
 const sh=()=>$('vSheet').classList.remove('show');
 $('vShare').onclick=()=>{sh();const c=S().cur,m=c&&[...c.msgs].reverse().find(x=>x.role==='assistant'&&x.c);if(m)K.native.share(m.c);else K.toast(T('لا يوجد رد لمشاركته','No reply to share yet'))};
 $('vScr').onclick=async()=>{sh();const v=$('voice');v.style.visibility='hidden';await new Promise(r=>setTimeout(r,320));let r=null;try{r=await K.native.screenshot()}catch(e){}v.style.visibility='';if(r&&r.th){K.home.addAtt({kind:'img',th:r.th,b64:r.b64});K.toast(T('أُضيفت لقطة الشاشة إلى سؤالك','Screen capture added to your question'))}else K.toast(T('تعذّر التقاط الشاشة','Could not capture the screen'))};
 $('vAlb').onclick=()=>{sh();$('aAlb').click()};
 $('vFile').onclick=()=>{sh();$('aFile').click()};
 $('vCm').onclick=()=>{sh();$('aCam').click()};
 $('vImg').onclick=()=>{sh();V.close();setTimeout(()=>K.imggen.enter(),350)};
}
function camState(){const c=$('vCam');if(c)c.classList.toggle('on',!!(S().atts&&S().atts.length));const r=$('vRot');if(r)r.classList.toggle('show',!!(S().atts&&S().atts.length))}
V.refreshAtts=camState;
function setState(s){V.state=s;const d=$('voice');d.classList.remove('listening','thinking','speaking');d.classList.toggle('muted',V.muted);
 if(!V.muted||s!=='listening')d.classList.add(s);
 const m=$('vMic');m.className='vc vmic'+(V.muted?' off':'');m.innerHTML=K.icon(V.muted?'mic-off':'mic',24);
 const g=$('vGreet'),tx=$('vText');const talk=V.heard||s!=='listening';g.classList.toggle('hide',talk);tx.classList.toggle('show',talk);camState()}
function level(v){const k=Math.max(0,Math.min(1,(v+2)/12));V.lvl=V.lvl*.6+k*.4;const p=$('vPill');if(p)p.style.setProperty('--l',V.lvl.toFixed(3))}
function stopLive(){if(V.live){try{V.live.stop()}catch(e){}V.live=null}level(-2)}
function startLive(){
 if(V.muted||!V.active)return;setState('listening');V.partial='';$('vText').textContent='';
 V.live=K.native.voiceLive(function(ev){
  if(!V.active)return;
  if(ev.t==='rms'){level(ev.v)}
  else if(ev.t==='partial'){V.partial=ev.d;V.heard=true;$('vText').textContent=ev.d;setState('listening')}
  else if(ev.t==='final'){stopLive();const t=(ev.d||'').trim();if(!t){setTimeout(startLive,300);return}V.heard=true;$('vText').textContent=t;V.ask(t)}
  else if(ev.t==='err'){stopLive();
   if(ev.c===9||ev.m==='denied'){K.toast(K.t('v_denied'));V.close();return}
   if(ev.m==='unavailable'){K.toast(K.t('voice_fail'));V.close();return}
   setTimeout(startLive,450)}
 });
}
V.ask=function(text){
 setState('thinking');const ta=$('ta');ta.value=text;S().voiceMode=true;ta.dispatchEvent(new Event('input'));K.chat.send();camState();
};
V.onStream=function(t){/* keep the user's words on screen while the answer streams */};
V.onReply=function(text){
 if(!V.active)return;const sp=plain(text)||text;setState('speaking');$('vText').textContent=sp.slice(0,170);
 window.__ttsDone=function(){if(V.active&&!V.muted)startLive();else if(V.active){setState('listening')}};
 K.native.speak(sp);
};
V.toggleMute=function(){
 V.muted=!V.muted;K.native.haptic();
 if(V.muted){stopLive();if(V.state==='speaking'){K.native.stopSpeak()}setState('listening')}
 else{K.native.stopSpeak();startLive()}
};
V.open=function(){
 build();V.active=true;V.muted=false;V.heard=false;S().voiceMode=true;
 const nm=(K.pref.get('name','')||'').trim();
 const en=nm.replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
 $('vGreet').innerHTML=T('مرحباً'+(en?' '+en:'')+'،<br>لنبدأ','Hi'+(en?' '+en:'')+',<br>let’s begin');
 $('vSheet').classList.remove('show');
 const d=$('voice');d.classList.add('show');
 $('vTrans').classList.remove('show');$('vText').textContent='';
 startLive();fxStop();fxStart();K.app.bars();
};
V.close=function(){
 if(!V.active)return;V.active=false;S().voiceMode=false;stopLive();fxStop();K.native.stopSpeak();window.__ttsDone=null;
 if(S().streaming)K.chat.stop();
 $('voice').classList.remove('show');$('vSheet')&&$('vSheet').classList.remove('show');K.app.bars();
};
})();
