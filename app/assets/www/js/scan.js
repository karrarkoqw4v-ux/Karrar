/* Security & Scan: link scan, file scan, published-website scan. Analysis is local/heuristic (+ a read-only fetch of the site by the app). */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const em=(c,cl)=>'<img class="sc-em '+(cl||'')+'" src="emoji/'+c+'.svg" alt="">';
const W={info:0,low:6,med:18,high:38,crit:65};
const LV={
 safe:{c:'#34c759',e:'2705',a:'آمن',n:'Safe',s:['لم نجد مؤشرات خطر','No risk indicators found']},
 low:{c:'#a3d635',e:'1f642',a:'خطر منخفض',n:'Low risk',s:['ملاحظات بسيطة فقط','Only minor notes']},
 med:{c:'#ffb020',e:'26a0',a:'مشبوه',n:'Suspicious',s:['توجد مؤشرات تستدعي الحذر','There are signs that call for caution']},
 high:{c:'#ff6b35',e:'1f6a8',a:'خطر',n:'Dangerous',s:['لا تفتحه وتجنّب إدخال أي بيانات','Do not open it or enter any data']},
 crit:{c:'#ff3b30',e:'2620',a:'خطر شديد',n:'Critical',s:['ابتعد عنه فوراً','Stay away from it immediately']}};
const levelOf=sc=>sc<8?'safe':sc<24?'low':sc<50?'med':sc<78?'high':'crit';

/* ---------------- helpers ---------------- */
const b64u8=b=>{const s=atob(b),u=new Uint8Array(s.length);for(let i=0;i<s.length;i++)u[i]=s.charCodeAt(i);return u};
function sha256js(u){const K2=[0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
 const H=[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
 const l=u.length,n=((l+9+63)>>6)<<6,m=new Uint8Array(n);m.set(u);m[l]=0x80;const dv=new DataView(m.buffer);dv.setUint32(n-4,(l*8)>>>0);dv.setUint32(n-8,Math.floor(l/0x20000000));
 const w=new Uint32Array(64),r=(x,k)=>(x>>>k)|(x<<(32-k));
 for(let o=0;o<n;o+=64){for(let i=0;i<16;i++)w[i]=dv.getUint32(o+i*4);
  for(let i=16;i<64;i++){const a=w[i-15],b=w[i-2];w[i]=(w[i-16]+(r(a,7)^r(a,18)^(a>>>3))+w[i-7]+(r(b,17)^r(b,19)^(b>>>10)))>>>0}
  let [a,b,c,d,e,f,g,h]=H;
  for(let i=0;i<64;i++){const t1=(h+(r(e,6)^r(e,11)^r(e,25))+((e&f)^(~e&g))+K2[i]+w[i])>>>0,t2=((r(a,2)^r(a,13)^r(a,22))+((a&b)^(a&c)^(b&c)))>>>0;h=g;g=f;f=e;e=(d+t1)>>>0;d=c;c=b;b=a;a=(t1+t2)>>>0}
  H[0]=(H[0]+a)>>>0;H[1]=(H[1]+b)>>>0;H[2]=(H[2]+c)>>>0;H[3]=(H[3]+d)>>>0;H[4]=(H[4]+e)>>>0;H[5]=(H[5]+f)>>>0;H[6]=(H[6]+g)>>>0;H[7]=(H[7]+h)>>>0}
 return H.map(x=>x.toString(16).padStart(8,'0')).join('')}
async function sha256(u){try{if(crypto&&crypto.subtle){const d=await crypto.subtle.digest('SHA-256',u);return [...new Uint8Array(d)].map(x=>x.toString(16).padStart(2,'0')).join('')}}catch(e){}return sha256js(u)}
function entropy(u){const s=u.length>1048576?u.subarray(0,1048576):u,c=new Uint32Array(256);for(let i=0;i<s.length;i++)c[s[i]]++;let e=0;for(let i=0;i<256;i++)if(c[i]){const p=c[i]/s.length;e-=p*Math.log2(p)}return e}
function ascii(u,max){let out=[],cur='';const n=Math.min(u.length,max||4e6);for(let i=0;i<n;i++){const b=u[i];if(b>=32&&b<127)cur+=String.fromCharCode(b);else{if(cur.length>=5)out.push(cur);cur=''}}if(cur.length>=5)out.push(cur);return out.join('\n')}
const fmtSize=n=>n<1024?n+' B':n<1048576?(n/1024).toFixed(1)+' KB':(n/1048576).toFixed(1)+' MB';
function magic(u,name){
 const h=(a)=>a.every((b,i)=>u[i]===b),t=String.fromCharCode.apply(null,u.subarray(0,8));
 if(h([0x4d,0x5a]))return {k:'exe',l:'Windows executable (EXE/DLL)',a:'ملف تنفيذي لويندوز (EXE/DLL)'};
 if(h([0x7f,0x45,0x4c,0x46]))return {k:'elf',l:'Linux/Android native binary (ELF)',a:'ملف تنفيذي لينكس/أندرويد (ELF)'};
 if(h([0xca,0xfe,0xba,0xbe])||h([0xcf,0xfa,0xed,0xfe]))return {k:'macho',l:'macOS/Java binary',a:'ملف ثنائي (macOS أو Java)'};
 if(t.startsWith('dex\n'))return {k:'dex',l:'Android DEX code',a:'كود أندرويد (DEX)'};
 if(h([0x50,0x4b,3,4])||h([0x50,0x4b,5,6])){return {k:'zip',l:'ZIP-based archive',a:'أرشيف مضغوط (ZIP)'}}
 if(t.startsWith('%PDF'))return {k:'pdf',l:'PDF document',a:'مستند PDF'};
 if(h([0xd0,0xcf,0x11,0xe0]))return {k:'ole',l:'Legacy Office file (OLE)',a:'ملف أوفيس قديم (OLE)'};
 if(h([0x89,0x50,0x4e,0x47]))return {k:'png',l:'PNG image',a:'صورة PNG'};
 if(h([0xff,0xd8,0xff]))return {k:'jpg',l:'JPEG image',a:'صورة JPEG'};
 if(t.startsWith('GIF8'))return {k:'gif',l:'GIF image',a:'صورة GIF'};
 if(t.startsWith('RIFF'))return {k:'riff',l:'RIFF media (WAV/WEBP/AVI)',a:'وسائط (WAV/WEBP/AVI)'};
 if(h([0x1f,0x8b]))return {k:'gz',l:'GZIP archive',a:'أرشيف GZIP'};
 if(h([0x52,0x61,0x72,0x21]))return {k:'rar',l:'RAR archive',a:'أرشيف RAR'};
 if(h([0x37,0x7a,0xbc,0xaf]))return {k:'7z',l:'7-Zip archive',a:'أرشيف 7z'};
 if(t.slice(4,8)==='ftyp')return {k:'mp4',l:'MP4/MOV video',a:'فيديو MP4/MOV'};
 if(t.startsWith('ID3')||h([0xff,0xfb]))return {k:'mp3',l:'MP3 audio',a:'صوت MP3'};
 if(t.startsWith('#!'))return {k:'script',l:'Shell script',a:'سكربت شِل'};
 if(t.startsWith('MZ'))return {k:'exe',l:'Windows executable',a:'ملف تنفيذي لويندوز'};
 let txt=true;const n=Math.min(u.length,2000);for(let i=0;i<n;i++){const b=u[i];if(b===0||(b<9)||(b>13&&b<32&&b!==27)){txt=false;break}}
 if(txt)return {k:'text',l:'Text / code',a:'نص أو كود'};
 return {k:'bin',l:'Unknown binary',a:'ملف ثنائي غير معروف'}}
const EXTK={png:'png',jpg:'jpg',jpeg:'jpg',gif:'gif',webp:'riff',bmp:'riff',pdf:'pdf',doc:'ole',xls:'ole',ppt:'ole',docx:'zip',xlsx:'zip',pptx:'zip',docm:'zip',xlsm:'zip',zip:'zip',apk:'zip',jar:'zip',aab:'zip',mp3:'mp3',wav:'riff',mp4:'mp4',mov:'mp4',avi:'riff',txt:'text',csv:'text',json:'text',md:'text',html:'text',htm:'text',css:'text',xml:'text',rar:'rar','7z':'7z',gz:'gz',exe:'exe',dll:'exe',msi:'ole'};
const RISKEXT=new Set('exe scr bat cmd com pif vbs vbe js jse wsf wsh ps1 msi jar apk lnk hta reg dll sh iso cpl gadget inf scf'.split(' '));

/* ---------------- Android manifest (binary XML) ---------------- */
function axml(u){
 const dv=new DataView(u.buffer,u.byteOffset,u.byteLength);let p=8;let strs=[];const out={perms:[],pkg:'',vn:'',vc:'',minSdk:'',target:'',debuggable:false,services:[],receivers:0,activities:0,flags:[]};
 const rd=(i)=>strs[i]!==undefined?strs[i]:'';
 while(p<u.length){const type=dv.getUint16(p,true),hs=dv.getUint16(p+2,true),sz=dv.getUint32(p+4,true);if(sz<8)break;
  if(type===0x0001){const sc=dv.getUint32(p+8,true),fl=dv.getUint32(p+16,true),ss=dv.getUint32(p+20,true),utf8=(fl&256)!==0;
   for(let i=0;i<sc;i++){let o=p+ss+dv.getUint32(p+hs+i*4,true);let s='';
    if(utf8){let l=u[o];o+=(l&128)?2:1;let bl=u[o];if(bl&128){bl=((bl&127)<<8)|u[o+1];o+=2}else o+=1;s=new TextDecoder().decode(u.subarray(o,o+bl))}
    else{let l=dv.getUint16(o,true);o+=2;if(l&0x8000){l=((l&0x7fff)<<16)|dv.getUint16(o,true);o+=2}let a=[];for(let k=0;k<l;k++)a.push(dv.getUint16(o+k*2,true));s=String.fromCharCode.apply(null,a)}
    strs.push(s)}}
  else if(type===0x0102){const nm=rd(dv.getUint32(p+20,true)),ac=dv.getUint16(p+28,true),at=p+36;const A={};
   for(let i=0;i<ac;i++){const o=at+i*20,n=rd(dv.getUint32(o+4,true)),raw=dv.getUint32(o+8,true),dt=u[o+15],d=dv.getUint32(o+16,true);let v;
    if(raw!==0xffffffff)v=rd(raw);else if(dt===3)v=rd(d);else if(dt===0x12)v=d!==0;else v=d;A[n]=v}
   if(nm==='manifest'){out.pkg=A.package||'';out.vn=A.versionName||'';out.vc=A.versionCode||''}
   else if(nm==='uses-permission'||nm==='uses-permission-sdk-23'){if(A.name)out.perms.push(A.name)}
   else if(nm==='uses-sdk'){out.minSdk=A.minSdkVersion||'';out.target=A.targetSdkVersion||''}
   else if(nm==='application'){out.debuggable=A.debuggable===true}
   else if(nm==='service'){out.services.push({n:A.name||'',perm:A.permission||''})}
   else if(nm==='receiver')out.receivers++;else if(nm==='activity')out.activities++}
  p+=sz}
 return out}
const PERM={
 SEND_SMS:['إرسال رسائل SMS','Send SMS','قد يرسل رسائل مدفوعة أو يشترك بخدمات دون علمك','Can send paid texts or subscribe you silently','high'],
 READ_SMS:['قراءة رسائل SMS','Read SMS','قد يقرأ رموز التحقق (OTP) وبنكك','Can read verification codes and bank messages','high'],
 RECEIVE_SMS:['استقبال رسائل SMS','Receive SMS','يعترض الرسائل الواردة عند وصولها','Intercepts incoming texts','high'],
 READ_CONTACTS:['قراءة جهات الاتصال','Read contacts','يصل إلى أسماء وأرقام من تعرفهم','Access to names and numbers of your contacts','med'],
 READ_CALL_LOG:['سجل المكالمات','Call log','يرى من اتصلت بهم ومتى','Sees who you called and when','med'],
 CALL_PHONE:['إجراء مكالمات','Make calls','قد يتصل بأرقام دون تدخل منك','Can place calls without you','med'],
 RECORD_AUDIO:['تسجيل الصوت','Record audio','يستخدم الميكروفون','Uses the microphone','med'],
 CAMERA:['الكاميرا','Camera','يستخدم الكاميرا لالتقاط صور وفيديو','Uses the camera to take photos and video','med'],
 ACCESS_FINE_LOCATION:['الموقع الدقيق','Precise location','يعرف مكانك بدقة','Knows exactly where you are','med'],
 ACCESS_COARSE_LOCATION:['الموقع التقريبي','Approx. location','يعرف منطقتك التقريبية','Knows your approximate area','low'],
 ACCESS_BACKGROUND_LOCATION:['الموقع في الخلفية','Background location','يتتبّعك حتى والتطبيق مغلق','Tracks you even when closed','high'],
 READ_EXTERNAL_STORAGE:['قراءة الملفات','Read files','يقرأ صورك وملفاتك','Reads your photos and files','low'],
 WRITE_EXTERNAL_STORAGE:['كتابة الملفات','Write files','يحفظ ويعدّل ملفات على جهازك','Saves and edits files on your device','low'],
 MANAGE_EXTERNAL_STORAGE:['إدارة كل الملفات','Manage all files','وصول كامل إلى كل ملفات الجهاز','Full access to every file on the device','high'],
 SYSTEM_ALERT_WINDOW:['الظهور فوق التطبيقات','Draw over other apps','قد يخدعك بشاشات مزيفة فوق تطبيقاتك (سرقة بيانات)','Can overlay fake screens to steal data','high'],
 REQUEST_INSTALL_PACKAGES:['تثبيت تطبيقات','Install apps','قد يثبّت برامج أخرى بدون علمك','Can install other apps','high'],
 BIND_ACCESSIBILITY_SERVICE:['خدمة الوصول','Accessibility service','يستطيع رؤية شاشتك والضغط نيابة عنك — يُستغل كثيراً في البرمجيات الخبيثة','Can read your screen and tap for you — common in malware','crit'],
 BIND_DEVICE_ADMIN:['مدير الجهاز','Device admin','قد يمنع حذف التطبيق أو يقفل الجهاز','May block uninstall or lock the device','crit'],
 RECEIVE_BOOT_COMPLETED:['التشغيل عند الإقلاع','Run at boot','يبدأ تلقائياً عند تشغيل الهاتف','Starts automatically at boot','low'],
 INTERNET:['الإنترنت','Internet','يتصل بالإنترنت (شائع في كل التطبيقات)','Connects to the internet (common)','info'],
 ACCESS_NETWORK_STATE:['حالة الشبكة','Network state','يعرف إن كنت متصلاً','Knows if you are online','info'],
 VIBRATE:['الاهتزاز','Vibrate','يهتز الجهاز','Vibrates the device','info'],
 WAKE_LOCK:['إبقاء الجهاز مستيقظاً','Keep awake','يمنع الجهاز من النوم','Keeps the device awake','info'],
 FOREGROUND_SERVICE:['خدمة في المقدمة','Foreground service','يعمل باستمرار بإشعار','Runs continuously with a notification','low'],
 POST_NOTIFICATIONS:['الإشعارات','Notifications','يرسل لك إشعارات','Sends you notifications','info'],
 GET_ACCOUNTS:['الحسابات','Accounts','يقرأ حسابات الجهاز','Reads accounts on the device','med'],
 READ_PHONE_STATE:['حالة الهاتف','Phone state','يقرأ معلومات الهاتف والرقم','Reads phone info and number','med'],
 PACKAGE_USAGE_STATS:['استخدام التطبيقات','App usage','يراقب التطبيقات التي تستخدمها','Watches which apps you use','med'],
 QUERY_ALL_PACKAGES:['قائمة كل التطبيقات','List all apps','يعرف كل التطبيقات المثبتة عندك','Knows every app you have installed','low'],
 READ_MEDIA_IMAGES:['قراءة الصور','Read photos','يصل إلى صورك','Access to your photos','low'],
 BLUETOOTH_CONNECT:['البلوتوث','Bluetooth','يتصل بأجهزة بلوتوث','Connects to Bluetooth devices','info']};

/* ---------------- file analysis ---------------- */
async function analyzeFile(f,prog){
 const R={kind:'file',name:f.name,size:f.size,facts:[],uses:[],risks:[],tips:[],hash:''};
 const add=(sev,t,d)=>R.risks.push({sev,t,d});const use=(ic,t,d,sev)=>R.uses.push({ic,t,d,sev:sev||'info'});
 const name=f.name||'file',low=name.toLowerCase(),ext=(low.match(/\.([a-z0-9]+)$/)||[])[1]||'';
 if(f.tooBig||!f.b64){R.partial=true;add('med',T('الملف كبير','File is large'),T('يتجاوز حدّ الفحص الكامل داخل التطبيق (6 ميغابايت)، فُحص الاسم والنوع فقط.','Larger than the in-app full-scan limit (6 MB); only the name and type were checked.'))}
 let u=f.b64?b64u8(f.b64):new Uint8Array(0);
 prog&&prog(1);
 R.hash=u.length?await sha256(u):'';prog&&prog(2);
 const real=u.length?magic(u,name):{k:'unknown',l:'Unknown',a:'غير معروف'};R.real=real;
 const claimed=EXTK[ext];
 R.facts.push([T('الاسم','Name'),name],[T('الحجم','Size'),fmtSize(f.size||u.length)],[T('الامتداد','Extension'),ext?'.'+ext:T('بلا امتداد','none')],[T('النوع الحقيقي','Real type'),T(real.a,real.l)]);
 if(R.hash)R.facts.push(['SHA-256',R.hash]);
 // name tricks
 if(/[‮‭]/.test(name))add('crit',T('حيلة في اسم الملف','Filename trick'),T('الاسم يحتوي رمز عكس اتجاه خفياً (U+202E) لإخفاء الامتداد الحقيقي.','The name contains a hidden right-to-left override (U+202E) that hides the real extension.'));
 if(/\.(pdf|docx?|xlsx?|pptx?|jpe?g|png|gif|txt|mp3|mp4)\.(exe|scr|bat|cmd|com|js|vbs|apk|jar|msi|lnk)$/i.test(name))add('crit',T('امتداد مزدوج مخادع','Deceptive double extension'),T('يبدو كمستند أو صورة لكنه في الحقيقة برنامج قابل للتشغيل.','It looks like a document or picture but is really an executable.'));
 if(/\s{5,}/.test(name))add('med',T('مسافات طويلة في الاسم','Long gaps in the name'),T('تُستخدم لإخفاء الامتداد الحقيقي.','Used to hide the real extension.'));
 if(RISKEXT.has(ext))add(ext==='apk'||ext==='jar'||ext==='msi'?'low':'med',T('نوع ملف قابل للتشغيل','Runnable file type'),T('الامتداد .'+ext+' ينفّذ كوداً على جهازك عند فتحه، ولا يجب فتحه إلا من مصدر موثوق.','.'+ext+' files run code on your device when opened; only open them from a trusted source.'));
 if(claimed&&real.k!=='unknown'&&claimed!==real.k&&!(claimed==='text'&&real.k==='text')){
  const danger=['exe','elf','dex','script','macho'].includes(real.k)||(real.k==='zip'&&ext!=='zip');
  if(['exe','elf','script','macho','dex'].includes(real.k))add('crit',T('الملف متنكّر','Disguised file'),T('الامتداد .'+ext+' لكن المحتوى الحقيقي «'+real.a+'». هذا أسلوب شائع لنشر البرمجيات الخبيثة.','Extension .'+ext+' but the real content is “'+real.l+'”. A common malware trick.'));
  else add('med',T('الامتداد لا يطابق المحتوى','Extension does not match content'),T('الامتداد .'+ext+' لكن المحتوى «'+real.a+'».','Extension .'+ext+' but content is “'+real.l+'”.'));
 }
 prog&&prog(3);
 // entropy
 let ent=0;if(u.length>2048){ent=entropy(u);R.facts.push([T('العشوائية (Entropy)','Entropy'),ent.toFixed(2)+' / 8'])}
 const packedKinds=['exe','elf','dex','macho'];
 if(ent>7.3&&packedKinds.includes(real.k))add('high',T('الكود مضغوط أو مشفّر','Packed / encrypted code'),T('درجة العشوائية عالية جداً، وهذا يُستخدم لإخفاء ما يفعله البرنامج عن برامج الحماية.','Very high randomness, often used to hide what a program does from security tools.'));
 // per type
 const strs=u.length?ascii(u,3e6):'';
 const hasStr=(re)=>re.test(strs);
 if(real.k==='zip'){
  try{
   await K.files.load('js/vendor/jszip.min.js');const z=await JSZip.loadAsync(u);const names=Object.keys(z.files);
   R.facts.push([T('عدد الملفات داخله','Files inside'),String(names.length)]);
   if(names.includes('AndroidManifest.xml')){
    R.isApk=true;R.facts.push([T('النوع','Kind'),T('تطبيق أندرويد (APK)','Android app (APK)')]);
    const m=axml(await z.file('AndroidManifest.xml').async('uint8array'));
    if(m.pkg)R.facts.push([T('اسم الحزمة','Package'),m.pkg]);if(m.vn)R.facts.push([T('الإصدار','Version'),m.vn]);
    if(m.target)R.facts.push(['targetSdk / minSdk',m.target+' / '+(m.minSdk||'?')]);
    const seen=new Set();let n=0;
    m.perms.forEach(p=>{const k=p.replace(/^android\.permission\./,'');if(seen.has(k))return;seen.add(k);const d=PERM[k];
     if(d){use('lock',T(d[0],d[1]),T(d[2],d[3]),d[4]);if(d[4]==='crit')add('crit',T(d[0],d[1]),T(d[2],d[3]));else if(d[4]==='high')add('high',T(d[0],d[1]),T(d[2],d[3]));else if(d[4]==='med')n++}
     else if(/^android\.permission|^com\.google/.test(p))use('lock',k,T('صلاحية نظام','System permission'),'info')});
    m.services.forEach(s=>{if(/ACCESSIBILITY/.test(s.perm)&&!seen.has('BIND_ACCESSIBILITY_SERVICE'))add('crit',T('خدمة الوصول','Accessibility service'),T('يطلب التحكم بشاشتك.','Asks to control your screen.'));if(/DEVICE_ADMIN/.test(s.perm))add('crit',T('مدير الجهاز','Device admin'),T('يطلب صلاحيات إدارة الجهاز.','Asks for device-admin rights.'))});
    const sensitive=['READ_SMS','SEND_SMS','READ_CONTACTS','RECORD_AUDIO','CAMERA','ACCESS_FINE_LOCATION'].filter(x=>seen.has(x)).length;
    if(sensitive>=4)add('high',T('صلاحيات حساسة كثيرة','Many sensitive permissions'),T('يطلب '+sensitive+' صلاحيات حساسة معاً، وهذا غير معتاد إلا في برامج التجسس.','Requests '+sensitive+' sensitive permissions together — unusual outside spyware.'));
    else if(n>=3)add('med',T('صلاحيات متعددة','Several permissions'),T('يطلب عدة صلاحيات تمسّ خصوصيتك؛ تأكد أن التطبيق يحتاجها فعلاً.','Requests several privacy-related permissions; make sure the app really needs them.'));
    if(m.debuggable)add('med',T('نسخة تجريبية (debuggable)','Debuggable build'),T('التطبيق قابل للفحص والتعديل أثناء التشغيل.','The app can be inspected and modified while running.'));
    if(!names.some(n=>/^META-INF\/.+\.(RSA|DSA|EC)$/i.test(n))&&!names.some(n=>/^META-INF\/.*\.SF$/i.test(n)))add('med',T('بلا توقيع','Unsigned'),T('لم نجد توقيعاً تقليدياً داخل الملف (قد يكون التوقيع v2 أو لا توقيع).','No classic signature found (it may use v2 signing, or none).'));
    const dexNames=names.filter(n=>/^classes\d*\.dex$/.test(n));let dexs='';for(const dn of dexNames.slice(0,3))dexs+=ascii(await z.file(dn).async('uint8array'),6e6)+'\n';
    const chk=[[/DexClassLoader|PathClassLoader/,'med',T('تحميل كود ديناميكي','Dynamic code loading'),T('يحمّل كوداً إضافياً أثناء التشغيل، قد يخفي سلوكاً غير ظاهر.','Loads extra code at runtime, which can hide behaviour.')],
     [/Runtime;->exec|ProcessBuilder/,'med',T('تنفيذ أوامر النظام','Runs system commands'),T('يستطيع تشغيل أوامر على الجهاز.','Can run commands on the device.')],
     [/\/system\/bin\/su|\bsu -c\b|Superuser/,'high',T('يطلب صلاحية الروت','Root access'),T('يحاول الحصول على أعلى صلاحية بالنظام.','Tries to get the highest system privilege.')],
     [/sendTextMessage|sendMultipartTextMessage/,'high',T('كود إرسال SMS','SMS-sending code'),T('يحتوي كوداً يرسل رسائل نصية.','Contains code that sends text messages.')],
     [/AccessibilityService|performGlobalAction/,'high',T('كود تحكم بالشاشة','Screen-control code'),T('يحتوي كوداً يتحكم بالشاشة عبر خدمة الوصول.','Contains code that controls the screen via accessibility.')],
     [/DevicePolicyManager|lockNow|wipeData/,'high',T('كود إدارة/مسح الجهاز','Device admin / wipe code'),T('يحتوي كوداً لقفل الجهاز أو مسحه.','Contains code that can lock or wipe the device.')],
     [/AudioRecord|MediaRecorder/,'low',T('كود تسجيل','Recording code'),T('يحتوي كوداً يسجّل الصوت.','Contains audio-recording code.')],
     [/getInstalledPackages|getInstalledApplications/,'low',T('يفحص التطبيقات المثبتة','Scans installed apps'),T('يسرد التطبيقات الأخرى على جهازك.','Lists other apps on your device.')],
     [/https?:\/\/\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/,'high',T('اتصال بعنوان IP مباشر','Direct IP connection'),T('يتصل بخادم عبر عنوان IP لا اسم نطاق، وهو أسلوب شائع للبرمجيات الخبيثة.','Talks to a server by raw IP, a common malware habit.')]];
    chk.forEach(c=>{if(c[0].test(dexs))add(c[1],c[2],c[3])});
    if(names.some(n=>/^assets\/.+\.(apk|dex|jar)$/i.test(n)))add('high',T('ملف تنفيذي مخفي داخله','Hidden executable inside'),T('يحتوي تطبيقاً أو كوداً إضافياً مخبأً في المرفقات (أسلوب Dropper).','Hides another app or code in assets (dropper behaviour).'));
    if(names.some(n=>/^lib\/.+\.so$/.test(n)))use('cpu',T('مكتبات أصلية','Native libraries'),T('يحتوي كوداً مترجماً (C/C++) يصعب فحصه.','Contains compiled C/C++ code that is hard to inspect.'),'low');
    use('package',T('المكونات','Components'),T(m.activities+' شاشة · '+m.receivers+' مستقبِل أحداث · '+m.services.length+' خدمة',m.activities+' screens · '+m.receivers+' receivers · '+m.services.length+' services'),'info');
   }else{
    // other zip based
    const exe=names.filter(n=>/\.(exe|scr|bat|cmd|com|vbs|js|ps1|msi|lnk|jar|apk|dll|sh)$/i.test(n));
    if(names.some(n=>/vbaProject\.bin$/i.test(n))){add('high',T('يحتوي ماكرو (VBA)','Contains a macro (VBA)'),T('الماكرو برنامج صغير داخل المستند، وهو وسيلة شائعة لنشر البرمجيات الخبيثة. لا تفعّله إلا إن كنت واثقاً.','A macro is a small program inside the document — a common malware vector. Do not enable it unless you trust the source.'));use('cpu',T('ماكرو VBA','VBA macro'),T('كود يعمل داخل المستند','Code that runs inside the document'),'high')}
    if(names.some(n=>/(embeddings|oleObject)/i.test(n)))add('med',T('عناصر مضمّنة (OLE)','Embedded objects (OLE)'),T('المستند يخفي ملفات أو كائنات بداخله.','The document hides embedded files or objects.'));
    const rels=names.filter(n=>/\.rels$/.test(n));for(const rn of rels.slice(0,40)){const t=await z.file(rn).async('string');if(/TargetMode="External"[^>]*attachedTemplate|attachedTemplate[^>]*TargetMode="External"/i.test(t))add('high',T('قالب خارجي','Remote template'),T('المستند يحمّل قالباً من الإنترنت عند فتحه (تقنية معروفة).','The document loads a template from the internet when opened (a known technique).'));if(/Target="https?:\/\/[^"]+"[^>]*TargetMode="External"/i.test(t)&&/oleObject|externalLink|frame/i.test(t))add('med',T('رابط خارجي مضمّن','Embedded external link'),T('يحتوي مرجعاً لموقع خارجي.','Contains a reference to an external site.'))}
    if(exe.length)add(ext==='zip'?'high':'med',T('ملفات تنفيذية داخل الأرشيف','Executables inside archive'),T('بداخله: '+exe.slice(0,4).join('، ')+(exe.length>4?' …':''),'Inside: '+exe.slice(0,4).join(', ')+(exe.length>4?' …':'')));
    let tot=0;for(const n of names.slice(0,300)){const o=z.files[n];if(o._data&&o._data.uncompressedSize)tot+=o._data.uncompressedSize}
    if(tot>f.size*200&&tot>200e6)add('high',T('قنبلة ضغط','Zip bomb'),T('حجمه بعد فكّ الضغط ضخم جداً مقارنة بحجمه (قد يعطّل جهازك).','Enormous when unpacked compared to its size (can freeze your device).'));
    use('file-text',T('محتويات الأرشيف','Archive contents'),names.slice(0,8).join('\n')+(names.length>8?'\n… +'+(names.length-8):''),'info');
   }
  }catch(e){add('low',T('تعذّر فتح الأرشيف','Could not open the archive'),T('قد يكون محمياً بكلمة مرور أو تالفاً.','It may be password-protected or damaged.'))}
 }
 else if(real.k==='pdf'){
  const fl=[[/\/JavaScript|\/JS\b/,'high',T('يحتوي جافاسكربت','Contains JavaScript'),T('ملفات PDF العادية نادراً تحتاج كوداً؛ وهذا يُستغل لاستهداف القارئ.','Normal PDFs rarely need code; attackers abuse it.')],
   [/\/OpenAction|\/AA\b/,'med',T('إجراء تلقائي عند الفتح','Auto-action on open'),T('ينفّذ شيئاً عند فتح الملف دون ضغطك.','Does something as soon as it opens.')],
   [/\/Launch\b/,'crit',T('يشغّل برنامجاً','Launches a program'),T('يحاول تشغيل ملف أو أمر على جهازك.','Tries to run a file or command on your device.')],
   [/\/EmbeddedFile/,'med',T('ملف مضمّن','Embedded file'),T('يحمل ملفاً آخر بداخله.','Carries another file inside.')],
   [/\/URI\s*\(/,'low',T('روابط خارجية','External links'),T('يحتوي روابط لمواقع، تحقق منها قبل الضغط.','Contains links; check them before clicking.')]];
  const raw=new TextDecoder('latin1').decode(u.subarray(0,Math.min(u.length,3e6)));fl.forEach(c=>{if(c[0].test(raw))add(c[1],c[2],c[3])});
  use('file-text',T('مستند PDF','PDF document'),T('نص وصور وروابط','Text, images and links'),'info');
 }
 else if(real.k==='ole'){
  if(/VBA|Macros|_VBA_PROJECT/.test(strs)){add('high',T('يحتوي ماكرو','Contains macros'),T('ملفات أوفيس القديمة مع الماكرو من أكثر ناقلات الفيروسات.','Legacy Office files with macros are a top malware carrier.'));use('cpu',T('ماكرو','Macro'),T('كود داخل المستند','Code inside the document'),'high')}
  if(/AutoOpen|Document_Open|Workbook_Open|Shell\(|WScript/i.test(strs))add('crit',T('ماكرو يعمل تلقائياً','Auto-running macro'),T('يتشغّل بمجرد الفتح ويستدعي أوامر نظام.','Runs on open and calls system commands.'));
 }
 else if(['exe','elf','macho'].includes(real.k)){
  use('cpu',T('برنامج تنفيذي','Executable program'),T('ينفّذ تعليمات مباشرة على جهازك بصلاحياتك.','Runs instructions directly on your device with your privileges.'),'med');
  const apis=[['VirtualAllocEx|WriteProcessMemory|CreateRemoteThread|NtUnmapViewOfSection','crit',T('حقن في عمليات أخرى','Process injection'),T('يحقن كوداً داخل برامج أخرى، سلوك شائع جداً للفيروسات.','Injects code into other programs — classic malware behaviour.')],
   ['URLDownloadToFile|InternetOpenUrl|WinHttpOpen|WSAStartup','med',T('اتصال وتحميل','Network / download'),T('يستطيع الاتصال وتحميل ملفات.','Can connect and download files.')],
   ['SetWindowsHookEx|GetAsyncKeyState|GetKeyboardState','high',T('مراقبة لوحة المفاتيح','Keyboard monitoring'),T('يلتقط ما تكتبه (Keylogger).','Captures what you type (keylogger).')],
   ['RegSetValueEx|CurrentVersion\\\\Run','med',T('تعديل السجل / التشغيل التلقائي','Registry / autorun'),T('يضيف نفسه ليعمل عند تشغيل ويندوز.','Adds itself to run at Windows startup.')],
   ['IsDebuggerPresent|CheckRemoteDebuggerPresent','med',T('يتهرّب من الفحص','Anti-debugging'),T('يكتشف أدوات التحليل ليخفي نفسه.','Detects analysis tools to hide itself.')],
   ['cmd\\.exe|powershell|/bin/sh','med',T('يشغّل أوامر','Runs commands'),T('يستدعي سطر الأوامر.','Calls the command line.')],
   ['CryptEncrypt|\\.locked|YOUR FILES|bitcoin|ransom','high',T('مؤشرات فدية','Ransomware hints'),T('يحتوي كلمات أو دوال تشفير مرتبطة ببرامج الفدية.','Contains encryption calls or words tied to ransomware.')]];
  apis.forEach(a=>{if(new RegExp(a[0],'i').test(strs))add(a[1],a[2],a[3])});
  if(!R.risks.some(r=>r.sev!=='low'&&r.sev!=='info'))add('low',T('برنامج غير معروف','Unknown program'),T('لا يمكننا التحقق من سمعته محلياً؛ افحصه في VirusTotal قبل التشغيل.','We cannot verify its reputation locally; check it on VirusTotal before running.'));
 }
 else if(real.k==='script'||real.k==='text'){
  const raw=new TextDecoder('utf-8').decode(u.subarray(0,Math.min(u.length,2e6)));
  const P=[[/powershell[^\n]*-e(nc|ncodedcommand)?\s+[A-Za-z0-9+\/=]{20,}/i,'crit',T('أمر PowerShell مشفّر','Encoded PowerShell command'),T('يخفي ما سينفّذه بترميز Base64.','Hides what it runs with Base64.')],
   [/(IEX|Invoke-Expression)\b|DownloadString|DownloadFile|Net\.WebClient/i,'high',T('يحمّل وينفّذ كوداً','Downloads & runs code'),T('يجلب كوداً من الإنترنت وينفّذه.','Pulls code from the internet and runs it.')],
   [/WScript\.Shell|CreateObject\(|ActiveXObject|\.Run\(/i,'high',T('يشغّل أوامر ويندوز','Runs Windows commands'),T('يستخدم كائنات نظام لتنفيذ أوامر.','Uses system objects to run commands.')],
   [/eval\s*\(\s*(atob|unescape|String\.fromCharCode)/i,'high',T('كود جافاسكربت مموّه','Obfuscated JavaScript'),T('يفكّ ترميز نص ثم ينفّذه، وهو أسلوب إخفاء.','Decodes text then executes it — an obfuscation trick.')],
   [/(curl|wget)[^\n|]*\|\s*(sudo\s+)?(ba)?sh/i,'high',T('تحميل وتشغيل من الإنترنت','Pipe-to-shell install'),T('ينزّل سكربتاً ويشغّله فوراً.','Downloads a script and runs it immediately.')],
   [/\/dev\/tcp\/|nc\s+-e|bash\s+-i\s+>&|mkfifo/i,'crit',T('نمط باب خلفي (Reverse Shell)','Reverse-shell pattern'),T('يفتح اتصالاً يتيح لغيرك التحكم بجهازك.','Opens a connection that lets someone else control your device.')],
   [/rm\s+-rf\s+(\/|~|\*)|format\s+c:|del\s+\/[sfq]/i,'high',T('أوامر حذف خطيرة','Destructive commands'),T('قد يمسح ملفاتك.','May wipe your files.')],
   [/[A-Za-z0-9+\/]{300,}={0,2}/,'med',T('كتلة مشفّرة طويلة','Long encoded blob'),T('نص طويل مرمّز قد يخفي كوداً.','A long encoded string that may hide code.')],
   [/<iframe[^>]*(width|height)=["']?0|display\s*:\s*none[^>]*<iframe/i,'high',T('إطار مخفي','Hidden iframe'),T('يحمّل صفحة خفية داخل هذه الصفحة.','Loads a hidden page inside this one.')],
   [/<input[^>]+type=["']?password/i,'med',T('حقل كلمة مرور','Password field'),T('احذر من إدخال بياناتك إن لم تعرف مصدر الصفحة.','Be careful entering data if you do not know the page source.')]];
  P.forEach(c=>{if(c[0].test(raw))add(c[1],c[2],c[3])});
  const ips=[...new Set((raw.match(/https?:\/\/\d{1,3}(\.\d{1,3}){3}[^\s"']*/g)||[]))].slice(0,3);if(ips.length)add('med',T('روابط بعناوين IP','Links to raw IPs'),ips.join('\n'));
  use('file-text',real.k==='script'?T('سكربت','Script'):T('ملف نصي','Text file'),T('يمكن قراءته كنص','Readable as text'),'info');
 }
 else if(['png','jpg','gif'].includes(real.k)){
  const tail=real.k==='png'?(()=>{const s=new TextDecoder('latin1').decode(u);const i=s.lastIndexOf('IEND');return i>0?u.length-(i+8):0})():real.k==='jpg'?(()=>{for(let i=u.length-2;i>2;i--){if(u[i]===0xff&&u[i+1]===0xd9)return u.length-(i+2)}return 0})():0;
  if(tail>256)add('high',T('بيانات مخفية بعد الصورة','Data hidden after the image'),T('يوجد '+fmtSize(tail)+' إضافية بعد نهاية الصورة، قد تكون ملفاً مخبأً.','There are '+fmtSize(tail)+' appended after the image ends — possibly a hidden file.'));
  use('image',T('صورة','Image'),T('لا تحتوي كوداً قابلاً للتشغيل عادةً','Normally holds no runnable code'),'info');
 }
 prog&&prog(4);
 if(!R.uses.length)use('file-text',T('بيانات فقط','Data only'),T('لا يظهر أنه يستخدم أي ميزة في جهازك.','It does not appear to use any device feature.'),'info');
 R.tips=[T('افحص البصمة (SHA-256) في VirusTotal للتحقق من سمعته عند محركات حماية متعددة.','Check the SHA-256 on VirusTotal to see what many security engines say.'),T('هذا الفحص تحليل سلوكي محلي ولا يغني عن مضاد فيروسات.','This is a local behavioural analysis and does not replace antivirus.')];
 return R}

/* ---------------- url analysis ---------------- */
const BRANDS={paypal:'paypal.com',google:'google.com',gmail:'google.com',facebook:'facebook.com',instagram:'instagram.com',tiktok:'tiktok.com',whatsapp:'whatsapp.com',telegram:'telegram.org',apple:'apple.com',icloud:'apple.com',microsoft:'microsoft.com',outlook:'microsoft.com',amazon:'amazon.com',netflix:'netflix.com',binance:'binance.com',twitter:'x.com',snapchat:'snapchat.com',steam:'steampowered.com',roblox:'roblox.com',youtube:'youtube.com',zain:'zain.com',stcpay:'stcpay.com.sa',alrajhi:'alrajhibank.com.sa'};
const SHORT=new Set('bit.ly tinyurl.com t.co is.gd cutt.ly rb.gy shorturl.at goo.gl ow.ly buff.ly tiny.cc s.id v.gd t.ly'.split(' '));
const BADTLD=new Set('zip mov xyz top tk ml ga cf gq click link work loan men country stream download review science party bid win icu cyou monster rest buzz'.split(' '));
const SEC2=new Set(['co','com','net','org','gov','edu','ac']);
function regDomain(h){const p=h.split('.');if(p.length<=2)return h;const l=p.length;return (SEC2.has(p[l-2])&&p[l-1].length===2)?p.slice(-3).join('.'):p.slice(-2).join('.')}
function urlHeur(raw,add,use){
 let u;try{u=new URL(raw)}catch(e){return null}
 const host=u.hostname.toLowerCase(),reg=regDomain(host),tld=host.split('.').pop();
 if(u.protocol==='http:')add('med',T('بدون تشفير (HTTP)','Not encrypted (HTTP)'),T('أي بيانات تُدخلها يمكن قراءتها أثناء الإرسال.','Anything you type can be read in transit.'));
 if(/^\d{1,3}(\.\d{1,3}){3}$/.test(host)||/^\[/.test(host))add('high',T('عنوان IP بدل اسم موقع','Raw IP instead of a site name'),T('المواقع الحقيقية نادراً تستخدم أرقاماً مباشرة، وهو أسلوب شائع في الاحتيال.','Real sites rarely use bare numbers; common in scams.'));
 if(/xn--/.test(host))add('high',T('أحرف مموّهة (Punycode)','Look-alike characters (Punycode)'),T('اسم الموقع يحتوي أحرفاً تشبه أحرفاً أخرى لخداعك.','The name uses characters that mimic others to fool you.'));
 if(u.username||/@/.test(raw.split('//')[1]?.split('/')[0]||''))add('high',T('علامة @ في الرابط','“@” in the link'),T('تُستخدم لإخفاء الوجهة الحقيقية خلف اسم يبدو موثوقاً.','Used to hide the real destination behind a trusted-looking name.'));
 if(SHORT.has(host))add('med',T('رابط مختصر','Shortened link'),T('يخفي الوجهة الحقيقية حتى فتحه.','Hides the real destination until opened.'));
 if(BADTLD.has(tld))add('med',T('نطاق شائع في الاحتيال (.'+tld+')','Scam-prone domain (.'+tld+')'),T('هذه النهاية رخيصة وتُستخدم كثيراً في مواقع مؤقتة وضارة.','This ending is cheap and often used for throwaway or malicious sites.'));
 const subs=host.split('.').length-2;if(subs>=4)add('med',T('نطاقات فرعية كثيرة','Many subdomains'),T('اسم طويل بطبقات كثيرة لإخفاء النطاق الأصلي.','A long layered name used to hide the real domain.'));
 if((host.match(/-/g)||[]).length>=3)add('low',T('شرطات كثيرة في الاسم','Many hyphens'),T('أسماء المواقع الموثوقة نادراً تحتوي شرطات كثيرة.','Trusted sites rarely have many hyphens.'));
 if(raw.length>120)add('low',T('رابط طويل جداً','Very long link'),T(raw.length+' حرفاً.',raw.length+' characters.'));
 const norm=host.replace(/0/g,'o').replace(/1/g,'l').replace(/3/g,'e').replace(/5/g,'s').replace(/@/g,'a').replace(/rn/g,'m');
 for(const b in BRANDS){const dom=BRANDS[b];if((norm.includes(b)||host.includes(b))&&reg!==dom&&!host.endsWith('.'+dom)){add('crit',T('ينتحل «'+b+'»','Impersonates “'+b+'”'),T('الاسم يشبه '+dom+' لكن النطاق الحقيقي هو '+reg+'.','The name resembles '+dom+' but the real domain is '+reg+'.'));break}}
 const kw=(u.pathname+u.search+host).toLowerCase().match(/login|signin|verify|secure|account|update|confirm|wallet|password|billing|suspend|unlock|free|gift|prize|claim|bonus|otp/g);
 if(kw&&kw.length>=2)add('med',T('كلمات استدراج','Lure keywords'),T('يحتوي: '+[...new Set(kw)].slice(0,5).join('، ')+' — تُستخدم لاستعجالك.','Contains: '+[...new Set(kw)].slice(0,5).join(', ')+' — used to rush you.'));
 if(u.port&&!['80','443'].includes(u.port))add('low',T('منفذ غير معتاد','Unusual port'),T('المنفذ '+u.port,'Port '+u.port));
 if(/\.(exe|apk|scr|msi|bat|zip|rar|jar|js)(\?|$)/i.test(u.pathname))add('high',T('رابط تحميل ملف قابل للتشغيل','Links to a runnable download'),T('الرابط ينزّل ملفاً '+(u.pathname.match(/\.(\w+)$/)||[])[0]+' مباشرة.','The link downloads a '+(u.pathname.match(/\.(\w+)$/)||[])[0]+' file directly.'));
 return {u,host,reg}}
async function analyzeUrl(raw,deep,prog){
 if(!/^[a-z]+:\/\//i.test(raw))raw='https://'+raw;
 const R={kind:deep?'site':'url',name:raw,facts:[],uses:[],risks:[],tips:[],hash:''};
 const add=(sev,t,d)=>R.risks.push({sev,t,d});const use=(ic,t,d,sev)=>R.uses.push({ic,t,d,sev:sev||'info'});
 const h=urlHeur(raw,add,use);prog&&prog(1);
 if(!h){R.invalid=true;add('high',T('رابط غير صالح','Invalid link'),T('تعذّر فهم الرابط.','The link could not be parsed.'));return R}
 R.facts.push([T('النطاق','Domain'),h.reg],[T('البروتوكول','Protocol'),h.u.protocol.replace(':','').toUpperCase()]);
 R.url=raw;
 let N=null;try{N=await K.native.scanUrl(raw,deep)}catch(e){}
 prog&&prog(2);
 if(!N||!N.ok){R.unreachable=true;add('med',T('تعذّر الوصول إلى الموقع','Could not reach the site'),T('قد يكون متوقفاً أو محجوباً أو غير موجود. '+(N&&N.error?N.error:''),'It may be down, blocked or not exist. '+(N&&N.error?N.error:'')));
  if(N&&N.ssl_error)add('high',T('مشكلة في شهادة الأمان','Certificate problem'),N.ssl_error);return R}
 // chain
 const ch=N.chain||[];if(ch.length>1){R.facts.push([T('التحويلات','Redirects'),String(ch.length-1)]);
  const fh=(()=>{try{return new URL(N.final).hostname}catch(e){return''}})();if(fh&&regDomain(fh)!==h.reg){add(ch.length>3?'high':'med',T('يحوّلك إلى موقع آخر','Redirects to another site'),T('من '+h.reg+' إلى '+regDomain(fh),'From '+h.reg+' to '+regDomain(fh)));
   const h2=urlHeur(N.final,add,use)}
  if(ch.length>4)add('med',T('تحويلات كثيرة','Many redirects'),T(ch.length-1+' تحويلات متتالية.',ch.length-1+' chained redirects.'))}
 R.facts.push([T('حالة الاستجابة','HTTP status'),String(N.status)]);if(N.ips&&N.ips.length)R.facts.push(['IP',N.ips.slice(0,3).join(' , ')]);
 R.facts.push([T('زمن الاستجابة','Response time'),N.ms+' ms']);
 prog&&prog(3);
 // tls
 const tl=N.tls;if(tl){const days=Math.round((tl.to-Date.now())/864e5),age=Math.round((Date.now()-tl.from)/864e5);const cn=(tl.subject.match(/CN=([^,]+)/)||[])[1]||tl.subject,iss=(tl.issuer.match(/O=([^,]+)/)||tl.issuer.match(/CN=([^,]+)/)||[])[1]||tl.issuer;
  R.tls={cn,iss,days,age,cipher:tl.cipher};R.facts.push([T('شهادة الأمان','Certificate'),cn+' — '+iss]);R.facts.push([T('صلاحيتها','Valid for'),T(days+' يوماً متبقية',days+' days left')]);
  if(days<0)add('high',T('الشهادة منتهية','Expired certificate'),T('انتهت صلاحية شهادة الأمان.','The security certificate has expired.'));
  else if(days<7)add('low',T('الشهادة توشك على الانتهاء','Certificate expires soon'),T('تبقّى '+days+' أيام.',days+' days left.'));
  if(tl.self)add('high',T('شهادة موقّعة ذاتياً','Self-signed certificate'),T('لا جهة موثوقة تضمن هوية الموقع.','No trusted authority vouches for this site.'));
  if(age<10)add('med',T('شهادة حديثة جداً','Very new certificate'),T('أُصدرت قبل '+age+' أيام فقط؛ مواقع الاحتيال غالباً جديدة.','Issued only '+age+' days ago; scam sites are often new.'));
  use('lock',T('اتصال مشفّر','Encrypted connection'),T('الجهة المُصدِرة: '+iss,'Issuer: '+iss),'info')}
 else if(h.u.protocol==='https:'&&N.ssl_error)add('high',T('فشل التحقق من الشهادة','Certificate check failed'),N.ssl_error);
 // headers
 const hd=N.headers||{};
 if(deep){
  const hh=[['strict-transport-security','HSTS',T('يفرض HTTPS دائماً','Forces HTTPS')],['content-security-policy','CSP',T('يمنع تنفيذ سكربتات غريبة','Blocks foreign scripts')],['x-frame-options','X-Frame-Options',T('يمنع عرض الموقع داخل إطار مخادع','Stops clickjacking frames')],['x-content-type-options','X-Content-Type-Options',T('يمنع تخمين نوع الملفات','Stops MIME sniffing')],['referrer-policy','Referrer-Policy',T('يحمي خصوصية الروابط','Protects link privacy')],['permissions-policy','Permissions-Policy',T('يقيّد ميزات المتصفح','Limits browser features')]];
  R.headers=hh.map(x=>({n:x[1],ok:!!hd[x[0]],d:x[2]}));const missing=R.headers.filter(x=>!x.ok).length;
  R.grade=missing<=1?'A':missing===2?'B':missing===3?'C':missing<=4?'D':'F';
  if(missing>=4)add('low',T('ترويسات حماية ناقصة','Missing security headers'),T('ينقص الموقع '+missing+' من 6 ترويسات حماية معيارية.','The site lacks '+missing+' of 6 standard security headers.'));
  const sc=hd['set-cookie']||'';if(sc){const bad=[!/secure/i.test(sc)&&'Secure',!/httponly/i.test(sc)&&'HttpOnly',!/samesite/i.test(sc)&&'SameSite'].filter(Boolean);use('database',T('ملفات تعريف الارتباط','Cookies'),bad.length?T('تفتقد: '+bad.join('، '),'Missing: '+bad.join(', ')):T('محميّة جيداً','Well protected'),bad.length?'low':'info')}
  if(hd.server||hd['x-powered-by'])use('server',T('التقنية المكشوفة','Exposed technology'),[hd.server,hd['x-powered-by']].filter(Boolean).join(' · '),'info');
 }
 prog&&prog(4);
 const pg=N.page;
 if(pg){
  if(pg.title)R.facts.push([T('عنوان الصفحة','Page title'),pg.title]);
  const fo=(pg.formHosts||[]).filter(x=>x&&regDomain(x)!==h.reg);
  if(pg.password>0&&fo.length)add('crit',T('نموذج كلمة مرور يرسل لموقع آخر','Password form posts elsewhere'),T('الصفحة تطلب كلمة مرور وترسلها إلى '+fo[0]+' — علامة تصيّد واضحة.','The page asks for a password and sends it to '+fo[0]+' — a clear phishing sign.'));
  else if(pg.password>0&&h.u.protocol==='http:')add('high',T('كلمة مرور بدون تشفير','Password over HTTP'),T('تُرسل كلمة المرور دون حماية.','The password is sent unprotected.'));
  else if(pg.password>0)use('lock',T('نموذج تسجيل دخول','Login form'),T('تأكد من أن العنوان هو الموقع الرسمي قبل الإدخال.','Make sure the address is the official site before typing.'),'low');
  if(pg.hiddenIframes)add('high',T('إطار مخفي','Hidden iframe'),T('يحمّل صفحة خفية داخل الصفحة.','Loads a hidden page inside the page.'));
  else if(pg.iframes>3)add('low',T('إطارات كثيرة','Many iframes'),T(pg.iframes+' إطارات.',pg.iframes+' iframes.'));
  if(pg.eval>2&&pg.atob>0)add('high',T('سكربتات مموّهة','Obfuscated scripts'),T('الصفحة تفكّ ترميز نصوص وتنفّذها.','The page decodes strings and executes them.'));
  else if(pg.eval>3)add('low',T('استخدام eval','Uses eval'),T('كود ديناميكي قد يخفي سلوكاً.','Dynamic code that may hide behaviour.'));
  if(pg.metaRefresh)add('low',T('تحويل تلقائي بالصفحة','Auto page refresh'),T('الصفحة تحوّلك تلقائياً.','The page redirects you automatically.'));
  if(pg.mixed>0&&h.u.protocol==='https:')add('low',T('محتوى غير مشفّر داخل صفحة مشفّرة','Mixed content'),T(pg.mixed+' عنصر عبر HTTP.',pg.mixed+' item(s) over HTTP.'));
  if((pg.extScripts||[]).length)use('cpu',T('سكربتات من مواقع أخرى','Third-party scripts'),pg.extScripts.slice(0,8).join('\n'),pg.extScripts.length>8?'low':'info');
  if(pg.forms>0&&!pg.password)use('file-text',T('نماذج إدخال','Input forms'),T(pg.forms+' نموذج',pg.forms+' form(s)'),'info');
 }
 const ct=hd['content-type']||'';if(/octet-stream|zip|android|msdownload/i.test(ct))add('high',T('الرابط ينزّل ملفاً','The link downloads a file'),T('نوع المحتوى: '+ct,'Content type: '+ct));
 if(!R.uses.length)use('globe',T('موقع ويب','Website'),T('صفحة عادية بلا ميزات خاصة','An ordinary page with no special features'),'info');
 R.tips=[T('تحقق من العنوان حرفاً بحرف قبل إدخال أي بيانات.','Check the address letter by letter before entering any data.'),T('هذا الفحص لا يتحقق من قواعد بيانات الاحتيال العالمية؛ استخدم أزرار Google وVirusTotal أدناه للتأكد.','This scan does not query global scam databases; use the Google and VirusTotal buttons below to double-check.')];
 return R}

function finish(R){
 let sc=0;const order={crit:0,high:1,med:2,low:3,info:4};
 {const seen={};R.risks=R.risks.filter(r=>{if(seen[r.t])return false;seen[r.t]=1;return true})}
 R.risks.sort((a,b)=>order[a.sev]-order[b.sev]);R.risks.forEach(r=>{sc+=W[r.sev]||0});
 const worst=R.risks.length?R.risks[0].sev:'info';
 sc=Math.min(100,sc);if(worst==='crit')sc=Math.max(sc,82);else if(worst==='high')sc=Math.max(sc,52);
 R.score=Math.round(sc);R.level=R.partial&&R.score<24?'low':levelOf(R.score);R.ts=Date.now();return R}

/* ---------------- history ---------------- */
const hist=()=>{try{return JSON.parse(K.pref.get('scan_hist','[]'))}catch(e){return[]}};
const save=R=>{const h=hist();h.unshift({k:R.kind,n:R.name,l:R.level,s:R.score,t:R.ts,r:R});K.pref.set('scan_hist',JSON.stringify(h.slice(0,12)))};
const textReport=R=>{const L=LV[R.level];return [T('تقرير فحص — بوصلة','Scan report — Bawsala'),R.name,T('النتيجة: ','Result: ')+T(L.a,L.n)+' ('+R.score+'/100)','',...R.risks.map(r=>'['+r.sev.toUpperCase()+'] '+r.t+' — '+r.d),'',...R.facts.map(f=>f[0]+': '+f[1])].join('\n')};

/* ---------------- UI ---------------- */
function sheet(html){const w=document.createElement('div');w.className='cd-layer';w.innerHTML='<div class="cd-bd"></div><div class="cd-sheet"><i class="cd-grab"></i>'+html+'</div>';$('scan').appendChild(w);const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),220)};w.querySelector('.cd-bd').onclick=close;w.close=close;return w}
const KIND={enc:['lock',T('التشفير','Encryption'),T('شفّر ملفاتك وتطبيقاتك ومواقعك في محادثة خاصة','Encrypt files, apps and sites in a private chat'),'#f59e0b'],url:['link',T('فحص رابط','Scan a link'),T('بنية الرابط والتحويلات والانتحال','Link structure, redirects and impersonation'),'#4d6bfe'],
 file:['file',T('فحص ملف','Scan a file'),T('أي صيغة: APK وEXE وPDF وZIP وأوفيس…','Any format: APK, EXE, PDF, ZIP, Office…'),'#8b5cf6'],
 site:['globe',T('فحص موقع منشور','Scan a public website'),T('الشهادة وترويسات الأمان ومحتوى الصفحة','Certificate, security headers and page content'),'#0ea5e9']};
K.scan={
 sha256,
 isOpen(){return !!$('scan')},
 back(){
  if(K.scan.runner&&K.scan.runner.active){K.scan.runner.cancel();return true}
  const r=$('scanres');if(r){r.classList.add('out');setTimeout(()=>r.remove(),240);return true}
  const sh=document.querySelector('#scan .cd-layer:not(.out)');if(sh){sh.close();return true}
  const d=$('scan');if(d){d.classList.add('out');setTimeout(()=>d.remove(),240);return true}return false},
 open(){
  if($('scan'))$('scan').remove();
  const d=document.createElement('div');d.id='scan';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);K.scan.paint()},
 paint(){
  const d=$('scan');if(!d)return;const h=hist();
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="scBack">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('الأمان والفحص','Security & Scan')+'</div><span class="sc-ib ph"></span></div><div class="sc-body">'
   +'<div class="sc-hero"><div class="sc-shield">'+em('1f6e1','big')+'<i class="r r1"></i><i class="r r2"></i><i class="r r3"></i></div><b>'+T('افحص قبل أن تفتح','Scan before you open')+'</b><span>'+T('روابط وملفات ومواقع — نكشف الخطر ونشرح لك ماذا يستخدم على جهازك','Links, files and sites — we spot the danger and explain what it uses on your device')+'</span></div>'
   +['url','file','site','enc'].map((k,i)=>'<button class="sc-act" data-k="'+k+'" style="--c:'+KIND[k][3]+';--i:'+i+'"><span class="ti">'+K.icon(KIND[k][0],24)+'</span><div><b>'+KIND[k][1]+'</b><small>'+KIND[k][2]+'</small></div>'+K.icon('chevron-left',20)+'</button>').join('')
   +'<div class="sc-sect">'+T('آخر الفحوصات','Recent scans')+'</div>'
   +(h.length?h.map((x,i)=>'<button class="sc-h" data-i="'+i+'"><span class="dot" style="background:'+LV[x.l].c+'"></span><div><b>'+esc(x.n.length>46?x.n.slice(0,44)+'…':x.n)+'</b><small>'+T(LV[x.l].a,LV[x.l].n)+' · '+x.s+'/100</small></div><span class="k">'+T({url:'رابط',file:'ملف',site:'موقع'}[x.k],{url:'Link',file:'File',site:'Site'}[x.k])+'</span></button>').join(''):'<div class="sc-empty">'+em('1f50d')+'<span>'+T('لا توجد فحوصات بعد','No scans yet')+'</span></div>')
   +'<p class="sc-note">'+T('الفحص تحليل بنية وسلوك محلي ولا يغني عن مضاد فيروسات.','The scan is a local structural/behavioural analysis and does not replace antivirus.')+'</p></div>';
  K.emojifyEl&&0;
  $('scBack').onclick=()=>K.scan.back();
  d.querySelectorAll('.sc-act').forEach(b=>b.onclick=()=>b.dataset.k==='enc'?K.enc.open():K.scan.start(b.dataset.k));
  d.querySelectorAll('.sc-h').forEach(b=>b.onclick=()=>K.scan.result(h[+b.dataset.i].r,true))},
 start(k){
  if(k==='file'){K.scan.pickAndRun();return}
  const w=sheet('<h3 class="cd-h">'+KIND[k][1]+'</h3><p class="cd-p">'+(k==='site'?T('أدخل عنوان موقع منشور على الإنترنت وسنفحص شهادته وترويسات حمايته ومحتوى صفحته.','Enter the address of a published site and we will inspect its certificate, security headers and page content.'):T('الصق الرابط المشكوك فيه ولا تفتحه.','Paste the suspicious link — do not open it.'))+'<div class="sc-in"><input id="scU" dir="ltr" placeholder="https://example.com" autocomplete="off" autocapitalize="off" inputmode="url"><button id="scPaste">'+K.icon('copy',20)+'</button></div><button class="cd-go" id="scGo">'+T('ابدأ الفحص','Start scan')+'</button><button class="cd-cancel" id="scX">'+T('إلغاء','Cancel')+'</button>');
  const inp=w.querySelector('#scU');setTimeout(()=>inp.focus(),300);
  w.querySelector('#scX').onclick=w.close;
  w.querySelector('#scPaste').onclick=async()=>{try{inp.value=(await navigator.clipboard.readText()).trim()}catch(e){const v=await K.ui.prompt({title:T('الصق الرابط','Paste the link'),hint:'https://'});if(v)inp.value=v}};
  const go=()=>{const v=inp.value.trim();if(!v){K.toast(T('أدخل رابطاً','Enter a link'));return}w.close();K.scan.run(k,v)};
  w.querySelector('#scGo').onclick=go;inp.onkeydown=e=>{if(e.key==='Enter')go()}},
 async pickAndRun(){const f=await K.native.pickFile();if(!f||!f.name)return;K.scan.run('file',f)},
 /* the scan session: animated radar + steps, then the result */
 async run(kind,input){
  const steps=kind==='file'?[T('قراءة الملف','Reading the file'),T('حساب البصمة SHA-256','Computing SHA-256'),T('التحقق من النوع الحقيقي','Checking the real type'),T('تحليل المحتوى والصلاحيات','Analysing content & permissions'),T('تقييم الخطر','Scoring the risk')]
   :[T('تحليل بنية الرابط','Analysing link structure'),T('الاتصال وتتبّع التحويلات','Connecting & following redirects'),T('فحص الشهادة والاتصال','Checking certificate & connection'),kind==='site'?T('فحص ترويسات الأمان والكوكيز','Checking security headers & cookies'):T('تحليل محتوى الصفحة','Analysing page content'),T('تقييم الخطر','Scoring the risk')];
  const name=kind==='file'?input.name:input;
  const o=document.createElement('div');o.id='scanrun';o.className='sc-run';o.dir=K.lang()==='ar'?'rtl':'ltr';
  o.innerHTML='<div class="sc-top"><button class="sc-ib" id="srX">'+K.icon('x',24)+'</button><div class="sc-title">'+T('جلسة فحص','Scan session')+'</div><span class="sc-ib ph"></span></div>'
   +'<div class="sc-radar"><i class="rg g1"></i><i class="rg g2"></i><i class="rg g3"></i><i class="sweep"></i>'+em('1f6e1','big')+'</div>'
   +'<div class="sc-tgt"><span>'+KIND[kind][1]+'</span><b dir="ltr">'+esc(name.length>48?name.slice(0,46)+'…':name)+'</b></div>'
   +'<div class="sc-prog"><i id="srBar"></i></div><div class="sc-time" id="srTime">0:00</div>'
   +'<div class="sc-steps">'+steps.map((s,i)=>'<div class="st" id="st'+i+'"><span class="ic"><i class="sp"></i></span><span>'+s+'</span></div>').join('')+'</div>';
  document.body.appendChild(o);
  const R={active:true,cancel(){R.active=false;clearInterval(tick);o.remove()}};K.scan.runner=R;
  $('srX').onclick=()=>R.cancel();
  const t0=Date.now();let done=0;const mark=i=>{for(let j=done;j<i&&j<steps.length;j++){const e=$('st'+j);if(e){e.classList.add('ok');e.querySelector('.ic').innerHTML=K.icon('check',16,3.2)}}done=Math.max(done,i);const nx=$('st'+done);if(nx)nx.classList.add('now');$('srBar').style.width=Math.min(100,done/steps.length*100)+'%'};
  const nx0=$('st0');nx0&&nx0.classList.add('now');
  const tick=setInterval(()=>{const s=Math.floor((Date.now()-t0)/1000),e=$('srTime');if(e)e.textContent=Math.floor(s/60)+':'+String(s%60).padStart(2,'0')},250);
  let rep=null,err=null;
  const prog=i=>{if(R.active)mark(i)};
  const job=(kind==='file'?analyzeFile(input,prog):analyzeUrl(input,kind==='site',prog)).then(x=>{rep=x}).catch(e=>{err=e});
  const timeout=new Promise(r=>setTimeout(r,17500));
  const minT=new Promise(r=>setTimeout(r,kind==='file'?3800:5200));
  await Promise.race([Promise.all([job,minT]),timeout]);
  if(!R.active)return;
  if(!rep){rep={kind,name,facts:[],uses:[],risks:[],tips:[]};rep.risks.push({sev:'med',t:err?T('تعذّر إكمال الفحص','Scan could not finish'):T('انتهت مهلة الفحص','Scan timed out'),d:err?String(err.message||err):T('الموقع بطيء جداً أو لا يستجيب.','The site is too slow or not responding.')})}
  mark(steps.length);await new Promise(r=>setTimeout(r,500));
  clearInterval(tick);R.active=false;rep.took=Math.max(1,Math.round((Date.now()-t0)/1000));finish(rep);save(rep);o.remove();K.scan.result(rep);if($('scan'))K.scan.paint()},
 result(R,fromHist){
  const old=$('scanres');if(old)old.remove();
  const L=LV[R.level],C=2*Math.PI*54;
  const d=document.createElement('div');d.id='scanres';d.className='sc-res '+R.level;d.dir=K.lang()==='ar'?'rtl':'ltr';d.style.setProperty('--lc',L.c);
  const sevName={crit:T('خطر شديد','Critical'),high:T('خطر','High'),med:T('متوسط','Medium'),low:T('منخفض','Low'),info:T('معلومة','Info')};
  const ico={crit:'1f6a8',high:'26a0',med:'26a0',low:'2139',info:'2139'};
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="rsBack">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('نتيجة الفحص','Scan result')+'</div><button class="sc-ib" id="rsShare">'+K.icon('share',22)+'</button></div><div class="sc-body">'
   +'<div class="sc-verdict"><div class="glow"></div><div class="gauge"><svg viewBox="0 0 120 120"><circle cx="60" cy="60" r="54" class="bg"/><circle cx="60" cy="60" r="54" class="fg" id="rsArc" stroke-dasharray="'+C.toFixed(1)+'" stroke-dashoffset="'+C.toFixed(1)+'"/></svg><div class="num"><b id="rsNum">0</b><small>/100</small></div></div>'
   +'<div class="vb">'+em(L.e,'v')+'<b>'+T(L.a,L.n)+'</b></div><p>'+T(L.s[0],L.s[1])+'</p><div class="vn" dir="ltr">'+esc(R.name.length>60?R.name.slice(0,58)+'…':R.name)+'</div></div>'
   +(R.grade?'<div class="sc-grade"><span class="g g'+R.grade+'">'+R.grade+'</span><div><b>'+T('درجة أمان الموقع','Site security grade')+'</b><small>'+T('حسب ترويسات الحماية القياسية','Based on standard security headers')+'</small></div></div>':'')
   +(R.risks.length?'<div class="sc-sect">'+T('المخاطر المكتشفة','Detected risks')+' <span class="n">'+R.risks.length+'</span></div>'+R.risks.map((r,i)=>'<div class="sc-risk s-'+r.sev+'" style="--i:'+i+'"><span class="ri">'+em(ico[r.sev]||'2139')+'</span><div><div class="rt"><b>'+esc(r.t)+'</b><i class="chip">'+sevName[r.sev]+'</i></div><p>'+esc(r.d).replace(/\n/g,'<br>')+'</p></div></div>').join(''):'<div class="sc-ok">'+em('2705')+'<div><b>'+T('لم نكتشف مخاطر','No risks detected')+'</b><small>'+T('هذا لا يعني ضماناً كاملاً، تحقق من المصدر دائماً.','That is not a full guarantee — always verify the source.')+'</small></div></div>')
   +(R.uses.length?'<div class="sc-sect">'+T(R.kind==='file'?'ماذا يستخدم على جهازك؟':'ماذا يستخدم؟',R.kind==='file'?'What does it use on your device?':'What does it use?')+'</div><div class="sc-card">'+R.uses.map(u=>'<div class="sc-use u-'+u.sev+'"><span class="ui">'+(K.icons[u.ic]?K.icon(u.ic,18):K.icon('info',18))+'</span><div><b>'+esc(u.t)+'</b><small>'+esc(u.d).replace(/\n/g,'<br>')+'</small></div>'+(u.sev!=='info'?'<i class="chip">'+sevName[u.sev]+'</i>':'')+'</div>').join('')+'</div>':'')
   +(R.facts.length?'<div class="sc-sect">'+T('تفاصيل','Details')+'</div><div class="sc-card">'+R.facts.map(f=>'<div class="sc-fact"><small>'+esc(f[0])+'</small><b dir="ltr">'+esc(f[1])+'</b></div>').join('')+'</div>':'')
   +(R.tls?'<div class="sc-sect">'+T('الشهادة','Certificate')+'</div><div class="sc-card"><div class="sc-fact"><small>'+T('صادرة من','Issuer')+'</small><b dir="ltr">'+esc(R.tls.iss)+'</b></div></div>':'')
   +(R.headers?'<div class="sc-sect">'+T('ترويسات الأمان','Security headers')+'</div><div class="sc-card">'+R.headers.map(x=>'<div class="sc-use '+(x.ok?'u-info':'u-low')+'"><span class="ui '+(x.ok?'okc':'no')+'">'+K.icon(x.ok?'circle-check':'triangle-alert',18)+'</span><div><b dir="ltr">'+x.n+'</b><small>'+x.d+'</small></div></div>').join('')+'</div>':'')
   +'<div class="sc-tip">'+em('1f4a1')+'<div>'+R.tips.map(t=>'<p>'+esc(t)+'</p>').join('')+(R.took?'<p class="took">'+T('استغرق الفحص '+R.took+' ثانية','Scan took '+R.took+' s')+'</p>':'')+'</div></div>'
   +'<div class="sc-btns">'
   +(R.kind==='file'&&R.hash?'<button data-a="vt">'+K.icon('shield-check',18)+'<span>'+T('افحص في VirusTotal','Check on VirusTotal')+'</span></button>':'')
   +(R.url?'<button data-a="gsb">'+K.icon('globe',18)+'<span>'+T('Google Safe Browsing','Google Safe Browsing')+'</span></button><button data-a="vtu">'+K.icon('shield-check',18)+'<span>VirusTotal</span></button>':'')
   +'<button data-a="ai">'+K.icon('sparkles',18)+'<span>'+T('اسأل بوصلة عن النتيجة','Ask Bawsala about it')+'</span></button><button data-a="copy">'+K.icon('copy',18)+'<span>'+T('نسخ التقرير','Copy report')+'</span></button><button class="p" data-a="again">'+K.icon('refresh-cw',18)+'<span>'+T('فحص جديد','New scan')+'</span></button></div></div>';
  document.body.appendChild(d);
  const arc=$('rsArc'),num=$('rsNum');requestAnimationFrame(()=>{arc.style.strokeDashoffset=(C*(1-R.score/100)).toFixed(1)});
  const t0=Date.now();const iv=setInterval(()=>{const p=Math.min(1,(Date.now()-t0)/1100);num.textContent=Math.round(R.score*(1-Math.pow(1-p,3)));if(p>=1)clearInterval(iv)},30);
  $('rsBack').onclick=()=>{clearInterval(iv);d.classList.add('out');setTimeout(()=>d.remove(),240)};
  $('rsShare').onclick=()=>K.native.share(textReport(R));
  d.querySelectorAll('.sc-btns button').forEach(b=>b.onclick=()=>{const a=b.dataset.a;
   if(a==='vt')K.native.open('https://www.virustotal.com/gui/file/'+R.hash);
   else if(a==='gsb')K.native.open('https://transparencyreport.google.com/safe-browsing/search?url='+encodeURIComponent(R.url));
   else if(a==='vtu')K.native.open('https://www.virustotal.com/gui/search/'+encodeURIComponent(R.url));
   else if(a==='copy')K.native.copy(textReport(R));
   else if(a==='again'){d.remove();if(!$('scan'))K.scan.open()}
   else if(a==='ai'){d.remove();const s=$('scan');s&&s.remove();K.home.newChat();const ta=$('ta');ta.value=T('اشرح لي بالتفصيل نتيجة فحص الأمان التالية وماذا يجب أن أفعل:\n\n','Explain in detail the following security scan result and what I should do:\n\n')+textReport(R);ta.dispatchEvent(new Event('input'));K.chat.send()}})}
};
})();
