/* Learning agent page: runs in a visible foreground service on the phone, only for the pages you list (robots.txt respected) */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const A=window.Android;
const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
function paint(st){
 const el=$('agBox');if(!el)return;st=st||{};const tot=st.total||0,p=tot?Math.round((st.done||0)/tot*100):0;
 const lab={running:T('يعمل الآن…','Running…'),done:T('تم الشغل بنجاح ✅','Finished ✅'),stopped:T('أُوقف','Stopped'),error:T('خطأ','Error')}[st.state]||T('جاهز','Ready');
 el.innerHTML='<div class="ag-st"><b>'+lab+'</b><span>'+(st.done||0)+' / '+tot+'</span></div><div class="ag-bar"><i style="width:'+p+'%"></i></div>'
  +'<div class="ag-n"><div><b>'+(st.saved||0)+'</b><small>'+T('صفحة محفوظة','pages saved')+'</small></div><div><b>'+(st.tokens||0).toLocaleString('en')+'</b><small>'+T('توكن (تقدير)','tokens (est.)')+'</small></div></div>';
 const run=st.state==='running';$('agGo').classList.toggle('hidden',run);$('agStop').classList.toggle('hidden',!run)}
window.__agent=paint;
K.agent={
 open(){
  const old=$('agent');if(old)old.remove();
  const d=document.createElement('div');d.id='agent';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="agB">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('وكيل التعلّم','Learning agent')+'</div><span class="sc-ib ph"></span></div>'
   +'<div class="sc-body"><div class="ag-card"><b>'+T('كيف يعمل','How it works')+'</b><p>'+T('يعمل في الخلفية بإشعار ظاهر، ويقرأ فقط الصفحات التي تكتبها أدناه (ويحترم robots.txt)، ويحفظ النص في مجلد التطبيق الخاص، ثم يرسل لك إشعار «تم الشغل بنجاح». أضف صفحاتك أو صفحات تسمح بإعادة استخدامها فقط.','Runs in the background with a visible notification, reads only the pages you list (robots.txt respected), stores text in the app\'s private folder, then notifies you. Add only pages you own or that allow reuse.')+'</p></div>'
   +'<div class="ag-card"><label>'+T('الروابط (رابط في كل سطر)','URLs (one per line)')+'</label><textarea id="agUrls" rows="6" dir="ltr" placeholder="https://example.com/page1">'+esc(K.pref.get('agent_urls',''))+'</textarea>'
   +'<label>'+T('التأخر بين الصفحات (ثوانٍ)','Delay between pages (s)')+'</label><input id="agDelay" type="number" min="2" value="'+esc(K.pref.get('agent_delay','3'))+'"></div>'
   +'<div id="agBox" class="ag-card"></div>'
   +'<button class="pbtn" id="agGo">'+T('ابدأ الوكيل','Start agent')+'</button><button class="pcan hidden" id="agStop">'+T('إيقاف','Stop')+'</button>'
   +'<p class="ag-note">'+T('لا يطلب صلاحية مشرف الجهاز ولا يثبّت تطبيقات ولا يلمس تطبيقاتك الأخرى. قد يطلب أندرويد منك السماح بالإشعارات، وإن أوقف النظام التطبيق لتوفير البطارية فاستثنِه من تحسين البطارية.','No device-admin, no app installs, other apps untouched. Android may ask for notification permission; exempt the app from battery optimisation if the system stops it.')+'</p></div>';
  $('agB').onclick=()=>d.remove();
  try{paint(JSON.parse((A&&A.agentStatus&&A.agentStatus())||'{}'))}catch(e){paint({})}
  $('agGo').onclick=()=>{const urls=$('agUrls').value.split('\n').map(x=>x.trim()).filter(x=>/^https?:\/\//.test(x));
   if(!urls.length){K.toast(T('أضف رابطاً واحداً على الأقل','Add at least one URL'));return}
   K.pref.set('agent_urls',$('agUrls').value);K.pref.set('agent_delay',$('agDelay').value||'3');
   if(A&&A.agentStart){A.agentStart(JSON.stringify({urls:urls,delay:+$('agDelay').value||3}));paint({state:'running',done:0,total:urls.length,saved:0,tokens:0});K.toast(T('بدأ الوكيل في الخلفية','Agent started'))}
   else K.toast(T('متاح داخل التطبيق فقط','Available in the app only'))};
  $('agStop').onclick=()=>{A&&A.agentStop&&A.agentStop();K.toast(T('جارٍ الإيقاف…','Stopping…'))}}
};
})();
