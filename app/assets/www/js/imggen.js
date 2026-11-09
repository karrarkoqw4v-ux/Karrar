/* Image creation (demo): pick a size per platform, pick accuracy, describe or upload images, watch a colourful "creating" box, get a generated picture */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const em=c=>'<img class="gb-em" src="emoji/'+c+'.svg" alt="">';
const G=[
 {id:'tiktok',n:'TikTok',it:[['فيديو / صورة عمودية','Vertical video / photo',1080,1920],['صورة الملف الشخصي','Profile photo',200,200]]},
 {id:'instagram',n:'Instagram',it:[['منشور مربع','Square post',1080,1080],['منشور عمودي','Portrait post',1080,1350],['ستوري / ريلز','Story / Reels',1080,1920],['الملف الشخصي','Profile',320,320]]},
 {id:'facebook',n:'Facebook',it:[['منشور','Post',1200,630],['منشور مربع','Square post',1080,1080],['ستوري','Story',1080,1920],['صورة الغلاف','Cover',820,312]]},
 {id:'x',n:'X (Twitter)',it:[['منشور','Post',1600,900],['الغلاف','Header',1500,500],['الملف الشخصي','Profile',400,400]]},
 {id:'youtube',n:'YouTube',it:[['صورة مصغّرة','Thumbnail',1280,720],['بانر القناة','Channel banner',2560,1440],['الملف الشخصي','Profile',800,800],['Shorts','Shorts',1080,1920]]},
 {id:'snapchat',n:'Snapchat',it:[['سناب','Snap',1080,1920]]},
 {id:'pinterest',n:'Pinterest',it:[['بن عمودي','Standard pin',1000,1500],['بن مربع','Square pin',1000,1000]]},
 {id:'linkedin',n:'LinkedIn',it:[['منشور','Post',1200,627],['الغلاف','Banner',1584,396],['الملف الشخصي','Profile',400,400]]},
 {id:'whatsapp',n:'WhatsApp',it:[['حالة','Status',1080,1920],['الملف الشخصي','Profile',640,640]]},
 {id:'telegram',n:'Telegram',it:[['صورة قناة / بوت','Channel / bot photo',512,512],['منشور','Post',1280,720]]},
 {id:'discord',n:'Discord',it:[['أيقونة سيرفر','Server icon',512,512],['بانر','Banner',960,540],['صورة بوت','Bot avatar',1024,1024]]},
 {id:'twitch',n:'Twitch',it:[['بانر','Banner',1200,480],['الملف الشخصي','Profile',256,256]]},
 {id:'',n:T('ملفات شخصية وبوتات','Profiles & bots'),ic:'#8b5cf6',it:[['صورة بوت','Bot avatar',1024,1024],['صورة شخصية','Profile picture',512,512],['شعار','Logo',1000,1000],['أيقونة تطبيق','App icon',1024,1024]]},
 {id:'',n:T('أبعاد عامة','General ratios'),ic:'#0ea5e9',it:[['1:1','1:1',1080,1080],['4:3','4:3',1440,1080],['16:9','16:9',1920,1080],['9:16','9:16',1080,1920],['3:2','3:2',1620,1080],['2:3','2:3',1080,1620]]},
 {id:'',n:T('خلفيات','Wallpapers'),ic:'#f59e0b',it:[['خلفية هاتف','Phone wallpaper',1080,2400],['خلفية حاسوب','Desktop wallpaper',1920,1080],['خلفية تابلت','Tablet',2048,1536]]}
];
const RES={1080:1080,'2k':1440,'4k':2160};
const resLabel=r=>r==='1080'||r===1080?'1080':String(r).toUpperCase();
const st=()=>K.state;
const D={get size(){return st().img||null},get res(){return st().res||'1080'}};

/* ---------- mulberry32 ---------- */
function rng(seed){let a=seed>>>0;return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
function hash(s){let h=2166136261;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}return h>>>0}
function loadImg(src){return new Promise(r=>{if(!src)return r(null);const i=new Image();i.onload=()=>r(i);i.onerror=()=>r(null);i.src=src})}
function outDims(g,res){
 const sh=RES[res]||1080,short=Math.min(g.w,g.h),s=sh/short;let w=Math.round(g.w*s),h=Math.round(g.h*s);
 const mx=4096,k=Math.max(w,h)>mx?mx/Math.max(w,h):1;return [Math.round(w*k),Math.round(h*k)];
}
async function draw(cv,w,h,g,base){
 cv.width=w;cv.height=h;const x=cv.getContext('2d'),r=rng(g.seed),S=Math.min(w,h),hue=r()*360;
 const col=(o,l,a)=>'hsla('+((hue+o)%360)+',85%,'+l+'%,'+(a==null?1:a)+')';
 const ang=r()*6.283,cx=w/2,cy=h/2,L=Math.hypot(w,h)/2;
 let gr=x.createLinearGradient(cx-Math.cos(ang)*L,cy-Math.sin(ang)*L,cx+Math.cos(ang)*L,cy+Math.sin(ang)*L);
 gr.addColorStop(0,col(0,38));gr.addColorStop(.5,col(70,46));gr.addColorStop(1,col(160,32));x.fillStyle=gr;x.fillRect(0,0,w,h);
 if(base){const k=Math.max(w/base.width,h/base.height),bw=base.width*k,bh=base.height*k;x.drawImage(base,(w-bw)/2,(h-bh)/2,bw,bh)}
 x.globalCompositeOperation=base?'soft-light':'screen';
 for(let i=0;i<9;i++){const bx=r()*w,by=r()*h,br=S*(.35+r()*.6);const rg=x.createRadialGradient(bx,by,0,bx,by,br);rg.addColorStop(0,col(r()*200,60,.75));rg.addColorStop(1,col(r()*200,50,0));x.fillStyle=rg;x.fillRect(0,0,w,h)}
 x.globalCompositeOperation='screen';
 for(let i=0;i<5;i++){const a0=r()*w,wd=S*(.05+r()*.12);x.save();x.translate(a0,0);x.rotate(.35+r()*.3);const lg=x.createLinearGradient(0,0,wd,0);lg.addColorStop(0,'rgba(255,255,255,0)');lg.addColorStop(.5,'rgba(255,255,255,.10)');lg.addColorStop(1,'rgba(255,255,255,0)');x.fillStyle=lg;x.fillRect(0,-h*.2,wd,h*1.6);x.restore()}
 x.globalCompositeOperation='lighter';
 for(let i=0;i<70;i++){const px=r()*w,py=r()*h,pr=S*(.002+r()*.007);const rg=x.createRadialGradient(px,py,0,px,py,pr*5);rg.addColorStop(0,'rgba(255,255,255,.95)');rg.addColorStop(.25,col(r()*120,75,.6));rg.addColorStop(1,'rgba(255,255,255,0)');x.fillStyle=rg;x.beginPath();x.arc(px,py,pr*5,0,6.283);x.fill()}
 x.globalCompositeOperation='source-over';
 const vg=x.createRadialGradient(cx,cy,S*.3,cx,cy,L);vg.addColorStop(0,'rgba(0,0,0,0)');vg.addColorStop(1,'rgba(0,0,0,.5)');x.fillStyle=vg;x.fillRect(0,0,w,h);
 const txt=(g.prompt||'').trim();
 if(txt){
  const fs=Math.max(14,Math.round(S*.07)),rtl=/[؀-ۿ]/.test(txt);
  x.font='800 '+fs+'px Almarai, Cairo, "IBM Plex Sans Arabic", sans-serif';x.direction=rtl?'rtl':'ltr';x.textAlign='center';x.textBaseline='middle';
  const words=txt.split(/\s+/),lines=[];let cur='';const mw=w*.78;
  words.forEach(wd=>{const t=cur?cur+' '+wd:wd;if(x.measureText(t).width>mw&&cur){lines.push(cur);cur=wd}else cur=t});if(cur)lines.push(cur);
  const ls=lines.slice(0,5),lh=fs*1.35,y0=h*.5-(ls.length-1)*lh/2;
  x.shadowColor='rgba(0,0,0,.55)';x.shadowBlur=fs*.5;x.fillStyle='#fff';
  ls.forEach((l,i)=>x.fillText(l,w/2,y0+i*lh));x.shadowBlur=0;
 }
 const wm=Math.max(10,Math.round(S*.028));x.font='700 '+wm+'px Almarai, sans-serif';x.direction='ltr';x.textAlign='right';x.fillStyle='rgba(255,255,255,.75)';x.fillText('Bawsala · DEMO',w-wm*1.1,h-wm*1.4);
}

/* ---------- card html ---------- */
function boxStyle(g){const r=g.w/g.h;const wpx=r>=1?340:Math.max(190,Math.round(380*r));return 'aspect-ratio:'+g.w+'/'+g.h+';width:min(100%,'+wpx+'px)'}
K.imggen={
 html(g,live){
  if(g.state==='run'){
   const sec=Math.max(1,Math.round((Date.now()-g.t0)/1000));
   const star='<svg viewBox="0 0 24 24"><path d="M12 2C12.6 7.4 16.6 11.4 22 12 16.6 12.6 12.6 16.6 12 22 11.4 16.6 7.4 12.6 2 12 7.4 11.4 11.4 7.4 12 2Z"/></svg>';
   return '<div class="genbox run" style="'+boxStyle(g)+'"><div class="gb-mesh"><i></i><i></i><i></i><i></i><i></i><i></i></div><div class="gb-shine"></div>'
    +'<span class="gb-tw t1">'+star+'</span><span class="gb-tw t2">'+star+'</span><span class="gb-tw t3">'+star+'</span><span class="gb-tw t4">'+star+'</span><span class="gb-tw t5">'+star+'</span>'
    +'<div class="gb-star"><span class="halo"></span>'+star+'</div>'
    +'<div class="gb-txt"><b>'+T('جارٍ إنشاء الصورة','Creating the image')+'</b><span class="gb-sec">'+T(sec+' ثانية',sec+' s')+'</span></div></div>';
  }
  const od=outDims(g,g.res);
  return '<div class="genbox done" style="'+boxStyle(g)+'"><img src="'+g.thumb+'" alt=""><span class="gb-tag">'+T('تجريبي','Demo')+'</span></div>'
   +'<div class="gb-meta">'+od[0]+'×'+od[1]+' · '+resLabel(g.res)+(g.plat?' · '+esc(g.plat):'')+' · '+T('أُنشئت في '+g.took+' ثانية','made in '+g.took+' s')+'</div>'
   +'<div class="gb-acts"><button data-g="save">'+K.icon('arrow-up',17,2.6).replace('<svg ','<svg style="transform:rotate(180deg)" ')+'<span>'+T('تحميل','Download')+'</span></button><button data-g="again">'+K.icon('refresh-cw',17)+'<span>'+T('إعادة الإنشاء','Regenerate')+'</span></button></div>';
 },
 /* send handler: called from chat.js when image mode is on */
 start(conv,m){
  const s=D.size,res=D.res;
  const ai={role:'assistant',c:'',t:'',s:0,src:[],v:0,gen:{state:'run',w:s.w,h:s.h,res:res,plat:s.plat,prompt:m.c||'',seed:hash((m.c||'')+Date.now()),t0:Date.now(),base:(m.imgs||[])[0]||''}};
  conv.msgs.push(ai);
  const view=new K.chat.AIView(ai,{last:true,live:true});$('msgs').appendChild(view.el);K.chat.scrollEnd(true);
  K.db.save();
  K.imggen.run(view);
 },
 async run(view){
  const g=view.m.gen;g.t0=g.t0||Date.now();g.state='run';view.render();
  const iv=setInterval(()=>{const e=view.el.querySelector('.gb-sec');if(!e){if(!document.body.contains(view.el))clearInterval(iv);return}const sec=Math.max(1,Math.round((Date.now()-g.t0)/1000));e.textContent=T(sec+' ثانية',sec+' s')},400);
  const base=await loadImg(g.base),t=3800+Math.random()*1800;
  await new Promise(r=>setTimeout(r,t));
  clearInterval(iv);
  const sc=document.createElement('canvas'),k=720/Math.max(g.w,g.h),tw=Math.max(80,Math.round(g.w*k)),th=Math.max(80,Math.round(g.h*k));
  await draw(sc,tw,th,g,base);
  g.thumb=sc.toDataURL('image/jpeg',.82);g.took=Math.max(1,Math.round((Date.now()-g.t0)/1000));g.state='done';
  view.done=true;view.render();K.db.save();K.home.refreshComposer();
 },
 async act(view,k){
  const g=view.m.gen;if(!g)return;
  if(k==='again'){g.seed=hash((g.prompt||'')+Date.now());g.t0=Date.now();g.thumb='';K.imggen.run(view);return}
  if(k==='save'){
   K.toast(T('جارٍ تجهيز الصورة…','Preparing the image…'));
   const od=outDims(g,g.res),cv=document.createElement('canvas');await draw(cv,od[0],od[1],g,await loadImg(g.base));
   const b64=cv.toDataURL('image/png').split(',')[1];
   const r=await K.native.saveFile('bawsala-'+od[0]+'x'+od[1]+'-'+Date.now().toString(36)+'.png','image/png',b64);
   K.toast(r&&r.indexOf('err')===0?T('تعذّر الحفظ','Could not save'):T('تم الحفظ في التنزيلات','Saved to Downloads'));
  }
 },

 /* ---------- composer chips + windows ---------- */
 chips(){
  const sz=D.size;
  return '<button class="mchip" data-img="mode">'+K.icon('image',16)+'<span>'+K.t('create_img')+'</span><span class="xx">'+K.icon('x',14,2.6)+'</span></button>'
   +'<button class="mchip ghost" data-img="size">'+K.icon('image',16)+'<span>'+(sz?esc(sz.plat?sz.plat+' · ':'')+sz.w+'×'+sz.h:T('حدد القياسات','Select sizes'))+'</span></button>'
   +'<button class="mchip ghost" data-img="res"><span>'+T('الدقة','Accuracy')+' - '+resLabel(D.res)+'</span>'+K.icon('chevron-down',15)+'</button>';
 },
 onChip(kind,el){
  if(kind==='mode'){st().imgMode=false;K.home.paintPills();$('ta').placeholder=K.t('hint')}
  else if(kind==='size')K.imggen.sizes();
  else if(kind==='res')K.imggen.resMenu(el);
 },
 enter(){st().imgMode=true;$('ta').placeholder=T('صف الصورة التي تريدها…','Describe the image you want…');K.home.paintPills();setTimeout(()=>{if(!D.size)K.imggen.sizes()},250)},
 resMenu(el){
  document.querySelectorAll('.resmenu').forEach(x=>x.remove());
  const m=document.createElement('div');m.className='resmenu';
  m.innerHTML=['1080','2k','4k'].map(r=>'<button data-r="'+r+'" class="'+(D.res===r?'on':'')+'"><span>'+T('الدقة','Accuracy')+' - '+resLabel(r)+'</span>'+(D.res===r?K.icon('check',18,2.8):'')+'</button>').join('');
  document.body.appendChild(m);const b=el.getBoundingClientRect();
  m.style.bottom=(innerHeight-b.top+8)+'px';m.style[document.documentElement.dir==='rtl'?'right':'left']=Math.max(10,document.documentElement.dir==='rtl'?innerWidth-b.right:b.left)+'px';
  const off=()=>{m.classList.add('out');setTimeout(()=>m.remove(),160);document.removeEventListener('pointerdown',away,true)};
  const away=e=>{if(!m.contains(e.target))off()};setTimeout(()=>document.addEventListener('pointerdown',away,true),0);
  m.querySelectorAll('button').forEach(b=>b.onclick=()=>{st().res=b.dataset.r;K.native.haptic();off();K.home.paintPills()});
 },
 sizes(){
  const old=$('sizes');if(old)old.remove();
  const d=document.createElement('div');d.id='sizes';d.className='sz';d.dir=K.lang()==='ar'?'rtl':'ltr';
  const cur=D.size;
  const ratio=(w,h)=>{const r=w/h,L=[[1,'1:1'],[.8,'4:5'],[1.25,'5:4'],[4/3,'4:3'],[.75,'3:4'],[1.5,'3:2'],[2/3,'2:3'],[16/9,'16:9'],[9/16,'9:16'],[2.33,'21:9'],[1.91,'1.91:1'],[2.6,'2.6:1'],[3.8,'3.8:1'],[2.5,'2.5:1'],[2.4,'12:5'],[.46,'9:19.5'],[2.39,'2.39:1']];let b=null,e=9;L.forEach(x=>{const d=Math.abs(Math.log(r/x[0]));if(d<e){e=d;b=x}});return e<.03?b[1]:(r>=1?r.toFixed(2)+':1':'1:'+(1/r).toFixed(2))};
  const fit=(w,h,box)=>{const k=box/Math.max(w,h);return [Math.max(10,Math.round(w*k)),Math.max(10,Math.round(h*k))]};
  let tab=cur&&G.findIndex(g=>g.n===cur.plat)>=0?G.findIndex(g=>g.n===cur.plat):0;
  let sel=cur?{w:cur.w,h:cur.h,plat:cur.plat||''}:null;
  const paint=()=>{
   const g=G[tab];
   if(!sel){const f=g.it[0];sel={w:f[2],h:f[3],plat:g.n}}
   const pv=fit(sel.w,sel.h,150);
   d.querySelector('#szPv').innerHTML='<div class="frame" style="width:'+pv[0]+'px;height:'+pv[1]+'px"><span>'+sel.w+'×'+sel.h+'</span><small>'+ratio(sel.w,sel.h)+'</small></div>';
   d.querySelector('#szCap').innerHTML='<b>'+esc(sel.plat||T('مخصص','Custom'))+'</b><span>'+sel.w+' × '+sel.h+' px</span>';
   d.querySelector('#szOkb span').textContent=T('تحديد هذا القياس','Use this size');
   d.querySelectorAll('.sz-tab').forEach((b,i)=>b.classList.toggle('on',i===tab));
   const box=d.querySelector('#szList');
   if(tab===G.length){
    box.innerHTML='<div class="sz-cust"><div class="rw"><input id="szW" type="number" inputmode="numeric" placeholder="'+T('العرض','Width')+'" value="'+(sel.w||'')+'"><span>×</span><input id="szH" type="number" inputmode="numeric" placeholder="'+T('الارتفاع','Height')+'" value="'+(sel.h||'')+'"></div><p>'+T('أدخل قياساً بين 64 و 8000 بكسل','Enter a size between 64 and 8000 px')+'</p></div>';
    const upd=()=>{const w=Math.round(+d.querySelector('#szW').value),h=Math.round(+d.querySelector('#szH').value);if(w>=64&&h>=64&&w<=8000&&h<=8000){sel={w,h,plat:T('مخصص','Custom')};const pv=fit(w,h,150);d.querySelector('#szPv').innerHTML='<div class="frame" style="width:'+pv[0]+'px;height:'+pv[1]+'px"><span>'+w+'×'+h+'</span><small>'+ratio(w,h)+'</small></div>';d.querySelector('#szCap').innerHTML='<b>'+T('مخصص','Custom')+'</b><span>'+w+' × '+h+' px</span>'}};
    box.querySelectorAll('input').forEach(i=>i.oninput=upd);
    return;
   }
   box.innerHTML=g.it.map((it,i)=>{const f=fit(it[2],it[3],46);const on=sel.w===it[2]&&sel.h===it[3]&&sel.plat===g.n;
    return '<button class="sz-it'+(on?' on':'')+'" data-i="'+i+'" style="--i:'+i+'"><span class="rt"><i style="width:'+f[0]+'px;height:'+f[1]+'px"></i></span><div><b>'+esc(T(it[0],it[1]))+'</b><small>'+it[2]+' × '+it[3]+' · '+ratio(it[2],it[3])+'</small></div></button>'}).join('');
   box.querySelectorAll('.sz-it').forEach(b=>b.onclick=()=>{const it=g.it[+b.dataset.i];sel={w:it[2],h:it[3],plat:g.n};K.native.haptic();paint()});
  };
  d.innerHTML='<div class="sz-top"><button class="sz-back" id="szBack">'+K.icon('chevron-right',24)+'</button><div class="sz-title">'+T('حدد القياسات','Select sizes')+'</div></div>'
   +'<div class="sz-stage"><div class="sz-pv" id="szPv"></div><div class="sz-cap" id="szCap"></div></div>'
   +'<div class="sz-tabs" id="szTabs">'+G.map((g,i)=>'<button class="sz-tab" data-t="'+i+'">'+(g.id?K.socialTile(g.id,24):'<span class="stile" style="background:'+g.ic+';width:24px;height:24px">'+K.icon('image',14)+'</span>')+'<span>'+esc(g.n)+'</span></button>').join('')
    +'<button class="sz-tab" data-t="'+G.length+'"><span class="stile" style="background:#4d6bfe;width:24px;height:24px">'+K.icon('pencil',13)+'</span><span>'+T('مخصص','Custom')+'</span></button></div>'
   +'<div class="sz-list" id="szList"></div>'
   +'<div class="sz-foot"><button class="sz-ok" id="szOkb"><span></span></button></div>';
  document.body.appendChild(d);
  d.querySelectorAll('.sz-tab').forEach(b=>b.onclick=()=>{tab=+b.dataset.t;if(tab<G.length&&!(sel&&sel.plat===G[tab].n)){const f=G[tab].it[0];sel={w:f[2],h:f[3],plat:G[tab].n}}paint();b.scrollIntoView({inline:'center',block:'nearest',behavior:'smooth'})});
  d.querySelector('#szBack').onclick=()=>{d.classList.add('out');setTimeout(()=>d.remove(),240)};
  d.querySelector('#szOkb').onclick=()=>{if(!sel||!(sel.w>=64&&sel.h>=64&&sel.w<=8000&&sel.h<=8000)){K.toast(T('أدخل قياساً بين 64 و 8000','Enter a size between 64 and 8000'));return}
   st().img={w:sel.w,h:sel.h,plat:sel.plat};d.classList.add('out');setTimeout(()=>d.remove(),240);K.native.haptic();K.home.paintPills()};
  paint();
 },
 closeSizes(){const d=$('sizes');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true}
};
})();
