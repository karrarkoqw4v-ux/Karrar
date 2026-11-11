/* Browser-only mock of the native bridge (used for development and for taking screenshots). */
window.K=window.K||{};
K.mock={
 chat(msgs,think,ctx,cb){let stop=false;const ar=K.lang()==='ar';
  const q=(msgs[msgs.length-1]||{}).content||'';
  const reasoning=ar?'المستخدم يسأل عن: «'+q.slice(0,60)+'». سأحلّل السؤال أولاً، ثم أرتّب الإجابة بنقاط واضحة ومختصرة.':'The user asks: "'+q.slice(0,60)+'". I will analyse it first, then structure a clear short answer.';
  const answer=ar?
  '## ابدأ بالأساسيات 🚀\nإليك خطة مختصرة لتعلّم البرمجة [1][2]:\n\n1. اختر لغة واحدة مثل **Python**.\n2. تدرّب يومياً على مشاريع صغيرة.\n3. اقرأ كود غيرك وتعلّم منه 😊\n\n> نصيحة: الاستمرار أهم من السرعة.\n\n```python\ndef hello(name):\n    return f"مرحباً {name}"\n\nprint(hello("بوصلة"))\n```\n\n| المرحلة | المدة |\n|---|---|\n| الأساسيات | شهر |\n| مشروع أول | شهران |\n\n> 💡 نصيحة: ابدأ بمشروع صغير تحبه، فهو يبقيك متحمساً.\n\n> ⚠️ لا تنتقل بين اللغات كثيراً في البداية.\n\n> ✅ بعد شهر ستكتب برامج صغيرة بنفسك.\n\n**الخلاصة:** لغة واحدة + تدريب يومي + مشروع صغير.\n':
  '## Start with the basics 🚀\nHere is a short plan [1][2]:\n\n1. Pick one language such as **Python**.\n2. Practise daily with small projects.\n3. Read other people\'s code 😊\n\n```python\ndef hello(name):\n    return f"Hello {name}"\n```\n';
  const fileAns='سأرسل لك الملف الآن 👇\n\n```file:todo-app.zip\n=== index.html ===\n<!DOCTYPE html>\n<html><body><h1>مهامي</h1></body></html>\n=== app.js ===\nconsole.log("hi")\n```\n\nوهذا مثال كود:\n\n```javascript\n// جمع رقمين\nfunction add(a, b) {\n  const total = a + b; // 42\n  return `Sum: ${total}`;\n}\nclass User extends Base { constructor(){ super(); this.name = \"Ali\"; } }\n```\n\n```python\ndef hello(name: str) -> str:\n    # greet\n    return f\"Hello {name}\" if name else None\n```\n\n```json\n{\"name\": \"app\", \"v\": 2, \"ok\": true}\n```\n\nاستخدم `npm start` للتشغيل.';
  const appAns='هذا تطبيق جاهز — اضغط «عرض» لتجربته على جهاز كامل:\n\n```app:مهامي\n'+`<!DOCTYPE html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>مهامي</title>
<style>
:root{--bg:#0d1020;--card:rgba(255,255,255,.07);--line:rgba(255,255,255,.12);--ac:#6f8cff;--ac2:#b06cff;--tx:#f3f5ff;--mut:#9aa3c7}
*{box-sizing:border-box;margin:0;-webkit-tap-highlight-color:transparent}
body{min-height:100vh;font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;color:var(--tx);background:radial-gradient(900px 500px at 80% -10%,#2a2f6b,transparent),radial-gradient(700px 400px at -10% 110%,#4a2a6b,transparent),var(--bg);padding:22px 18px 40px}
h1{font-size:1.7rem;font-weight:800;margin:10px 0 2px}p.s{color:var(--mut);margin-bottom:18px}
.ring{display:flex;align-items:center;gap:16px;background:var(--card);border:1px solid var(--line);border-radius:22px;padding:16px;backdrop-filter:blur(14px);margin-bottom:16px}
.ring svg{width:74px;height:74px;transform:rotate(-90deg)}.ring circle{fill:none;stroke-width:8;stroke-linecap:round}.ring .bg{stroke:var(--line)}.ring .fg{stroke:url(#g);transition:stroke-dashoffset .6s cubic-bezier(.2,.9,.3,1)}
.ring b{font-size:1.3rem}.ring small{display:block;color:var(--mut)}
form{display:flex;gap:8px;margin-bottom:14px}input{flex:1;height:50px;border-radius:16px;border:1px solid var(--line);background:var(--card);color:var(--tx);padding:0 16px;font-size:1rem;outline:none}input:focus{border-color:var(--ac)}
button.add{width:50px;border:0;border-radius:16px;background:linear-gradient(135deg,var(--ac),var(--ac2));color:#fff;font-size:1.6rem;box-shadow:0 8px 20px rgba(111,140,255,.4)}
ul{list-style:none;display:flex;flex-direction:column;gap:10px}li{display:flex;align-items:center;gap:12px;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:14px;animation:in .35s cubic-bezier(.2,.9,.3,1) both}
@keyframes in{from{opacity:0;transform:translateY(10px) scale(.97)}}
.ck{width:26px;height:26px;border-radius:50%;border:2px solid var(--mut);display:grid;place-items:center;flex:none;background:none;color:#fff;transition:.25s}li.done .ck{background:linear-gradient(135deg,var(--ac),var(--ac2));border-color:transparent}
li span{flex:1}li.done span{text-decoration:line-through;color:var(--mut)}.del{background:none;border:0;color:var(--mut);font-size:1.2rem}
</style></head><body>
<h1>مهامي ✨</h1><p class="s">نظّم يومك بخطوات صغيرة</p>
<div class="ring"><svg viewBox="0 0 100 100"><defs><linearGradient id="g"><stop offset="0" stop-color="#6f8cff"/><stop offset="1" stop-color="#b06cff"/></linearGradient></defs><circle class="bg" cx="50" cy="50" r="42"/><circle class="fg" id="fg" cx="50" cy="50" r="42" stroke-dasharray="264" stroke-dashoffset="264"/></svg><div><b id="pc">0%</b><small id="ct">لا توجد مهام</small></div></div>
<form id="f"><input id="i" placeholder="أضف مهمة جديدة…" autocomplete="off"><button class="add">+</button></form>
<ul id="l"></ul>
<script>
let T=[{t:"تصميم الواجهة",d:true},{t:"كتابة الكود",d:false},{t:"اختبار التطبيق",d:false}];
const l=document.getElementById('l');
function R(){l.innerHTML='';T.forEach((x,k)=>{const e=document.createElement('li');if(x.d)e.className='done';e.innerHTML='<button class="ck">'+(x.d?'✓':'')+'</button><span></span><button class="del">×</button>';e.querySelector('span').textContent=x.t;e.querySelector('.ck').onclick=()=>{x.d=!x.d;R()};e.querySelector('.del').onclick=()=>{T.splice(k,1);R()};l.appendChild(e)});
const n=T.filter(x=>x.d).length,p=T.length?Math.round(n/T.length*100):0;document.getElementById('fg').style.strokeDashoffset=264-264*p/100;document.getElementById('pc').textContent=p+'%';document.getElementById('ct').textContent=T.length?n+' من '+T.length+' منجزة':'لا توجد مهام'}
document.getElementById('f').onsubmit=e=>{e.preventDefault();const v=document.getElementById('i').value.trim();if(v){T.unshift({t:v,d:false});document.getElementById('i').value='';R()}};R();
</script></body></html>
`+'\n```\n\n- قائمة مهام تفاعلية\n- دائرة تقدم متحركة\n- تصميم زجاجي داكن';
  const ans=/تطبيق|app|لعبة|game/i.test(q)?appAns:/ملف|file/i.test(q)?fileAns:answer;
  const parts=[];for(let i=0;i<reasoning.length;i+=4)parts.push(['think',reasoning.slice(i,i+4)]);
  for(let i=0;i<ans.length;i+=3)parts.push(['text',ans.slice(i,i+3)]);
  let k=0;(function step(){if(stop)return;if(k>=parts.length){cb({t:'done'});return}const p=parts[k++];cb({t:p[0],d:p[1]});setTimeout(step,k<20?60:14)})();
  return {cancel(){stop=true}}},
 search(q){return new Promise(r=>setTimeout(()=>r([
  {title:'كيف تبدأ تعلّم البرمجة من الصفر؟ دليل شامل للمبتدئين',url:'https://academy.hsoub.com/programming/general/learn-programming',snippet:'خطوات عملية لتعلّم البرمجة: اختيار اللغة المناسبة، المصادر المجانية، وبناء مشاريع صغيرة للتدرّب.'},
  {title:'Learn to Code — Free Interactive Courses | freeCodeCamp',url:'https://www.freecodecamp.org/learn',snippet:'Learn to code for free with thousands of interactive lessons, projects and certifications.'},
  {title:'برمجة | منصة إدراك — دورات مجانية باللغة العربية',url:'https://www.edraak.org/programming',snippet:'دورات برمجة مجانية مقدّمة من نخبة من المدرّسين مع شهادات إتمام معتمدة.'},
  {title:'Python for Beginners | Python.org',url:'https://www.python.org/about/gettingstarted/',snippet:'New to programming? Python is free and easy to learn if you know where to start.'},
  {title:'أساسيات البرمجة — موقع W3Schools بالعربية',url:'https://www.w3schools.com/python/',snippet:'دروس مختصرة وأمثلة تفاعلية تساعدك على فهم أساسيات بايثون بسرعة.'}]),900))},
 http(m,u,b){if(/device\/code/.test(u))return {code:200,body:JSON.stringify({device_code:'dc',user_code:'WDJB-MJHT',verification_uri:'https://github.com/login/device',expires_in:900,interval:5})};return {code:200,body:JSON.stringify({error:'authorization_pending'})}},
 googleAccounts(){return [{name:'Sara Demo',email:'sara.demo@gmail.com'},{name:'Omar Test',email:'omar.test88@gmail.com'},{name:'Lina Sample',email:'lina.sample@gmail.com'}]},
 scanUrl(url,deep){const bad=/paypa1|login|verify|free|gift|\.xyz|\.tk/i.test(url);return new Promise(r=>setTimeout(()=>r(bad?{ok:true,chain:[{url:url,status:302},{url:'http://185.12.44.9/p/login.php',status:200}],status:200,final:'http://185.12.44.9/p/login.php',ms:1430,ips:['185.12.44.9'],headers:{'content-type':'text/html'},page:{title:'PayPal - Log in',forms:1,password:1,iframes:1,hiddenIframes:1,eval:6,atob:2,metaRefresh:false,mixed:0,extScripts:['cdn.badhost.top','track.evil.xyz'],formHosts:['collect.evil-data.xyz']}}:{ok:true,chain:[{url:url,status:200}],status:200,final:url,ms:420,ips:['93.184.216.34'],tls:{subject:'CN=example.com',issuer:"C=US,O=DigiCert Inc,CN=DigiCert TLS RSA",from:Date.now()-90*864e5,to:Date.now()+200*864e5,self:false,cipher:'TLS_AES_256_GCM_SHA384'},headers:{server:'nginx','strict-transport-security':'max-age=31536000','x-content-type-options':'nosniff','content-type':'text/html','set-cookie':'sid=1; Secure; HttpOnly'},page:{title:'Example Domain',forms:0,password:0,iframes:0,eval:0,atob:0,metaRefresh:false,mixed:0,extScripts:['www.googletagmanager.com'],formHosts:[]}}),1200))},
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
