/* Developer mode: a LOCAL-ONLY password the user sets themselves on their own device. It is never sent to
   Claude, to any server, or stored anywhere but this device (only its SHA-256 hash is kept via K.pref).
   "Developer mode" unlocks nothing new technically — it only shows extra diagnostic info already in the app
   (version, agent status) under a clear badge, and answers "من هو المطوّر" chat messages locally instead of
   sending them to the AI model. It can NOT edit this app's code or push an update by itself: that still
   requires a human (you or the real developer) to build and ship a new APK, same as every update so far. */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
async function sha256(text){
 const buf=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text));
 return Array.from(new Uint8Array(buf)).map(b=>b.toString(16).padStart(2,'0')).join('')}
const TRIGGER=/^\s*(انا|أنا)?\s*(مطور|مطوّر)\s*(التطبيق|البرنامج)?\s*\??\s*$/i;
K.devmode={
 isOn(){return K.pref.bool('dev_mode_on',false)},
 hasPassword(){return !!K.pref.get('dev_pw_hash','')},
 async setPassword(){
  const p1=await K.ui.prompt({title:T('اختر كلمة سر وضع المطوّر (تُحفظ على جهازك فقط)','Choose a developer-mode password (kept on this device only)'),secret:true,hint:T('كلمة السر','Password')});
  if(!p1)return false;
  const p2=await K.ui.prompt({title:T('أكّد كلمة السر','Confirm the password'),secret:true,hint:T('أعد كتابتها','Type it again')});
  if(p1!==p2){K.toast(T('كلمتا السر غير متطابقتين','Passwords did not match'));return false}
  K.pref.set('dev_pw_hash',await sha256(p1));K.toast(T('تم حفظ كلمة سر وضع المطوّر على جهازك','Developer-mode password saved on this device'));
  return true},
 async verify(){
  if(!K.devmode.hasPassword()){const ok=await K.devmode.setPassword();if(!ok)return false}
  const p=await K.ui.prompt({title:T('كلمة سر وضع المطوّر','Developer-mode password'),secret:true,hint:T('أدخلها للتأكيد','Enter it to confirm')});
  if(p==null)return false;
  const h=await sha256(p);
  if(h!==K.pref.get('dev_pw_hash','')){K.toast(T('كلمة سر غير صحيحة','Wrong password'));return false}
  K.pref.set('dev_mode_on','1');return true},
 turnOff(){K.pref.set('dev_mode_on','')},
 /** called by chat.js before sending to the model; returns a local reply string, or null to send normally. */
 async localAnswer(text){
  if(!TRIGGER.test(text||''))return null;
  const ok=await K.devmode.verify();
  if(!ok)return T('## 🔒 غير مؤكَّد\n\nلم يُثبَت أنك المطوّر. أي شخص آخر يرى نفس هذه الرسالة، ولا تُرسَل أي كلمة سر أو إثبات إلى الذكاء الاصطناعي أو إلى أي خادم — التحقق يحصل على جهازك فقط.',
   '## 🔒 Not verified\n\nDeveloper status was not confirmed. No password or proof is ever sent to the AI or to any server — verification happens only on this device.');
  return T('## ✅ تم التأكيد محلياً على جهازك\n\nمرحباً أيها المطوّر. وضع المطوّر الآن يعرض معلومات تشخيصية إضافية فقط (الإصدار 3.0، حالة الوكيل)، ولا يعطي الذكاء الاصطناعي أي صلاحية جديدة بالفعل: لا تعديل كود، ولا نشر تحديث من داخل الجهاز — هذا يبقى يحدث خارج الجهاز (بين المطوّر الحقيقي وأداة البناء)، تماماً كما حدث مع هذا الإصدار نفسه.\n\nلإدارة وضع المطوّر: الإعدادات ← حول ← وضع المطوّر.',
   '## ✅ Verified locally on this device\n\nHello, developer. Developer mode now only shows extra diagnostic info (version 3.0, agent status); it does not grant the AI any new real power — no code edits, no pushing an update from inside the device. That still happens outside the device, same as this very release.\n\nManage developer mode from Settings → About → Developer mode.')}
};
})();
