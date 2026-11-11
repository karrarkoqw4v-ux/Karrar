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
  const old=$('msheet');if(old){old.remove();return}
  const ar=K.lang()==='ar',pm=K.pref.get('pref_model',''),pp=K.pref.get('pref_provider','');
  const OPT=[['',''  ,T('تلقائي','Automatic'),T('أفضل نموذج متاح تلقائياً','Best available model automatically')],
   ['groq','llama-3.3-70b-versatile','Llama 3.3 70B',T('متوازن وسريع','Balanced and fast')],
   ['groq','openai/gpt-oss-120b','GPT-OSS 120B',T('استدلال متقدّم','Advanced reasoning')],
   ['groq','qwen/qwen3-32b','Qwen3 32B',T('برمجة والمهام المعقّدة','Coding and complex tasks')],
   ['openrouter','qwen/qwen3-coder:free','Qwen3 Coder',T('الأقوى للبرمجة (مفتوح المصدر)','Strongest open coder')],
   ['openrouter','deepseek/deepseek-r1:free','DeepSeek R1',T('تفكير عميق','Deep thinking')]];
  const w=document.createElement('div');w.id='msheet';w.className='mdrop';w.dir=ar?'rtl':'ltr';
  const tl=K.pref.get('think_level','auto');
  w.innerHTML='<div class="md-bd"></div><div class="md-box">'+OPT.map((o,i)=>'<button class="md-it" data-i="'+i+'"><span class="mt"><b>'+o[2]+'</b><small>'+o[3]+'</small></span><span class="mk">'+((o[1]===pm&&o[0]===pp)||(!o[0]&&!pm)?K.icon('check',22,2.4):'')+'</span></button>').join('')
   +'<button class="md-it md-more" data-more="1"><span class="mt"><b>'+T('المزيد من النماذج…','More models…')+'</b></span></button><div class="md-sep"></div>'
   +'<button class="md-it md-tl"><span class="mt"><b>'+T('مستوى التفكير','Thinking level')+'</b></span><span class="mk chv">'+K.icon('chevron-down',20)+'</span></button>'
   +'<div class="md-lv hidden">'+[['auto',T('تلقائي (عند الحاجة)','Auto (when needed)')],['always',T('تفكير عميق دائماً','Always think deeply')]].map(x=>'<button class="md-it md-l" data-l="'+x[0]+'"><span class="mt"><b>'+x[1]+'</b></span><span class="mk">'+(tl===x[0]?K.icon('check',20,2.4):'')+'</span></button>').join('')+'</div></div>';
  document.body.appendChild(w);
  const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),200)};K.models.close=close;
  w.querySelector('.md-bd').onclick=close;
  w.querySelectorAll('.md-it[data-i]').forEach(b=>b.onclick=()=>{const o=OPT[+b.dataset.i];K.pref.set('pref_provider',o[0]);K.pref.set('pref_model',o[1]);K.native.haptic();
   if(o[0]&&!K.pref.get('fk_'+o[0],'').trim())K.toast(T('هذا النموذج يحتاج مفتاح '+o[0]+' مجاني (الإعدادات ← النماذج)','This model needs a free '+o[0]+' key (Settings → Models)'));close()});
  w.querySelector('[data-more]').onclick=()=>{close();K.catalog.open()};
  w.querySelector('.md-tl').onclick=()=>{w.querySelector('.md-lv').classList.toggle('hidden');w.querySelector('.chv').classList.toggle('up')};
  w.querySelectorAll('.md-l').forEach(b=>b.onclick=()=>{K.pref.set('think_level',b.dataset.l);close()});
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
