/* Contact + Terms page: developer info, official social links, terms of use with animated emoji */
window.K=window.K||{};
(function(){
const P={"tiktok": "M12.525.02c1.31-.02 2.61-.01 3.91-.02.08 1.53.63 3.09 1.75 4.17 1.12 1.11 2.7 1.62 4.24 1.79v4.03c-1.44-.05-2.89-.35-4.2-.97-.57-.26-1.1-.59-1.62-.93-.01 2.92.01 5.84-.02 8.75-.08 1.4-.54 2.79-1.35 3.94-1.31 1.92-3.58 3.17-5.91 3.21-1.43.08-2.86-.31-4.08-1.03-2.02-1.19-3.44-3.37-3.65-5.71-.02-.5-.03-1-.01-1.49.18-1.9 1.12-3.72 2.58-4.96 1.66-1.44 3.98-2.13 6.15-1.72.02 1.48-.04 2.96-.04 4.44-.99-.32-2.15-.23-3.02.37-.63.41-1.11 1.04-1.36 1.75-.21.51-.15 1.07-.14 1.61.24 1.64 1.82 3.02 3.5 2.87 1.12-.01 2.19-.66 2.77-1.61.19-.33.4-.67.41-1.06.1-1.79.06-3.57.07-5.36.01-4.03-.01-8.05.02-12.07z", "instagram": "M7.0301.084c-1.2768.0602-2.1487.264-2.911.5634-.7888.3075-1.4575.72-2.1228 1.3877-.6652.6677-1.075 1.3368-1.3802 2.127-.2954.7638-.4956 1.6365-.552 2.914-.0564 1.2775-.0689 1.6882-.0626 4.947.0062 3.2586.0206 3.6671.0825 4.9473.061 1.2765.264 2.1482.5635 2.9107.308.7889.72 1.4573 1.388 2.1228.6679.6655 1.3365 1.0743 2.1285 1.38.7632.295 1.6361.4961 2.9134.552 1.2773.056 1.6884.069 4.9462.0627 3.2578-.0062 3.668-.0207 4.9478-.0814 1.28-.0607 2.147-.2652 2.9098-.5633.7889-.3086 1.4578-.72 2.1228-1.3881.665-.6682 1.0745-1.3378 1.3795-2.1284.2957-.7632.4966-1.636.552-2.9124.056-1.2809.0692-1.6898.063-4.948-.0063-3.2583-.021-3.6668-.0817-4.9465-.0607-1.2797-.264-2.1487-.5633-2.9117-.3084-.7889-.72-1.4568-1.3876-2.1228C21.2982 1.33 20.628.9208 19.8378.6165 19.074.321 18.2017.1197 16.9244.0645 15.6471.0093 15.236-.005 11.977.0014 8.718.0076 8.31.0215 7.0301.0839m.1402 21.6932c-1.17-.0509-1.8053-.2453-2.2287-.408-.5606-.216-.96-.4771-1.3819-.895-.422-.4178-.6811-.8186-.9-1.378-.1644-.4234-.3624-1.058-.4171-2.228-.0595-1.2645-.072-1.6442-.079-4.848-.007-3.2037.0053-3.583.0607-4.848.05-1.169.2456-1.805.408-2.2282.216-.5613.4762-.96.895-1.3816.4188-.4217.8184-.6814 1.3783-.9003.423-.1651 1.0575-.3614 2.227-.4171 1.2655-.06 1.6447-.072 4.848-.079 3.2033-.007 3.5835.005 4.8495.0608 1.169.0508 1.8053.2445 2.228.408.5608.216.96.4754 1.3816.895.4217.4194.6816.8176.9005 1.3787.1653.4217.3617 1.056.4169 2.2263.0602 1.2655.0739 1.645.0796 4.848.0058 3.203-.0055 3.5834-.061 4.848-.051 1.17-.245 1.8055-.408 2.2294-.216.5604-.4763.96-.8954 1.3814-.419.4215-.8181.6811-1.3783.9-.4224.1649-1.0577.3617-2.2262.4174-1.2656.0595-1.6448.072-4.8493.079-3.2045.007-3.5825-.006-4.848-.0608M16.953 5.5864A1.44 1.44 0 1 0 18.39 4.144a1.44 1.44 0 0 0-1.437 1.4424M5.8385 12.012c.0067 3.4032 2.7706 6.1557 6.173 6.1493 3.4026-.0065 6.157-2.7701 6.1506-6.1733-.0065-3.4032-2.771-6.1565-6.174-6.1498-3.403.0067-6.156 2.771-6.1496 6.1738M8 12.0077a4 4 0 1 1 4.008 3.9921A3.9996 3.9996 0 0 1 8 12.0077", "x": "M14.234 10.162 22.977 0h-2.072l-7.591 8.824L7.251 0H.258l9.168 13.343L.258 24H2.33l8.016-9.318L16.749 24h6.993zm-2.837 3.299-.929-1.329L3.076 1.56h3.182l5.965 8.532.929 1.329 7.754 11.09h-3.182z"};
const T=(a,e)=>K.lang()==='ar'?a:e;
const EM=c=>'<img class="lgl-em" src="emoji/'+c+'.svg" alt="">';
const DEV={name:'كروري',nameEn:'Karrouri',mail:'karrarkooqwtoi@gmail.com'};
const WEB='http://bawsala.unaux.com/';
const SOC=[
 {id:'tiktok',n:'TikTok',h:'vwv2e',u:'https://www.tiktok.com/@vwv2e'},
 {id:'instagram',n:'Instagram',h:'10s.i2',u:'https://www.instagram.com/10s.i2'},
 {id:'x',n:'X (Twitter)',h:'Compassv4',u:'https://x.com/Compassv4'}];
function ico(id){
 let bg,svg;
 if(id==='tiktok'){bg='#000';svg='<path transform="translate(-.45,.45)" fill="#25f4ee" d="'+P.tiktok+'"/><path transform="translate(.45,-.45)" fill="#fe2c55" d="'+P.tiktok+'"/><path fill="#fff" d="'+P.tiktok+'"/>'}
 else if(id==='instagram'){bg='radial-gradient(circle at 30% 107%,#fdf497 0%,#fdf497 5%,#fd5949 45%,#d6249f 60%,#285AEB 90%)';svg='<path fill="#fff" d="'+P.instagram+'"/>'}
 else{bg='#000';svg='<path fill="#fff" d="'+P.x+'"/>'}
 return '<span class="lgl-ico" style="background:'+bg+'"><svg viewBox="0 0 24 24" width="15" height="15">'+svg+'</svg></span>';
}
function sections(){return [
 ['1f91d','accept',T('قبول الشروط','Acceptance of terms'),T('باستخدامك تطبيق «بوصلة» فأنت توافق على هذه الشروط والأحكام كاملة. إذا لم توافق على أي بند فيرجى التوقف عن استخدام التطبيق.','By using Bawsala you agree to these terms in full. If you do not agree with any part, please stop using the app.')],
 ['2728','use',T('الاستخدام المسموح','Permitted use'),T('التطبيق مخصّص للمحادثة مع الذكاء الاصطناعي والتعلّم والكتابة والبرمجة والإنتاجية، وللاستخدام الشخصي المشروع فقط.','The app is for chatting with AI, learning, writing, coding and productivity, and for lawful personal use only.')],
 ['1f6ab','ban',T('الاستخدامات المحظورة','Prohibited uses'),[
  T('أي نشاط غير قانوني أو يخالف قوانين بلدك أو القوانين الدولية.','Any illegal activity or use that breaks your local or international law.'),
  T('إنتاج أو طلب محتوى غير أخلاقي: استغلال الأطفال، المحتوى الجنسي الفاضح، التحريض على العنف أو الكراهية أو الإرهاب.','Creating or requesting unethical content: child exploitation, explicit sexual content, incitement to violence, hate or terrorism.'),
  T('الاحتيال والنصب وانتحال الشخصيات والتضليل وصناعة الأخبار الزائفة.','Fraud, scams, impersonation, deception and fake news.'),
  T('الاختراق وكتابة برمجيات خبيثة أو أدوات تجسس أو سرقة بيانات الآخرين.','Hacking, writing malware or spyware, or stealing other people\'s data.'),
  T('انتهاك حقوق الملكية الفكرية أو خصوصية الأشخاص ونشر معلوماتهم الخاصة.','Infringing intellectual property or people\'s privacy, or publishing their private information.'),
  T('إيذاء النفس أو الآخرين أو المساعدة على ذلك.','Harming yourself or others, or helping with it.')],'warn'],
 ['1f513','jail',T('كسر القيود والتحايل','Bypassing restrictions'),[
  T('يُمنع محاولة كسر قيود الذكاء الاصطناعي أو ضوابط الأمان (Jailbreak) أو التحايل عليها بأي صيغة أو حيلة.','Trying to jailbreak the AI or bypass its safety rules by any prompt or trick is forbidden.'),
  T('يُمنع فك التطبيق أو هندسته العكسية أو تعديله أو إعادة توزيعه أو إزالة اسمه وحقوقه.','Do not decompile, reverse-engineer, modify, redistribute the app or remove its name and credits.'),
  T('يُمنع تجاوز حدود الاستخدام أو التحقق أو القفل المدمج في التطبيق.','Do not bypass usage limits, verification or the lock built into the app.')],'warn'],
 ['1f4bb','dev',T('سلامة جهازك والخدمة','Device & service safety'),[
  T('لا تستخدم التطبيق بطريقة تُتلف جهازك أو أجهزة الآخرين أو تُعطّلها أو تستنزف مواردها.','Do not use the app in a way that damages, disables or drains your device or anyone else\'s.'),
  T('يُمنع إرسال طلبات آلية مفرطة أو هجمات إغراق أو استغلال ثغرات في التطبيق أو في مزوّد الخدمة.','No excessive automated requests, flooding attacks or exploiting weaknesses in the app or the provider.'),
  T('لا تثبّت نسخًا معدّلة أو غير رسمية من التطبيق؛ قد تُعرّض جهازك وبياناتك للخطر.','Do not install modified or unofficial copies of the app; they may put your device and data at risk.')],'danger'],
 ['1f9e0','ai',T('مخرجات الذكاء الاصطناعي','AI output'),T('قد تكون الإجابات غير دقيقة أو ناقصة. لا تعتمد عليها وحدها في القرارات الطبية أو القانونية أو المالية أو المصيرية، وراجع مختصًا عند الحاجة. أنت المسؤول عن كيفية استخدامك للمخرجات.','Answers may be inaccurate or incomplete. Do not rely on them alone for medical, legal, financial or critical decisions; consult a professional when needed. You are responsible for how you use the output.')],
 ['1f512','priv',T('الخصوصية والبيانات','Privacy & data'),[
  T('محادثاتك وإعداداتك ومفاتيح الـAPI تُحفظ محليًا على جهازك.','Your chats, settings and API keys are stored locally on your device.'),
  T('عند الإرسال تُرسل رسائلك إلى مزوّد الذكاء الاصطناعي الذي تختاره، وتخضع لسياسته. لا تُدخل كلمات مرور أو بيانات بنكية أو أسرارًا.','When you send a message it goes to the AI provider you choose and is subject to its policy. Never enter passwords, banking data or secrets.'),
  T('عند استخدام البحث في الويب تُرسل عبارة البحث إلى محرك البحث.','With web search, your query is sent to the search engine.'),
  T('عند اختيار حساب Google يُقرأ بريدك واسمك فقط لعرضهما في التطبيق.','When you pick a Google account, only your email and name are read to display them in the app.')]],
 ['1f510','acc',T('الحساب والأمان','Account & security'),T('أنت مسؤول عن سرّية حسابك وكلمة المرور وقفل التطبيق، وعن كل نشاط يتم من خلالها.','You are responsible for keeping your account, password and app lock private, and for all activity under them.')],
 ['1f6d1','stop',T('الإيقاف والتقييد','Suspension'),T('يحق للمطوّر إيقاف أو تقييد استخدام أي شخص يخالف هذه الشروط، أو تعديل التطبيق أو إيقافه في أي وقت.','The developer may suspend or restrict anyone who breaks these terms, and may change or discontinue the app at any time.')],
 ['2696','law',T('إخلاء المسؤولية','Disclaimer'),T('يُقدَّم التطبيق «كما هو» دون ضمان من أي نوع. لا يتحمّل المطوّر مسؤولية أي ضرر ينتج عن سوء الاستخدام أو عن الاعتماد على مخرجات الذكاء الاصطناعي.','The app is provided "as is" without warranty of any kind. The developer is not liable for damage from misuse or from relying on AI output.')],
 ['1f4dd','upd',T('تعديل الشروط','Changes'),T('قد تُحدَّث هذه الشروط من وقت لآخر، واستمرارك في الاستخدام يعني موافقتك على النسخة الجديدة.','These terms may be updated from time to time; continuing to use the app means you accept the new version.')]
]}
K.legal={
 isOpen(){return !!document.getElementById('legal')},
 close(){const d=document.getElementById('legal');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),260);return true},
 open(where){
  const old=document.getElementById('legal');if(old)old.remove();
  const d=document.createElement('div');d.id='legal';d.className='lgl';d.dir=K.lang()==='ar'?'rtl':'ltr';
  let h='<div class="lgl-top"><button class="lgl-back" id="lglBack">'+K.icon('chevron-right',24)+'</button><div class="lgl-title">'+T('اتصل بنا والشروط','Contact & Terms')+'</div></div><div class="lgl-body" id="lglBody">';
  h+='<div class="lgl-hero"><div class="lgl-mk">'+K.LOGO+'</div><div class="lgl-nm">'+T('بوصلة','Bawsala')+'</div>'
   +'<span class="fl f1">'+EM('1f4dc')+'</span><span class="fl f2">'+EM('2696')+'</span><span class="fl f3">'+EM('1f6e1')+'</span><span class="fl f4">'+EM('2728')+'</span><span class="fl f5">'+EM('1f9ed')+'</span></div>';
  h+='<div class="lgl-sec" id="lglContact"><div class="lgl-h">'+EM('1f4e7')+'<span>'+T('اتصل بنا','Contact us')+'</span></div><div class="lgl-card">'
   +'<div class="lgl-row"><span class="lgl-ico dev">'+EM('1f464')+'</span><div class="lgl-tx"><small>'+T('المطوّر','Developer')+'</small><b>'+T(DEV.name,DEV.nameEn+' ('+DEV.name+')')+'</b></div></div>'
   +'<button class="lgl-row" id="lglMail"><span class="lgl-ico mail">'+EM('1f4e7')+'</span><div class="lgl-tx"><small>'+T('البريد الإلكتروني','Email')+'</small><b dir="ltr">'+DEV.mail+'</b></div></button>'
   +'<button class="lgl-row" data-u="'+WEB+'"><span class="lgl-ico">'+EM('1f310')+'</span><div class="lgl-tx"><small>'+T('الموقع الرسمي','Official website')+'</small><b dir="ltr">bawsala.unaux.com</b></div></button>'
   +'</div><div class="lgl-sub">'+T('حسابات المطوّر','Developer accounts')+'</div><div class="lgl-card">'
   +SOC.map(s=>'<button class="lgl-row" data-u="'+s.u+'">'+ico(s.id)+'<div class="lgl-tx"><small>'+s.n+'</small><b dir="ltr">@'+s.h+'</b></div></button>').join('')+'</div></div>';
  h+='<div class="lgl-sec" id="lglTerms"><div class="lgl-h">'+EM('1f4dc')+'<span>'+T('شروط وأحكام الاستخدام','Terms of use')+'</span></div>'
   +'<div class="lgl-alert">'+EM('26a0')+'<div>'+T('يرجى قراءة هذه الشروط بعناية. مخالفتها قد تؤدي إلى إيقاف استخدامك للتطبيق.','Please read these terms carefully. Breaking them may lead to your use of the app being suspended.')+'</div></div>';
  sections().forEach((s,i)=>{
   const body=Array.isArray(s[3])?'<ul>'+s[3].map(x=>'<li>'+x+'</li>').join('')+'</ul>':'<p>'+s[3]+'</p>';
   h+='<div class="lgl-t '+(s[4]||'')+'" id="lgl-'+s[1]+'" style="--i:'+i+'"><div class="lgl-th"><span class="lgl-n">'+(i+1)+'</span><span class="lgl-te">'+EM(s[0])+'</span><b>'+s[2]+'</b></div>'+body+'</div>';
  });
  h+='<div class="lgl-end">'+T('آخر تحديث: 2026 · للأسئلة راسلنا على ','Last updated: 2026 · Questions? Write to ')+'<b dir="ltr">'+DEV.mail+'</b></div></div></div>';
  d.innerHTML=h;document.body.appendChild(d);
  d.querySelector('#lglBack').onclick=()=>K.legal.close();
  d.querySelector('#lglMail').onclick=()=>{K.native.copy(DEV.mail);K.native.open('mailto:'+DEV.mail)};
  d.querySelectorAll('[data-u]').forEach(b=>b.onclick=()=>K.native.open(b.dataset.u));
  const sc=where==='terms'?d.querySelector('#lglTerms'):where==='privacy'?d.querySelector('#lgl-priv'):null;
  if(sc)setTimeout(()=>{d.querySelector('#lglBody').scrollTo({top:sc.offsetTop-70,behavior:'smooth'})},450);
 }
};
})();
