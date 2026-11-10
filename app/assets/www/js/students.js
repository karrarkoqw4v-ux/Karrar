/* Students: Iraqi stages -> grades, 110 study tools by subject, private chats per grade/subject, camera question scanner */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const STAGES=[['pri','المرحلة الابتدائية','#10b981',[['p1','الأول الابتدائي'],['p2','الثاني الابتدائي'],['p3','الثالث الابتدائي'],['p4','الرابع الابتدائي'],['p5','الخامس الابتدائي'],['p6','السادس الابتدائي']]],
 ['mid','المرحلة المتوسطة','#f59e0b',[['m1','الأول المتوسط'],['m2','الثاني المتوسط'],['m3','الثالث المتوسط']]],
 ['prep','المرحلة الإعدادية','#8b5cf6',[['s4','الرابع العلمي'],['s5','الخامس العلمي'],['s6','السادس العلمي']]]];
/* subject -> [name, icon, colour, tools[[name,hint]]] : 11 subjects x 10 tools = 110 */
const SUB={
 math:['الرياضيات','plus','#4d6bfe',[['حل مسألة خطوة بخطوة','حلّ المسألة بخطوات مرتبة مع ذكر القانون المستخدم والجواب النهائي'],['شرح درس','اشرح الدرس بأسلوب مبسّط مع مثال محلول'],['حل معادلة','حلّ المعادلة وتحقق من الجواب بالتعويض'],['مسائل كلامية','حوّل المسألة الكلامية إلى معادلة ثم حلّها'],['هندسة وأشكال','حلّ مسألة الهندسة مع وصف الشكل والقوانين'],['نسبة وتناسب','حلّ مسائل النسبة والتناسب والنسبة المئوية'],['كسور وأعداد عشرية','حلّ مسائل الكسور والأعداد العشرية بخطوات واضحة'],['اختبار قصير','ضع 5 أسئلة متدرجة الصعوبة مع الإجابات'],['القوانين والملخص','لخّص قوانين الدرس في جدول سريع'],['تصحيح حلّي','صحّح حلّي التالي وبيّن أين الخطأ']]],
 physics:['الفيزياء','atom','#0ea5e9',[['حل مسألة فيزياء','حلّ المسألة بذكر المعطيات والقانون والتعويض والوحدات'],['شرح قانون','اشرح القانون ومعنى كل رمز وتطبيقاته'],['تحويل الوحدات','حوّل الوحدات خطوة بخطوة'],['تحليل رسم بياني','فسّر الرسم البياني أو الدائرة الكهربائية'],['تجربة مختبر','اشرح خطوات التجربة والنتائج المتوقعة'],['أسئلة وزارية','أجب عن سؤال وزاري بنمط الإجابة النموذجية'],['مراجعة سريعة','لخّص الفصل في نقاط للمراجعة'],['اختبار قصير','ضع 5 أسئلة مع الحلول'],['تفسير ظاهرة','فسّر الظاهرة الفيزيائية بمثال من الحياة'],['تصحيح حلّي','صحّح حلّي وبيّن الأخطاء']]],
 chemistry:['الكيمياء','sparkles','#ec4899',[['موازنة معادلة','وازن المعادلة الكيميائية خطوة بخطوة'],['مسائل المول','حلّ مسائل المول والكتلة والتراكيز'],['شرح درس','اشرح درس الكيمياء بأسلوب مبسّط'],['الجدول الدوري','اشرح خصائص العنصر وموقعه في الجدول'],['أنواع التفاعلات','حدّد نوع التفاعل واكتب نواتجه'],['المحاليل والتراكيز','حلّ مسائل المحاليل والتخفيف'],['تجربة مختبر','اشرح التجربة واحتياطات الأمان'],['اختبار قصير','ضع 5 أسئلة مع الإجابات'],['مراجعة سريعة','لخّص الفصل في نقاط'],['تصحيح حلّي','صحّح حلّي وبيّن الخطأ']]],
 biology:['الأحياء','sparkles','#22c55e',[['شرح درس','اشرح الدرس بأسلوب مبسّط مع تشبيهات'],['رسم وتسمية','صف الرسم واكتب أجزاءه ووظيفة كل جزء'],['مقارنة جدولية','قارن بين المفهومين في جدول'],['أسئلة تدريب','ضع أسئلة تدريب مع الإجابة'],['وراثة','حلّ مسألة وراثية بمربع بانيت'],['الجسم البشري','اشرح الجهاز مع وظائفه'],['خلية ووظائفها','اشرح أجزاء الخلية ووظائفها'],['اختبار قصير','ضع 5 أسئلة متدرجة'],['مراجعة سريعة','لخّص الفصل في نقاط'],['تصحيح إجابتي','صحّح إجابتي واشرح الخطأ']]],
 arabic:['اللغة العربية','pencil','#f97316',[['إعراب جملة','أعرب الجملة كلمة بكلمة'],['شرح نص','اشرح النص وأفكاره ومعاني المفردات'],['قواعد النحو','اشرح القاعدة النحوية مع أمثلة'],['قراءة وتشكيل','شكّل النص تشكيلاً تاماً'],['إملاء','صحّح الإملاء واشرح القاعدة'],['تعبير وإنشاء','ساعدني في كتابة موضوع تعبير'],['أدب وشعر','اشرح البيت أو القصيدة وصورها الفنية'],['بلاغة','حلّل الصور البلاغية في العبارة'],['اختبار قصير','ضع 5 أسئلة مع الإجابات'],['تصحيح كتابتي','صحّح كتابتي لغوياً وأسلوبياً']]],
 english:['اللغة الإنجليزية','languages','#06b6d4',[['ترجمة','ترجم النص مع شرح الكلمات الصعبة'],['شرح قاعدة','اشرح القاعدة بالعربي مع أمثلة'],['محادثة تدريبية','تحدّث معي بالإنجليزية بمستوى مبسّط وصحّح أخطائي'],['مفردات','اشرح المفردات مع جمل مثال'],['كتابة فقرة','ساعدني أكتب فقرة بالإنجليزية'],['تصحيح قواعد','صحّح جملي وبيّن الأخطاء'],['نطق وتهجئة','اشرح نطق الكلمات وتهجئتها'],['قراءة وفهم','اشرح القطعة وأجب عن أسئلتها'],['اختبار قصير','ضع 5 أسئلة مع الإجابات'],['مراجعة سريعة','لخّص الوحدة في نقاط']]],
 islamic:['التربية الإسلامية','file-text','#14b8a6',[['شرح درس','اشرح الدرس بأسلوب مبسّط'],['آيات ومعانٍ','اشرح معاني الآيات بتبسيط'],['أحاديث شريفة','اشرح الحديث ومعاني ألفاظه وفوائده'],['العبادات','اشرح الحكم بأسلوب بسيط'],['السيرة النبوية','لخّص الحدث من السيرة وعبره'],['أسئلة واختبار','ضع أسئلة مع الإجابات'],['حفظ ومراجعة','ساعدني في حفظ النص بطريقة سهلة'],['معاني المفردات','اشرح معاني الكلمات'],['قصص وعبر','اروِ القصة المناسبة للدرس'],['تصحيح إجابتي','صحّح إجابتي']]],
 science:['العلوم','sparkles','#84cc16',[['شرح درس','اشرح درس العلوم بتبسيط وأمثلة'],['تجربة بسيطة','اقترح تجربة آمنة تُجرى في البيت'],['سؤال وجواب','أجب عن السؤال بكلمات سهلة'],['رسم وتسمية','صف الرسم واكتب الأسماء'],['مقارنة','قارن في جدول'],['ظواهر الطبيعة','فسّر الظاهرة الطبيعية للأطفال'],['الحيوانات والنباتات','اشرح عن الحيوان أو النبات'],['اختبار قصير','ضع 5 أسئلة مع الإجابات'],['مراجعة سريعة','لخّص الدرس في نقاط'],['لغز علمي','اطرح لغزاً علمياً وأجبني']]],
 social:['الاجتماعيات','globe','#a855f7',[['شرح درس','اشرح درس التاريخ أو الجغرافية'],['خرائط','صف الخريطة والمواقع المهمة'],['خط زمني','اصنع خطاً زمنياً للأحداث'],['مقارنة','قارن بين الحدثين أو المنطقتين'],['أسئلة وزارية','أجب بنمط الإجابة النموذجية'],['تلخيص','لخّص الدرس في نقاط'],['شخصيات تاريخية','عرّفني بالشخصية وأثرها'],['اختبار قصير','ضع 5 أسئلة مع الإجابات'],['مواطنة وقيم','اشرح الدرس بأمثلة'],['تصحيح إجابتي','صحّح إجابتي']]],
 computer:['الحاسوب','code-xml','#3b82f6',[['شرح درس','اشرح الدرس بتبسيط'],['مفاهيم البرمجة','اشرح المفهوم بمثال بسيط'],['حل خوارزمية','اكتب خطوات الحل أو المخطط الانسيابي'],['أخطاء الكود','اكتشف الخطأ في الكود واشرحه'],['جداول Excel','اشرح الدالة أو الجدول خطوة بخطوة'],['أمان وإنترنت','اشرح قواعد الأمان على الإنترنت'],['مشروع صغير','اقترح مشروعاً بسيطاً بالخطوات'],['اختبار قصير','ضع 5 أسئلة مع الإجابات'],['مصطلحات','اشرح المصطلحات التقنية'],['مراجعة سريعة','لخّص الدرس في نقاط']]],
 study:['مهارات الدراسة','clock','#ef4444',[['تلخيص درس','لخّص النص الذي سأكتبه في نقاط'],['خطة مذاكرة','ضع لي جدول مذاكرة أسبوعي'],['مساعد الواجب','ساعدني أفهم الواجب دون إعطاء الجواب مباشرة'],['بطاقات مراجعة','حوّل الدرس إلى بطاقات سؤال وجواب'],['قبل الامتحان','أعطني خطة ليلة الامتحان'],['تقوية الذاكرة','اقترح طرقاً لحفظ المعلومات'],['تنظيم الوقت','ساعدني في تنظيم وقتي'],['تحفيز','حفّزني بكلمات قصيرة'],['شرح ببساطة','اشرح لي كأني في عمر 10 سنوات'],['اختبر نفسي','اسألني أسئلة على الدرس']]]};
const BYSTAGE={pri:['arabic','english','math','science','islamic','social','computer','study'],mid:['arabic','english','math','physics','chemistry','biology','islamic','social','computer','study'],prep:['arabic','english','math','physics','chemistry','biology','islamic','computer','study']};
const gradeInfo=id=>{for(const s of STAGES)for(const g of s[3])if(g[0]===id)return {id:g[0],name:g[1],stage:s[0],stageName:s[1]}; return null};
let subj='';
K.students={
 STAGES,SUB,count(){return Object.keys(SUB).reduce((n,k)=>n+SUB[k][3].length,0)},
 grade(){return gradeInfo(K.pref.get('stu_grade',''))},
 persona(c){const g=gradeInfo(c.stu);if(!g)return '';const sn=c.subj&&SUB[c.subj]?SUB[c.subj][0]:'';
  return 'You are a kind, patient teacher for Iraqi students in "'+g.name+'" ('+g.stageName+') following the Iraqi curriculum. '+(sn?'Subject: '+sn+'. ':'')
   +'Reply in clear Arabic suited to this age (light Iraqi warmth is fine). Explain simply, show every step of the working for maths/science, define new terms, and end with one short check question. If a photo of a question is attached, read it carefully first, restate the question, then solve it.'},
 open(){
  const old=$('stu');if(old)old.remove();
  const d=document.createElement('div');d.id='stu';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);K.students.paint()},
 close(){const d=$('stu');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true},
 paint(){
  const d=$('stu');if(!d)return;const g=K.students.grade();
  let body='';
  if(!g){
   body='<div class="sc-hero"><div class="sc-shield"><img class="sc-em big" src="emoji/1f393.svg" alt=""><i class="r r1"></i><i class="r r2"></i></div><b>'+T('اختر مرحلتك الدراسية','Choose your grade')+'</b><span>'+T('نثبّت مرحلتك، وتُحفظ محادثاتك الخاصة بها','We lock your grade and keep your private chats for it')+'</span></div>'
    +STAGES.map(s=>'<div class="sc-sect" style="color:'+s[2]+'">'+s[1]+'</div><div class="st-grid">'+s[3].map(x=>'<button class="st-g" data-g="'+x[0]+'" style="--c:'+s[2]+'">'+x[1]+'</button>').join('')+'</div>').join('');
  }else{
   const keys=BYSTAGE[g.stage];if(!subj||keys.indexOf(subj)<0)subj=keys[0];
   const tools=SUB[subj][3];
   const mine=K.db.convs.filter(c=>c.stu===g.id).sort((a,b)=>b.ts-a.ts);
   body='<div class="st-cur"><span>🎓</span><div><b>'+esc(g.name)+'</b><small>'+esc(g.stageName)+' · '+K.students.count()+' '+T('أداة','tools')+'</small></div><button id="stChg">'+T('تغيير','Change')+'</button></div>'
    +'<div class="st-act"><button id="stChat" style="--c:#4d6bfe">'+K.icon('message-square',22)+'<span>'+T('محادثة مباشرة','Direct chat')+'</span></button><button id="stScan" style="--c:#f59e0b">'+K.icon('camera',22)+'<span>'+T('ماسح الأسئلة','Question scanner')+'</span></button></div>'
    +'<div class="sc-sect">'+T('المواد','Subjects')+'</div><div class="st-subs">'+keys.map(k=>'<button class="st-s'+(k===subj?' on':'')+'" data-k="'+k+'" style="--c:'+SUB[k][2]+'">'+K.icon(SUB[k][1],18)+'<span>'+SUB[k][0]+'</span></button>').join('')+'</div>'
    +'<div class="sc-sect">'+T('أدوات '+SUB[subj][0],SUB[subj][0]+' tools')+' <span class="n">'+tools.length+'</span></div><div class="st-tools">'+tools.map((t,i)=>'<button class="st-t" data-i="'+i+'" style="--i:'+i+';--c:'+SUB[subj][2]+'"><b>'+t[0]+'</b></button>').join('')+'</div>'
    +(mine.length?'<div class="sc-sect">'+T('محادثاتي الخاصة','My private chats')+'</div>'+mine.map((c,i)=>'<div class="sc-h" data-c="'+i+'"><span class="dot" style="background:'+(SUB[c.subj]?SUB[c.subj][2]:'#4d6bfe')+'"></span><button class="op" style="flex:1;text-align:start;display:flex;flex-direction:column"><b>'+esc(c.title)+'</b><small>'+c.msgs.length+' '+T('رسالة','messages')+'</small></button><button class="rm" style="color:var(--text3);padding:6px">'+K.icon('trash-2',18)+'</button></div>').join(''):'');
  }
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="stBack">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('الطلاب','Students')+'</div><span class="sc-ib ph"></span></div><div class="sc-body">'+body+'</div>';
  $('stBack').onclick=()=>K.students.close();
  d.querySelectorAll('.st-g').forEach(b=>b.onclick=()=>{K.pref.set('stu_grade',b.dataset.g);K.native.haptic();subj='';K.students.paint()});
  if(g){
   $('stChg').onclick=()=>{K.pref.set('stu_grade','');K.students.paint()};
   $('stChat').onclick=()=>K.students.enter(g,'');
   $('stScan').onclick=()=>K.students.scan(g);
   d.querySelectorAll('.st-s').forEach(b=>b.onclick=()=>{subj=b.dataset.k;K.students.paint()});
   const tools=SUB[subj][3];
   d.querySelectorAll('.st-t').forEach(b=>b.onclick=async()=>{const t=tools[+b.dataset.i];
    const v=await K.ui.prompt({title:t[0],hint:T('اكتب سؤالك أو المسألة هنا','Type your question here'),multi:true});if(!v)return;
    K.students.enter(g,subj,'الأداة: '+t[0]+' ('+SUB[subj][0]+')\nالتعليمات: '+t[1]+'\nالمدخل:\n'+v)});
   const mine=K.db.convs.filter(c=>c.stu===g.id).sort((a,b)=>b.ts-a.ts);
   d.querySelectorAll('.sc-h[data-c]').forEach(r=>{const c=mine[+r.dataset.c];r.querySelector('.op').onclick=()=>K.students.enter(g,c.subj||'',null,c);r.querySelector('.rm').onclick=async()=>{if(await K.ui.confirm(T('حذف هذه المحادثة؟','Delete this chat?'),true)){K.db.remove(c);K.students.paint()}}})}
 },
 /* open (or create) the private chat for grade+subject, optionally sending a first message */
 enter(g,sk,msg,conv){
  let c=conv||K.db.convs.find(x=>x.stu===g.id&&(x.subj||'')===(sk||''));
  if(!c){c=K.db.create();c.stu=g.id;c.subj=sk||'';c.title='🎓 '+g.name+(sk&&SUB[sk]?' · '+SUB[sk][0]:'');
   c.msgs.push({role:'assistant',c:T('أهلاً بيك! أني مساعدك الدراسي لـ**'+g.name+'**'+(sk&&SUB[sk]?' في مادة **'+SUB[sk][0]+'**':'')+'. اكتب سؤالك، أو افتح **ماسح الأسئلة** وصوّر السؤال، وأحلّه إلك خطوة بخطوة. 📚','Hi! I am your study helper for **'+g.name+'**. Ask a question, or use the **scanner** to photograph one.'),t:'',s:0,src:[],v:0});
   c.ts=Date.now();K.db.convs.unshift(c);K.db.save()}
  const st=$('stu');st&&st.remove();
  if(K.state.streaming)K.chat.stop();K.state.cur=c;K.pref.set('cur',c.id);K.chat.show(c);K.home.closeDrawer();K.home.paintPills();
  if(msg){const ta=$('ta');ta.value=msg;ta.dispatchEvent(new Event('input'));K.chat.send()}},
 async scan(g){
  K.toast(T('وجّه الكاميرا على السؤال','Point the camera at the question'));
  const r=await K.native.camera();if(!r||!r.length)return;
  K.students.enter(g,subj||'');
  r.forEach(i=>K.home.addAtt({kind:'img',th:i.th,b64:i.b64}));
  const ta=$('ta');ta.value='اقرأ السؤال الموجود في الصورة، اكتبه نصاً، ثم حلّه حلاً صحيحاً خطوة بخطوة مع الشرح.';ta.dispatchEvent(new Event('input'));K.chat.send()}
};
document.addEventListener('click',e=>{const b=e.target.closest&&e.target.closest('[data-act=students]');if(b){K.home.closeSheet();K.students.open()}});
})();
