/* "Open <app>" commands: handled locally on the device (launch intent only), never sent to the AI model.
   This only starts another app, the same as tapping its icon; it cannot read, type into, or control it after. */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const RE=/^\s*(?:افتح|شغّل|شغل|open|launch)\s+(?:لي\s+|تطبيق\s+|برنامج\s+|app\s+)?(.+?)\s*[.!؟?]?\s*$/i;
K.openapp={
 match(text){const m=RE.exec(text||'');return m?m[1].trim():null},
 /** returns a markdown reply string, or null if this text isn't an "open app" command. */
 async run(text){
  const name=K.openapp.match(text);
  if(!name)return null;
  if(!window.Android||!window.Android.openApp)
   return T('لا أقدر أفتح تطبيقات من هذا المتصفح، فقط داخل تطبيق بوصلة نفسه.','I can only open apps from inside the Bawsala app itself, not this browser.');
  let r={ok:false,reason:'?'};
  try{r=JSON.parse(await window.Android.openApp(name))}catch(e){}
  if(r.ok)return T('✅ فتحت **'+r.label+'**.','✅ Opened **'+r.label+'**.');
  if(r.reason==='ambiguous')
   return T('في أكثر من تطبيق قريب من "'+name+'": '+r.options.join('، ')+'. حدّد الاسم بدقة أكثر.','More than one app matches "'+name+'": '+r.options.join(', ')+'. Be more specific.');
  return T('ما لقيت تطبيقاً اسمه "'+name+'" مثبَّتاً على جهازك.','No installed app named "'+name+'" was found.');
 }
};
})();
