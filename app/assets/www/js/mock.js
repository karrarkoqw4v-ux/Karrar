/* Browser-only mock of the native bridge (used for development and for taking screenshots). */
window.K=window.K||{};
K.mock={
 chat(msgs,think,ctx,cb){let stop=false;const ar=K.lang()==='ar';
  const q=(msgs[msgs.length-1]||{}).content||'';
  const reasoning=ar?'المستخدم يسأل عن: «'+q.slice(0,60)+'». سأحلّل السؤال أولاً، ثم أرتّب الإجابة بنقاط واضحة ومختصرة.':'The user asks: "'+q.slice(0,60)+'". I will analyse it first, then structure a clear short answer.';
  const answer=ar?
  '## ابدأ بالأساسيات 🚀\nإليك خطة مختصرة لتعلّم البرمجة [1][2]:\n\n1. اختر لغة واحدة مثل **Python**.\n2. تدرّب يومياً على مشاريع صغيرة.\n3. اقرأ كود غيرك وتعلّم منه 😊\n\n> نصيحة: الاستمرار أهم من السرعة.\n\n```python\ndef hello(name):\n    return f"مرحباً {name}"\n\nprint(hello("بوصلة"))\n```\n\n| المرحلة | المدة |\n|---|---|\n| الأساسيات | شهر |\n| مشروع أول | شهران |\n\n> 💡 نصيحة: ابدأ بمشروع صغير تحبه، فهو يبقيك متحمساً.\n\n> ⚠️ لا تنتقل بين اللغات كثيراً في البداية.\n\n> ✅ بعد شهر ستكتب برامج صغيرة بنفسك.\n\n**الخلاصة:** لغة واحدة + تدريب يومي + مشروع صغير.\n':
  '## Start with the basics 🚀\nHere is a short plan [1][2]:\n\n1. Pick one language such as **Python**.\n2. Practise daily with small projects.\n3. Read other people\'s code 😊\n\n```python\ndef hello(name):\n    return f"Hello {name}"\n```\n';
  const parts=[];for(let i=0;i<reasoning.length;i+=4)parts.push(['think',reasoning.slice(i,i+4)]);
  for(let i=0;i<answer.length;i+=3)parts.push(['text',answer.slice(i,i+3)]);
  let k=0;(function step(){if(stop)return;if(k>=parts.length){cb({t:'done'});return}const p=parts[k++];cb({t:p[0],d:p[1]});setTimeout(step,k<20?60:14)})();
  return {cancel(){stop=true}}},
 search(q){return new Promise(r=>setTimeout(()=>r([
  {title:'كيف تبدأ تعلّم البرمجة من الصفر؟ دليل شامل للمبتدئين',url:'https://academy.hsoub.com/programming/general/learn-programming',snippet:'خطوات عملية لتعلّم البرمجة: اختيار اللغة المناسبة، المصادر المجانية، وبناء مشاريع صغيرة للتدرّب.'},
  {title:'Learn to Code — Free Interactive Courses | freeCodeCamp',url:'https://www.freecodecamp.org/learn',snippet:'Learn to code for free with thousands of interactive lessons, projects and certifications.'},
  {title:'برمجة | منصة إدراك — دورات مجانية باللغة العربية',url:'https://www.edraak.org/programming',snippet:'دورات برمجة مجانية مقدّمة من نخبة من المدرّسين مع شهادات إتمام معتمدة.'},
  {title:'Python for Beginners | Python.org',url:'https://www.python.org/about/gettingstarted/',snippet:'New to programming? Python is free and easy to learn if you know where to start.'},
  {title:'أساسيات البرمجة — موقع W3Schools بالعربية',url:'https://www.w3schools.com/python/',snippet:'دروس مختصرة وأمثلة تفاعلية تساعدك على فهم أساسيات بايثون بسرعة.'}]),900))},
 voiceLive(h){let on=true,t=0;const iv=setInterval(()=>{if(!on)return;t++;h({t:'rms',v:2+Math.random()*7});if(t===8)h({t:'partial',d:'مرحبا'});if(t===14){h({t:'final',d:'مرحبا كيف الحال؟'});on=false;clearInterval(iv)}},180);return{stop(){on=false;clearInterval(iv)}}},
 has(e){return !!JSON.parse(localStorage.getItem('mock_acc')||'{}')[e.toLowerCase()]},
 reset(e,p,p2){if(p.length<6)return{err:'e_pass_short'};if(p!==p2)return{err:'e_pass_match'};const a=JSON.parse(localStorage.getItem('mock_acc')||'{}');if(!a[e.toLowerCase()])return{err:'e_nouser'};a[e.toLowerCase()].p=p;localStorage.setItem('mock_acc',JSON.stringify(a));return{ok:1}},
 login(e,p){const a=JSON.parse(localStorage.getItem('mock_acc')||'{}');if(!/^\S+@\S+\.\S+$/.test(e))return{err:'e_email'};if(!p)return{err:'e_empty'};const u=a[e.toLowerCase()];if(!u)return{err:'e_nouser'};if(u.p!==p)return{err:'e_wrong'};
  K.native.setPref('logged','1');K.native.setPref('name',u.n);K.native.setPref('user_email',e.toLowerCase());return{ok:1}},
 signup(n,e,p,p2){if(n.trim().length<2)return{err:'e_name'};if(!/^\S+@\S+\.\S+$/.test(e))return{err:'e_email'};if(p.length<6)return{err:'e_pass_short'};if(p!==p2)return{err:'e_pass_match'};
  const a=JSON.parse(localStorage.getItem('mock_acc')||'{}');if(a[e.toLowerCase()])return{err:'e_exists'};a[e.toLowerCase()]={n:n,p:p};localStorage.setItem('mock_acc',JSON.stringify(a));
  K.native.setPref('logged','1');K.native.setPref('name',n);K.native.setPref('user_email',e.toLowerCase());return{ok:1}},
 _img(i){const c=document.createElement('canvas');c.width=260;c.height=300;const x=c.getContext('2d');const g=x.createLinearGradient(0,0,260,300);
  const pals=[['#1d2b64','#f8cdda'],['#134e5e','#71b280'],['#41295a','#2f0743'],['#c33764','#1d2671'],['#0f2027','#2c5364'],['#e65c00','#f9d423']];const p=pals[i%pals.length];g.addColorStop(0,p[0]);g.addColorStop(1,p[1]);x.fillStyle=g;x.fillRect(0,0,260,300);
  x.fillStyle='rgba(255,255,255,.18)';x.beginPath();x.arc(60+i*20,90,50,0,7);x.fill();x.beginPath();x.moveTo(0,300);x.lineTo(120,170);x.lineTo(260,300);x.fill();return c.toDataURL('image/jpeg',.8)},
 images(n){const a=[];for(let i=0;i<n;i++)a.push({th:K.mock._img(i+3),b64:''});return a},
 gallery(){const a=[];for(let i=0;i<8;i++)a.push({uri:'mock://'+i,th:K.mock._img(i)});return{perm:true,items:a}}
};
