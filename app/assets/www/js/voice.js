/* Voice conversation: listen (live recognition) -> send -> think -> speak the answer -> listen again */
window.K=window.K||{};
(function(){
const $=id=>document.getElementById(id);const S=()=>K.state;
const V=K.voice={active:false,muted:false,state:'listening',live:null,partial:''};
/* markdown -> speakable plain text (code blocks are skipped) */
function plain(t){return String(t||'').replace(/```[\s\S]*?(```|$)/g,' ').replace(/`([^`]*)`/g,'$1').replace(/\[\d{1,2}\]/g,'').replace(/^\s*#{1,6}\s*/gm,'').replace(/^\s*>\s?/gm,'').replace(/^\s*[-*•]\s+/gm,'').replace(/\*\*?([^*]+)\*\*?/g,'$1').replace(/\|[-:| ]+\|/g,' ').replace(/\|/g,' ').replace(/\s+/g,' ').trim()}
V.plain=plain;
function build(){
 if($('voice'))return;const d=document.createElement('div');d.id='voice';d.className='voice';
 d.innerHTML='<button class="vtop vr" id="vSet">'+K.icon('sliders-horizontal',22)+'</button><button class="vtop vl" id="vTr">'+K.icon('align-justify',22)+'</button>'
 +'<div class="stage"><div class="orbbox"><div class="halo"></div><div class="orb" id="orb"><i class="c1"></i><i class="c2"></i><i class="c3"></i><i class="c4"></i><i class="shine"></i></div></div><div class="vtext" id="vText"></div></div>'
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
function level(v){const o=$('orb');if(!o)return;const k=Math.max(0,Math.min(1,(v+2)/12));o.style.setProperty('--s',(1+k*.16).toFixed(3))}
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
 startLive();
};
V.close=function(){
 if(!V.active)return;V.active=false;S().voiceMode=false;stopLive();K.native.stopSpeak();window.__ttsDone=null;
 if(S().streaming)K.chat.stop();
 $('voice').classList.remove('show');
};
})();
