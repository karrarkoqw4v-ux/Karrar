/* Voice conversation: listen (live recognition) -> send -> think -> speak the answer -> listen again */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);const S=()=>K.state;
const V=K.voice={active:false,muted:false,state:'listening',live:null,partial:'',lvl:0};
/* markdown -> speakable plain text (code blocks are skipped) */
function plain(t){return String(t||'').replace(/```[\s\S]*?(```|$)/g,' ').replace(/`([^`]*)`/g,'$1').replace(/\[\d{1,2}\]/g,'').replace(/^\s*#{1,6}\s*/gm,'').replace(/^\s*>\s?/gm,'').replace(/^\s*[-*•]\s+/gm,'').replace(/\*\*?([^*]+)\*\*?/g,'$1').replace(/\|[-:| ]+\|/g,' ').replace(/\|/g,' ').replace(/\s+/g,' ').trim()}
V.plain=plain;
/* colour fall: green / blue / red / yellow light curtains + glowing drops falling from the top of the screen */
const COL=[[52,199,89],[77,107,254],[255,69,58],[255,214,10]];
let fxRaf=0,fxT0=0,drops=[];
function fxStart(){
 const cv=$('vfx');if(!cv)return;const k=.5;let W=cv.width=Math.round(innerWidth*k),H=cv.height=Math.round(innerHeight*k);const cx=cv.getContext('2d');
 drops=[];for(let i=0;i<26;i++)drops.push({x:Math.random(),y:-Math.random(),v:.18+Math.random()*.5,c:COL[i%4],r:1.4+Math.random()*2.4,l:30+Math.random()*60});
 fxT0=performance.now();
 (function frame(ts){
  if(!V.active){return}
  const t=(ts-fxT0)/1000,sp=V.state==='speaking',th=V.state==='thinking',boost=(V.muted?.12:1)*(.5+V.lvl*.55+(sp?.35:0)+(th?.15:0));
  cx.clearRect(0,0,W,H);cx.globalCompositeOperation='lighter';
  /* curtains */
  cx.filter='blur(14px)';
  for(let i=0;i<8;i++){
   const c=COL[i%4],x=W*(i+.5)/8+Math.sin(t*.7+i*1.3)*W*.05,len=H*(.16+.12*(Math.sin(t*.55+i*1.9)*.5+.5))*boost,w=W*.16;
   const g=cx.createLinearGradient(0,0,0,len);g.addColorStop(0,'rgba('+c+',.55)');g.addColorStop(.55,'rgba('+c+',.16)');g.addColorStop(1,'rgba('+c+',0)');
   cx.fillStyle=g;cx.beginPath();cx.roundRect?cx.roundRect(x-w/2,-30,w,len+30,w/2):cx.rect(x-w/2,-30,w,len+30);cx.fill();
  }
  cx.filter='none';
  /* falling glowing drops */
  drops.forEach(function(d){d.y+=d.v*.016*(1+V.lvl*1.6+(sp?.8:0));if(d.y>1.05){d.y=-.05-Math.random()*.2;d.x=Math.random()}
   const x=d.x*W+Math.sin(t*1.4+d.x*9)*6,y=d.y*H,tr=d.l*(.8+V.lvl);
   const g=cx.createLinearGradient(0,y-tr,0,y);g.addColorStop(0,'rgba('+d.c+',0)');g.addColorStop(1,'rgba('+d.c+',.9)');
   cx.strokeStyle=g;cx.lineWidth=d.r*1.3;cx.lineCap='round';cx.beginPath();cx.moveTo(x,y-tr);cx.lineTo(x,y);cx.stroke();
   cx.fillStyle='rgba('+d.c+',.95)';cx.shadowColor='rgb('+d.c+')';cx.shadowBlur=10;cx.beginPath();cx.arc(x,y,d.r,0,6.3);cx.fill();cx.shadowBlur=0});
  cx.globalCompositeOperation='source-over';
  fxRaf=requestAnimationFrame(frame)})(performance.now());
}
function fxStop(){cancelAnimationFrame(fxRaf)}
function build(){
 if($('voice'))return;const d=document.createElement('div');d.id='voice';d.className='voice';
 d.innerHTML='<canvas class="vfx" id="vfx"></canvas><button class="vtop vr" id="vSet">'+K.icon('sliders-horizontal',22)+'</button><button class="vtop vl" id="vTr">'+K.icon('align-justify',22)+'</button>'
 +'<div class="stage"><div class="orbbox"><div class="aura"></div><div class="halo"></div><div class="orb" id="orb"><i class="c1"></i><i class="c2"></i><i class="c3"></i><i class="c4"></i><i class="shine"></i></div></div><div class="vtext" id="vText"></div></div>'
 +'<div class="vtrans" id="vTrans"></div><div class="vtip" id="vTip">'+K.t('v_tip')+'</div>'
 +'<div class="vbar"><div class="vpill"><span id="vState"></span>'+K.icon('plus',22)+'</div><button class="vmic" id="vMic"></button><button class="vx" id="vX">'+K.icon('x',22,2.4)+'</button></div>';
 document.body.appendChild(d);
 $('vX').onclick=()=>V.close();$('vMic').onclick=()=>V.toggleMute();
 $('vSet').onclick=async()=>{const i=await K.ui.choose(K.t('v_lang'),['العربية','English'],K.pref.get('voice_lang','ar')==='en'?1:0);if(i!=null){K.pref.set('voice_lang',i===0?'ar':'en');if(!V.muted&&V.state==='listening'){stopLive();startLive()}}};
 $('vTr').onclick=()=>{const t=$('vTrans');if(t.classList.contains('show')){t.classList.remove('show');return}
  const c=S().cur;t.innerHTML=(c?c.msgs.slice(-6):[]).map(m=>'<p><b>'+(m.role==='user'?K.t('v_you'):K.t('v_ai'))+'</b>'+String(m.c||'').replace(/&/g,'&amp;').replace(/</g,'&lt;')+'</p>').join('')||'<p>—</p>';t.classList.add('show')};
}
function setState(s){V.state=s;const d=$('voice');d.classList.remove('listening','thinking','speaking');d.classList.toggle('muted',V.muted);
 if(!V.muted||s!=='listening')d.classList.add(s);
 $('vState').textContent=V.muted&&s==='listening'?K.t('v_muted'):K.t({listening:'v_listen',thinking:'v_think',speaking:'v_speak'}[s]);
 const m=$('vMic');m.className='vmic'+(V.muted?' off':'');m.innerHTML=K.icon(V.muted?'mic-off':'mic',22)}
function level(v){const o=$('orb');if(!o)return;const k=Math.max(0,Math.min(1,(v+2)/12));V.lvl=V.lvl*.6+k*.4;o.style.setProperty('--s',(1+k*.16).toFixed(3))}
function stopLive(){if(V.live){try{V.live.stop()}catch(e){}V.live=null}level(-2)}
function startLive(){
 if(V.muted||!V.active)return;setState('listening');V.partial='';$('vText').textContent='';
 V.live=K.native.voiceLive(function(ev){
  if(!V.active)return;
  if(ev.t==='rms'){level(ev.v)}
  else if(ev.t==='partial'){V.partial=ev.d;$('vText').textContent=ev.d}
  else if(ev.t==='final'){stopLive();const t=(ev.d||'').trim();if(!t){setTimeout(startLive,300);return}$('vText').textContent=t;V.ask(t)}
  else if(ev.t==='err'){stopLive();
   if(ev.c===9||ev.m==='denied'){K.toast(K.t('v_denied'));V.close();return}
   if(ev.m==='unavailable'){K.toast(K.t('voice_fail'));V.close();return}
   setTimeout(startLive,450)}
 });
}
V.ask=function(text){
 setState('thinking');const ta=$('ta');ta.value=text;S().voiceMode=true;ta.dispatchEvent(new Event('input'));K.chat.send();
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
 build();V.active=true;V.muted=false;S().voiceMode=true;
 const d=$('voice');d.classList.add('show');
 $('vTip').classList.remove('hide');setTimeout(()=>{const t=$('vTip');if(t)t.classList.add('hide')},4200);
 $('vTrans').classList.remove('show');$('vText').textContent='';
 startLive();fxStop();fxStart();K.app.bars();
};
V.close=function(){
 if(!V.active)return;V.active=false;S().voiceMode=false;stopLive();fxStop();K.native.stopSpeak();window.__ttsDone=null;
 if(S().streaming)K.chat.stop();
 $('voice').classList.remove('show');K.app.bars();
};
})();
