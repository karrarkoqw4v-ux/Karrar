/* Model picker (display names only; only the default one is active) + generated-images gallery */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const LIST=()=>[
 ['v4','Compass - v4',T('الافتراضي — للمهام اليومية','Default — for everyday tasks'),0],
 ['plus','Compass - Plus',T('إجابات أذكى للأعمال المعقّدة','Smarter answers for complex work'),1],
 ['go','Compass - Go',T('سريع وخفيف للمهام البسيطة','Fast and light for simple tasks'),1],
 ['pro','Compass - Pro',T('للمشاريع المتطلّبة','For demanding projects'),1],
 ['max','Compass - Max',T('لأصعب التحديات','For your toughest challenges'),1]];
K.models={
 cur(){return 'v4'},
 name(){return 'Compass - v4'},
 paint(){const b=$('bModel');if(b)b.innerHTML='<span>'+K.models.name()+'</span>'+K.icon('chevron-down',15)},
 open(){
  const old=$('msheet');if(old)old.remove();
  const w=document.createElement('div');w.id='msheet';w.className='ms';
  w.innerHTML='<div class="ms-bd"></div><div class="ms-sh"><i class="ms-grab"></i><div class="ms-top"><b>'+T('اختيار النموذج','Select model')+'</b><button class="ms-x">'+K.icon('x',24,2.2)+'</button></div>'
   +LIST().map((m,i)=>'<button class="ms-it'+(m[0]===K.models.cur()?' on':'')+'" data-i="'+i+'" style="--i:'+i+'"><span class="mck">'+(m[0]===K.models.cur()?K.icon('check',17,3.2):'')+'</span><div><div class="nm"><b>'+m[1]+'</b>'+(m[3]?'<i class="pill">'+T('قريباً','Soon')+'</i>':'')+'</div><small>'+m[2]+'</small></div></button>').join('')+'</div>';
  document.body.appendChild(w);
  const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),240)};K.models.close=close;
  w.querySelector('.ms-bd').onclick=close;w.querySelector('.ms-x').onclick=close;
  w.querySelectorAll('.ms-it').forEach(b=>b.onclick=()=>{const m=LIST()[+b.dataset.i];if(m[3]){K.native.haptic();K.toast(T('هذا النموذج قريباً','This model is coming soon'));return}K.models.paint();close()});
 },
 closeIfOpen(){const w=$('msheet');if(!w)return false;w.classList.add('out');setTimeout(()=>w.remove(),240);return true}
};
K.gallery={
 open(){
  const old=$('gallery');if(old)old.remove();
  const items=[];K.db.convs.forEach(c=>c.msgs.forEach(m=>{if(m.gen&&m.gen.thumb)items.push({t:m.gen.thumb,c:c,p:m.gen.prompt})}));
  items.reverse();
  const d=document.createElement('div');d.id='gallery';d.className='gal-pg';d.dir=K.lang()==='ar'?'rtl':'ltr';
  d.innerHTML='<div class="gp-top"><button class="gp-back">'+K.icon('chevron-right',24)+'</button><b>'+T('الصور','Images')+'</b></div><div class="gp-body">'
   +(items.length?'<div class="gp-grid">'+items.map((x,i)=>'<button class="gp-it" data-i="'+i+'"><img src="'+x.t+'" alt=""></button>').join('')+'</div>'
     :'<div class="gp-empty"><img src="emoji/1f3a8.svg" alt=""><b>'+T('لا توجد صور بعد','No images yet')+'</b><span>'+T('اضغط «+» ثم «إنشاء صورة» لتبدأ','Tap “+” then “Create image” to start')+'</span></div>')+'</div>';
  document.body.appendChild(d);
  const close=()=>{d.classList.add('out');setTimeout(()=>d.remove(),240)};K.gallery.close=close;
  d.querySelector('.gp-back').onclick=close;
  d.querySelectorAll('.gp-it').forEach(b=>b.onclick=()=>K.ui.lightbox(items[+b.dataset.i].t));
 },
 closeIfOpen(){const d=$('gallery');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true}
};
})();
