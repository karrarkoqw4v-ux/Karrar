/* Design brief: before a build/design request runs, the user picks options (type, style, colours, language, sections, device) so the AI never designs blindly */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const Q=()=>[
 {k:'type',t:T('نوع المشروع','Project type'),one:1,o:[['تطبيق جوال','Mobile app'],['موقع ويب','Website'],['لوحة تحكم','Dashboard'],['متجر إلكتروني','Online store'],['لعبة','Game']]},
 {k:'style',t:T('الأسلوب','Style'),one:1,o:[['احترافي داكن','Pro dark'],['فاتح نظيف','Clean light'],['زجاجي متدرج','Glass gradient'],['دافئ مثل Claude','Warm, Claude-like'],['نيون للألعاب','Neon gaming']]},
 {k:'color',t:T('اللون الأساسي','Main colour'),one:1,o:[['أزرق','Blue'],['بنفسجي','Purple'],['أخضر','Green'],['برتقالي','Orange'],['أحمر','Red'],['وردي','Pink']]},
 {k:'lang',t:T('لغة الواجهة','UI language'),one:1,o:[['عربي (RTL)','Arabic (RTL)'],['English','English'],['ثنائي عربي + English','Arabic + English']]},
 {k:'parts',t:T('الأقسام (اختر عدة)','Sections (pick several)'),one:0,o:[['تسجيل الدخول','Login'],['الصفحة الرئيسية','Home'],['الملف الشخصي','Profile'],['الدفع','Payments'],['الإشعارات','Notifications'],['الإعدادات','Settings'],['لوحة إدارة','Admin panel'],['دردشة','Chat']]},
 {k:'device',t:T('الجهاز','Device'),one:1,o:[['هاتف','Phone'],['لوحي','Tablet'],['هاتف + لوحي + حاسوب','All devices']]}];
K.brief={
 need(text,conv){return !!(K.intent&&K.intent.build(text)||/(تصميم|صمم لي|صمملي|design (a|an|me))/i.test(text||''))&&!(conv&&conv.briefed)&&!/(بدون اسئلة|بدون أسئلة|no questions|skip)/i.test(text||'')&&K.pref.bool('brief',true)},
 ask(text){return new Promise(res=>{
  const ar=K.lang()==='ar',ans={},qs=Q();
  const w=document.createElement('div');w.className='psheet brief';w.dir=ar?'rtl':'ltr';
  w.innerHTML='<div class="pbd"></div><div class="psh bsh"><i class="grab"></i><b class="bt">'+T('حدّد تصميمك قبل البدء','Define your design first')+'</b><small class="bs">'+T('اختر ما يناسبك ليصمم الذكاء الاصطناعي بدقة. يمكنك التخطي.','Pick what fits so the AI designs precisely. You can skip.')+'</small><div class="bq">'
   +qs.map((q,qi)=>'<div class="qb"><span>'+q.t+'</span><div class="chips">'+q.o.map((o,oi)=>'<button class="bc" data-q="'+qi+'" data-o="'+oi+'">'+esc(ar?o[0]:o[1])+'</button>').join('')+'</div></div>').join('')
   +'<div class="qb"><span>'+T('تفاصيل إضافية (اختياري)','Extra details (optional)')+'</span><textarea id="bExtra" rows="2" placeholder="'+T('مثال: خدمات صيانة منزلية في بغداد','e.g. home maintenance services in Baghdad')+'"></textarea></div></div>'
   +'<button class="pbtn" id="bGo">'+T('ابدأ التصميم','Start designing')+'</button><button class="pcan" id="bSkip">'+T('تخطي','Skip')+'</button></div>';
  document.body.appendChild(w);
  const done=v=>{w.classList.add('out');setTimeout(()=>w.remove(),220);res(v)};
  w.querySelectorAll('.bc').forEach(b=>b.onclick=()=>{const q=qs[+b.dataset.q],o=+b.dataset.o;K.native.haptic();
   if(q.one){w.querySelectorAll('.bc[data-q="'+b.dataset.q+'"]').forEach(x=>x.classList.remove('on'));b.classList.add('on');ans[q.k]=[o]}
   else{b.classList.toggle('on');const a=ans[q.k]||[];const i=a.indexOf(o);if(i>=0)a.splice(i,1);else a.push(o);ans[q.k]=a}});
  w.querySelector('.pbd').onclick=()=>done(null);$('bSkip').onclick=()=>done(null);
  $('bGo').onclick=()=>{const lines=[];qs.forEach(q=>{const a=ans[q.k]||[];if(a.length)lines.push(q.t.replace(/ \(.*\)/,'')+': '+a.map(i=>q.o[i][ar?0:1]).join(', '))});
   const ex=$('bExtra').value.trim();if(ex)lines.push(T('تفاصيل','Details')+': '+ex);
   done(lines.length?'DESIGN BRIEF (chosen by the user, follow exactly):\n'+lines.join('\n'):'')}
 })}
};
})();
