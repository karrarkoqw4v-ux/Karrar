/* Visual effects: aurora blobs + falling coloured characters (canvas), typewriter helpers */
window.K=window.K||{};
(function(){
let raf=0,mode=0,cv,cx,W,H,t0=0;const G='ابتثجحخدذرزسشصضطعغفقكلمنهوي01<>{}/=;AIkarrar';
let cols=[],cell=17;
function resize(){cv=document.getElementById('fx');if(!cv)return;const d=Math.min(window.devicePixelRatio||1,2);W=cv.width=innerWidth*d;H=cv.height=innerHeight*d;cx=cv.getContext('2d');cx.setTransform(d,0,0,d,0,0);W/=d;H/=d;
 const n=Math.floor(W/cell);cols=[];for(let i=0;i<n;i+=2)cols.push({x:i*cell+cell/2,y:-Math.random()*30,s:3+Math.random()*5})}
const pal=['#4d6bfe','#8b5cf6','#22d3ee','#34d399'];
function frame(ts){
 if(!mode){return}
 const dt=Math.min(.05,(ts-t0)/1000||.016);t0=ts;
 cx.clearRect(0,0,W,H);const dark=document.documentElement.dataset.theme==='dark';
 const t=ts/22000*Math.PI*2;
 [[.15,.08,'rgba(77,107,254,A)'],[.95,.3,'rgba(139,92,246,A)'],[.4,.75,'rgba(34,211,238,A)']].forEach((b,i)=>{
  const x=W*b[0]+Math.sin(t+i*2.1)*W*.22,y=H*b[1]+Math.cos(t+i*2.1)*H*.1,r=Math.max(W,H)*.62,g=cx.createRadialGradient(x,y,0,x,y,r);
  g.addColorStop(0,b[2].replace('A',dark?(i?.28:.4):.16));g.addColorStop(1,b[2].replace('A',0));cx.fillStyle=g;cx.fillRect(0,0,W,H)});
 if(mode===2){
  cx.font='700 13px KAr, sans-serif';cx.textAlign='center';
  cols.forEach(function(c,ci){c.y+=c.s*dt;const rows=H/cell;if(c.y-11>rows*.8){c.y=-Math.random()*14;c.s=3+Math.random()*5}
   const base=pal[(ci+Math.floor(ts/1500))%4];
   for(let k=0;k<11;k++){const row=Math.floor(c.y)-k;if(row<0)continue;const y=row*cell+cell,f=1-y/(H*.78);if(f<=0)continue;
    const ch=G[Math.floor(((ci*131+row*31+Math.floor(ts/(k?420:110)))*2654435761>>>5)%G.length)];
    cx.globalAlpha=Math.max(0,(k===0?.9:.42*(1-k/11))*f*(dark?1:.7));cx.fillStyle=k===0?'#fff':base;
    if(k===0){cx.shadowColor=base;cx.shadowBlur=8}else cx.shadowBlur=0;cx.fillText(ch,c.x,y)}});
  cx.globalAlpha=1;cx.shadowBlur=0}
 raf=requestAnimationFrame(frame)}
K.fx={
 start(m){this.stop();mode=m;if(!m)return;resize();t0=performance.now();raf=requestAnimationFrame(frame)},
 stop(){mode=0;cancelAnimationFrame(raf);const c=document.getElementById('fx');if(c){const x=c.getContext('2d');x.clearRect(0,0,c.width,c.height)}},
 /* typewriter: type text into el (with blinking caret) */
 type(el,text,ms,done){clearTimeout(el._tw);el.textContent='';el.classList.add('caret');let i=0;
  (function step(){i++;el.textContent=text.slice(0,i);if(i<text.length)el._tw=setTimeout(step,ms||55);else{el.classList.remove('caret');done&&done()}})()},
 /* loop phrases: type, hold, erase, next */
 loop(el,phrases){clearTimeout(el._tw);let p=0,i=0,mode='t';el.classList.add('caret');
  (function step(){const s=phrases[p];let d=60;
   if(mode==='t'){i++;if(i>=s.length){mode='h';d=2200}}else if(mode==='h'){mode='e';d=30}else{i--;d=26;if(i<=0){i=0;mode='t';p=(p+1)%phrases.length;d=320}}
   el.textContent=s.slice(0,Math.max(0,i));el._tw=setTimeout(step,d)})()}
};
window.addEventListener('resize',function(){if(mode)resize()});
})();
