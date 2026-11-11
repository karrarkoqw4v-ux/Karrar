/* Subscription & Upgrade page (display only: plans are not on sale yet) */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const STAR='<svg viewBox="0 0 64 64" width="96" height="96"><defs><linearGradient id="pg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7ea6ff"/><stop offset="1" stop-color="#3a63f0"/></linearGradient></defs><path fill="url(#pg)" d="M38 12c1.6 11.6 8.4 18.4 20 20-11.6 1.6-18.4 8.4-20 20-1.6-11.6-8.4-18.4-20-20 11.6-1.6 18.4-8.4 20-20Z"/><path fill="url(#pg)" d="M14 4c.7 5.2 3.8 8.3 9 9-5.2.7-8.3 3.8-9 9-.7-5.2-3.8-8.3-9-9 5.2-.7 8.3-3.8 9-9Z"/></svg>';
const ROWS=()=>[[T('النموذج الأساسي','Core model'),1,1],[T('رسائل أكثر مع الأدوات','More messages with tools'),0,1],[T('مساحة أكبر لتخزين الملفات','More file storage'),0,1],[T('إنشاء المزيد من الصور','More image creation'),0,1],[T('المزيد من الدردشات الصوتية','More voice chats'),0,1],[T('ذاكرة أطول','Longer memory'),0,1]];
const CK='<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
const DASH='<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M7 12h10"/></svg>';
K.plans={
 isOpen(){return !!$('plans')},
 close(){const d=$('plans');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true},
 open(){
  if($('plans'))$('plans').remove();
  const d=document.createElement('div');d.id='plans';d.className='pl';d.dir=K.lang()==='ar'?'rtl':'ltr';
  d.innerHTML='<button class="pl-x" id="plX">'+K.icon('x',26,2.2)+'</button><div class="pl-body"><div class="pl-star">'+STAR+'</div>'
   +'<h1>'+T('احصل على ','Get ')+'<span dir="ltr">Bawsala Go</span></h1><p class="pl-s">'+T('مساحة أكبر للكتابة والتعلّم وإنشاء المحتوى','More room to write, learn and create')+'</p>'
   +'<div class="pl-card"><div class="pl-r head"><b>'+T('الميزات','Features')+'</b><b>'+T('مجاني','Free')+'</b><b class="go">Go</b></div>'
   +ROWS().map(r=>'<div class="pl-r"><span>'+r[0]+'</span><i class="'+(r[1]?'pk free':'no')+'">'+(r[1]?CK:DASH)+'</i><i class="pk go">'+CK+'</i></div>').join('')
   +'<button class="pl-cmp" id="plCmp">'+T('قارن بين الخطط','Compare plans')+'</button></div></div>'
   +'<div class="pl-foot"><button class="pl-go" id="plGo"><span dir="ltr">'+T('الترقية إلى Go','Upgrade to Go')+'</span></button><p>'+T('يتجدد تلقائيًا كل شهر. يمكنك الإلغاء في أي وقت.','Renews automatically every month. Cancel anytime.')+'</p><button class="pl-rs" id="plRs">'+T('استعادة الاشتراك','Restore subscription')+'</button></div>';
  document.body.appendChild(d);
  $('plX').onclick=()=>K.plans.close();
  $('plGo').onclick=()=>{K.native.haptic();K.toast(T('الاشتراكات غير متاحة بعد — قريباً','Subscriptions are not available yet — coming soon'))};
  $('plRs').onclick=()=>K.toast(T('لا يوجد اشتراك لاستعادته','No subscription to restore'));
  $('plCmp').onclick=()=>K.plans.compare()},
 compare(){
  const L=[['Compass - v4',T('الافتراضي — متاح الآن','Default — available now'),1],['Compass - Plus',T('إجابات أذكى للأعمال المعقّدة','Smarter answers for complex work'),0],['Compass - Go',T('سريع وخفيف للمهام البسيطة','Fast and light for simple tasks'),0],['Compass - Pro',T('للمشاريع المتطلّبة','For demanding projects'),0],['Compass - Max',T('لأصعب التحديات','For your toughest challenges'),0]];
  const w=document.createElement('div');w.className='pl-cmpw';w.innerHTML='<div class="bd"></div><div class="sh"><i class="gr"></i><h3>'+T('مقارنة الخطط','Compare plans')+'</h3>'+L.map(x=>'<div class="it"><div><b dir="ltr">'+x[0]+'</b><small>'+x[1]+'</small></div><i class="'+(x[2]?'on':'soon')+'">'+(x[2]?T('متاح','Active'):T('قريباً','Soon'))+'</i></div>').join('')+'<button class="cl">'+T('إغلاق','Close')+'</button></div>';
  $('plans').appendChild(w);const c=()=>{w.classList.add('out');setTimeout(()=>w.remove(),220)};w.querySelector('.bd').onclick=c;w.querySelector('.cl').onclick=c}
};
})();
