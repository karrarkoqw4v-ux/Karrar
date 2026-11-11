/* Local device commands, handled on the device before the text ever reaches the AI model:
   - open an app by name (English/Arabic/any language label, with common aliases)
   - set an alarm / timer
   - add a calendar event ("موعد"/"ذكّرني")
   - hand a message off to WhatsApp (opens its contact picker with the text ready; the user still taps Send)
   None of this reads, types into, or controls another app's content. */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;

/* Arabic/other-spelling -> the English label most Android app listings actually use. */
const ALIASES={
 'واتساب':'WhatsApp','واتس اب':'WhatsApp','واتس':'WhatsApp',
 'يوتيوب':'YouTube','انستغرام':'Instagram','انستقرام':'Instagram','فيسبوك':'Facebook','فيس بوك':'Facebook',
 'تيك توك':'TikTok','تلغرام':'Telegram','تيليجرام':'Telegram','سناب شات':'Snapchat','سناب':'Snapchat',
 'الكاميرا':'Camera','كاميرا':'Camera','الاعدادات':'Settings','الإعدادات':'Settings','اعدادات':'Settings',
 'المعرض':'Gallery','معرض الصور':'Gallery','الرسائل':'Messages','رسائل':'Messages','الهاتف':'Phone','هاتف':'Phone',
 'المتصفح':'Chrome','متصفح':'Chrome','خرائط':'Maps','خرائط جوجل':'Maps','جيميل':'Gmail','البريد':'Gmail',
 'الالة الحاسبة':'Calculator','الآلة الحاسبة':'Calculator','حاسبة':'Calculator','الساعة':'Clock','الملفات':'Files',
 'بلاي ستور':'Play Store','متجر':'Play Store','سبوتيفاي':'Spotify','درايف':'Drive'
};
function resolveNames(name){
 const n=name.trim();
 const out=[n];
 if(ALIASES[n])out.unshift(ALIASES[n]);
 const noAl=n.replace(/^(ال)/,'');
 if(ALIASES[noAl])out.unshift(ALIASES[noAl]);
 return [...new Set(out)];
}

const OPEN_RE=/^\s*(?:افتح|شغّل|شغل|open|launch)\s+(?:لي\s+|تطبيق\s+|برنامج\s+|app\s+)?(.+?)\s*[.!؟?]?\s*$/i;
const ALARM_RE=/(?:ضبط\s+)?منبّ?ه\s*(?:الساعة|على|لل?ساعة)?\s*(\d{1,2})(?::(\d{2}))?\s*(ص|صباحا|صباحاً|م|مساء|مساءً|am|pm)?/i;
const TIMER_RE=/(?:مؤقت|تايمر|timer)\s*(\d+)\s*(ثانية|ثواني|دقيقة|دقائق|ساعة|ساعات|sec|second|min|minute|hour)?/i;
const EVENT_RE=/^\s*(?:ذكّرني|ذكرني|موعد|اجتماع|appointment|remind me)[\s:ب]+(.+)$/i;
const WA_RE=/^\s*(?:راسل|ارسل|رسالة)\s*(?:ب|على|في)?\s*(واتساب|whatsapp)\s*[:ـ-]?\s*(.+)$/i;

function to24h(h,m,suf){
 h=+h;m=m?+m:0;
 if(/^(م|مساء|مساءً|pm)$/i.test(suf||'')&&h<12)h+=12;
 if(/^(ص|صباحا|صباحاً|am)$/i.test(suf||'')&&h===12)h=0;
 return {h,m}}

K.openapp={
 match(text){const m=OPEN_RE.exec(text||'');return m?m[1].trim():null},
 async run(text){
  const name=K.openapp.match(text);
  if(!name)return null;
  if(!window.Android||!window.Android.openApp)
   return T('لا أقدر أفتح تطبيقات من هذا المتصفح، فقط داخل تطبيق بوصلة نفسه.','I can only open apps from inside the Bawsala app itself, not this browser.');
  const tries=resolveNames(name);let r={ok:false,reason:'not-found'};
  for(const t of tries){try{r=JSON.parse(await window.Android.openApp(t))}catch(e){r={ok:false,reason:String(e)}}if(r.ok)break}
  if(r.ok)return T('✅ فتحت **'+r.label+'**.','✅ Opened **'+r.label+'**.');
  if(r.reason==='ambiguous')
   return T('في أكثر من تطبيق قريب من "'+name+'": '+r.options.join('، ')+'. حدّد الاسم بدقة أكثر.','More than one app matches "'+name+'": '+r.options.join(', ')+'. Be more specific.');
  return T('ما لقيت تطبيقاً اسمه "'+name+'" مثبَّتاً على جهازك.','No installed app named "'+name+'" was found.');
 }
};

K.commands={
 async run(text){
  if(!window.Android)return null;
  text=(text||'').trim();if(!text)return null;

  let m=ALARM_RE.exec(text);
  if(m){const {h,m:mi}=to24h(m[1],m[2],m[3]);const r=await window.Android.setAlarm(h,mi,text.slice(0,40));
   return r==='ok'?T('⏰ فتحت لك شاشة ضبط المنبّه على الساعة '+String(h).padStart(2,'0')+':'+String(mi).padStart(2,'0')+'، أكّده من هناك.','⏰ Opened the alarm screen for '+String(h).padStart(2,'0')+':'+String(mi).padStart(2,'0')+'; confirm it there.'):T('تعذّر فتح شاشة المنبّه: '+r,'Could not open the alarm screen: '+r)}

  m=TIMER_RE.exec(text);
  if(m){let n=+m[1];const unit=(m[2]||'دقيقة').toLowerCase();
   const secs=/ساع|hour/.test(unit)?n*3600:/دقيق|min/.test(unit)?n*60:n;
   const r=await window.Android.setTimer(secs,text.slice(0,40));
   return r==='ok'?T('⏱️ بدأت مؤقّتاً لمدة '+m[1]+' '+(m[2]||'دقيقة')+'.','⏱️ Started a '+m[1]+' '+(m[2]||'minute')+' timer.'):T('تعذّر ضبط المؤقّت: '+r,'Could not set the timer: '+r)}

  m=WA_RE.exec(text);
  if(m){const msg=m[2].trim();const r=await window.Android.shareToApp('com.whatsapp',msg);
   return r==='ok'?T('📨 فتحت واتساب مع رسالتك جاهزة؛ اختر الشخص واضغط إرسال بنفسك.','📨 Opened WhatsApp with your message ready; pick the contact and tap Send yourself.')
    :r==='err:not-installed'?T('واتساب غير مثبَّت على جهازك.','WhatsApp is not installed on this device.'):T('تعذّر الفتح: '+r,'Could not open: '+r)}

  m=EVENT_RE.exec(text);
  if(m){const title=m[1].trim();const start=Date.now()+60*60*1000;
   const r=await window.Android.addCalendarEvent(title,start);
   return r==='ok'?T('📅 فتحت لك شاشة إضافة موعد بعد ساعة من الآن بعنوان «'+title+'»، عدّل الوقت وأكّده من هناك.','📅 Opened a new calendar event an hour from now titled "'+title+'"; adjust the time and confirm it there.'):T('تعذّر فتح شاشة الموعد: '+r,'Could not open the event screen: '+r)}

  return null;
 }
};
})();
