/* In-app dictation (no Google dialog): live waveform + live text, then insert into the message box.
   Two looks: "bar" (replaces the composer) and "orb" (bottom sheet with a pulsing orb). */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
let D=null;
const NB=30;
function norm(v){return Math.max(.06,Math.min(1,(v+2)/11))}
K.dictate={
 style(){return K.pref.get('dict_style','bar')},
 active(){return !!D},
 start(){
  if(D)return;
  const ta=$('ta');ta.blur();
  const st=K.dictate.style();
  D={st,acc:'',part:'',lv:new Array(NB).fill(.08),t0:Date.now(),errs:0,live:null,lvl:.08};
  const root=document.createElement('div');root.id='dict';root.className='dict dk-'+st;
  const bars='<div class="dwave">'+'<i></i>'.repeat(NB)+'</div>';
  if(st==='orb'){
   root.innerHTML='<div class="d-bd"></div><div class="d-sheet"><i class="d-grab"></i><div class="d-title">'+T('جارٍ الاستماع…','Listening…')+'</div>'
    +'<div class="d-orb"><span class="o1"></span><span class="o2"></span><span class="o3"></span><span class="core"></span></div>'
    +'<div class="d-text" id="dText"><span class="ph">'+T('تحدّث الآن…','Speak now…')+'</span></div><div class="d-time" id="dTime">0:00</div>'
    +'<div class="d-btns"><button class="d-x" id="dX">'+K.icon('x',26,2.4)+'</button><button class="d-ok" id="dOk">'+K.icon('check',22,2.8)+'<span>'+T('إدراج في الرسالة','Insert')+'</span></button></div></div>';
  }else{
   const c=$('composer').getBoundingClientRect();
   root.innerHTML='<div class="d-text card" id="dText" style="bottom:'+(innerHeight-c.top+12)+'px"><span class="ph">'+T('تحدّث الآن…','Speak now…')+'</span></div>'
    +'<div class="d-bar" style="left:'+c.left+'px;top:'+c.top+'px;width:'+c.width+'px;height:'+Math.max(56,c.height)+'px"><button class="d-x" id="dX">'+K.icon('x',22,2.4)+'</button>'+bars+'<span class="d-time" id="dTime">0:00</span><button class="d-ok" id="dOk">'+K.icon('check',22,2.8)+'</button></div>';
  }
  document.body.appendChild(root);D.root=root;
  root.querySelector('#dX').onclick=()=>K.dictate.stop(false);
  root.querySelector('#dOk').onclick=()=>K.dictate.stop(true);
  const bd=root.querySelector('.d-bd');if(bd)bd.onclick=()=>K.dictate.stop(true);
  D.tick=setInterval(()=>{
   if(!D)return;D.lv.shift();D.lv.push(D.lvl);D.lvl=Math.max(.08,D.lvl*.82);
   const is=D.root.querySelectorAll('.dwave i');is.forEach((e,i)=>e.style.transform='scaleY('+D.lv[i].toFixed(2)+')');
   const o=D.root.querySelector('.d-orb');if(o)o.style.setProperty('--l',D.lv[NB-1].toFixed(2));
   const s=Math.floor((Date.now()-D.t0)/1000),t=$('dTime');if(t)t.textContent=Math.floor(s/60)+':'+String(s%60).padStart(2,'0');
  },60);
  K.native.haptic();K.dictate.listen();
 },
 listen(){
  if(!D)return;
  const me=D;
  me.live=K.native.voiceLive(function(ev){
   if(D!==me)return;
   if(ev.t==='rms'){me.lvl=Math.max(me.lvl,norm(ev.v))}
   else if(ev.t==='partial'){me.part=ev.d||'';me.errs=0;K.dictate.paint()}
   else if(ev.t==='final'){const t=(ev.d||'').trim();if(t){me.acc+=(me.acc?' ':'')+t;me.errs=0}me.part='';K.dictate.paint();K.dictate.again()}
   else if(ev.t==='err'){
    if(ev.c===9||ev.m==='denied'){K.toast(K.t('v_denied'));K.dictate.stop(false);return}
    if(ev.m==='unavailable'){K.toast(K.t('voice_fail'));K.dictate.stop(false);return}
    me.errs++;if(me.errs>5){K.toast(K.t('voice_fail'));K.dictate.stop(true);return}K.dictate.again()}
  });
 },
 again(){const me=D;if(!me)return;try{me.live&&me.live.stop()}catch(e){}me.live=null;setTimeout(()=>{if(D===me)K.dictate.listen()},220)},
 paint(){
  const e=$('dText');if(!e||!D)return;const t=(D.acc+(D.acc&&D.part?' ':'')+D.part).trim();
  e.classList.toggle('has',!!t);
  e.innerHTML=t?esc(t):'<span class="ph">'+T('تحدّث الآن…','Speak now…')+'</span>';e.scrollTop=e.scrollHeight;
 },
 stop(insert){
  const me=D;if(!me)return;D=null;clearInterval(me.tick);try{me.live&&me.live.stop()}catch(e){}
  const text=(me.acc+(me.acc&&me.part?' ':'')+me.part).trim();
  const r=me.root;r.classList.add('out');setTimeout(()=>r.remove(),260);
  if(insert&&text){const ta=$('ta');ta.value+=(ta.value&&!/\s$/.test(ta.value)?' ':'')+text;ta.dispatchEvent(new Event('input'));K.native.haptic();ta.focus()}
 }
};
})();
