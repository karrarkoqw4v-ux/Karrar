/* Google services (Drive, Gmail, Calendar, Docs, Sheets): sign-in with extra scopes in the browser (PKCE), tokens kept on the device, read-only views + "ask the AI" */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const CID='141595198139-dk1njht8r2j8h184e8053d5m070d1ijp.apps.googleusercontent.com';
const A='https://www.googleapis.com/auth/';
const SV={
 drive:{n:'Google Drive',brand:'googledrive',scope:A+'drive.readonly',api:'drive.googleapis.com'},
 gmail:{n:'Gmail',brand:'gmail',scope:A+'gmail.readonly',api:'gmail.googleapis.com'},
 cal:{n:'Google Calendar',brand:'googlecalendar',scope:A+'calendar.readonly',api:'calendar-json.googleapis.com'},
 docs:{n:'Google Docs',brand:'googledocs',scope:A+'drive.readonly '+A+'documents.readonly',api:'docs.googleapis.com'},
 sheets:{n:'Google Sheets',brand:'googlesheets',scope:A+'drive.readonly '+A+'spreadsheets.readonly',api:'sheets.googleapis.com'}};
const load=k=>{try{return JSON.parse(K.pref.get('g_'+k,'')||'null')}catch(e){return null}};
const save=(k,v)=>K.pref.set('g_'+k,v?JSON.stringify(v):'');
K.google={
 SV,
 connected(k){const t=load(k);return !!(t&&(t.rt||t.at))},
 async connect(k){
  const sv=SV[k];const r=await K.native.googleAuth(sv.scope);
  if(!r||r.err){if(!r||r.err!=='cancel')K.ui.info(T('تعذّر الربط','Could not connect'),String((r&&r.err)||'')+'\n'+T('تأكد أن تطبيقك في Google Cloud يسمح بهذه الصلاحية (Test users) وأن واجهة '+sv.api+' مفعّلة.','Make sure your Google Cloud app allows this scope (Test users) and that '+sv.api+' is enabled.'));return false}
  if(!r.access_token){K.toast(T('لم تصل صلاحية الوصول','No access token'));return false}
  save(k,{at:r.access_token,rt:r.refresh_token||'',exp:Date.now()+(r.expires_in||3600)*1000-60000,email:r.email});
  K.toast(T('تم ربط ','Connected ')+sv.n+' · '+(r.email||''));return true},
 disconnect(k){save(k,null)},
 async token(k){
  const t=load(k);if(!t)return null;
  if(t.at&&Date.now()<t.exp)return t.at;
  if(!t.rt)return null;
  const r=await K.native.http('POST','https://oauth2.googleapis.com/token',{'Content-Type':'application/x-www-form-urlencoded'},'client_id='+encodeURIComponent(CID)+'&grant_type=refresh_token&refresh_token='+encodeURIComponent(t.rt));
  let d={};try{d=JSON.parse(r.body)}catch(e){}
  if(!d.access_token)return null;
  t.at=d.access_token;t.exp=Date.now()+(d.expires_in||3600)*1000-60000;save(k,t);return t.at},
 async api(k,url){
  const at=await K.google.token(k);if(!at)return {err:T('انتهت الجلسة — أعد الربط','Session expired — reconnect')};
  const r=await K.native.http('GET',url,{'Authorization':'Bearer '+at},'');
  let d={};try{d=JSON.parse(r.body)}catch(e){}
  if(r.code>=400||d.error){const m=d.error&&(d.error.message||d.error)||('HTTP '+r.code);return {err:String(m)+(r.code===403?'\n'+T('فعّل الواجهة '+SV[k].api+' في Google Cloud ثم أعد المحاولة.','Enable the '+SV[k].api+' API in Google Cloud and retry.'):'')}}
  return d},
 async list(k){
  const q=encodeURIComponent;
  if(k==='drive'){const d=await K.google.api(k,'https://www.googleapis.com/drive/v3/files?pageSize=25&orderBy=modifiedTime%20desc&fields=files(id,name,mimeType,modifiedTime,webViewLink)');if(d.err)return d;return (d.files||[]).map(f=>({t:f.name,s:(f.modifiedTime||'').slice(0,10)+' · '+(f.mimeType||'').split('.').pop().replace('application/',''),u:f.webViewLink}))}
  if(k==='docs'||k==='sheets'){const mt=k==='docs'?'application/vnd.google-apps.document':'application/vnd.google-apps.spreadsheet';const d=await K.google.api(k,'https://www.googleapis.com/drive/v3/files?pageSize=25&orderBy=modifiedTime%20desc&q='+q("mimeType='"+mt+"' and trashed=false")+'&fields=files(id,name,modifiedTime,webViewLink)');if(d.err)return d;return (d.files||[]).map(f=>({t:f.name,s:(f.modifiedTime||'').slice(0,10),u:f.webViewLink}))}
  if(k==='cal'){const d=await K.google.api(k,'https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=20&singleEvents=true&orderBy=startTime&timeMin='+q(new Date().toISOString()));if(d.err)return d;return (d.items||[]).map(e=>({t:e.summary||'—',s:((e.start&&(e.start.dateTime||e.start.date))||'').replace('T',' ').slice(0,16),u:e.htmlLink}))}
  if(k==='gmail'){const d=await K.google.api(k,'https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=12');if(d.err)return d;const out=[];
   for(const m of (d.messages||[])){const x=await K.google.api(k,'https://gmail.googleapis.com/gmail/v1/users/me/messages/'+m.id+'?format=metadata&metadataHeaders=Subject&metadataHeaders=From');if(x.err)continue;const h=n=>((x.payload&&x.payload.headers||[]).find(y=>y.name===n)||{}).value||'';out.push({t:h('Subject')||'—',s:h('From').replace(/<.*>/,'').trim()+' · '+(x.snippet||'').slice(0,70),u:'https://mail.google.com/mail/u/0/#inbox/'+m.id})}return out}
  return []},
 async open(k){
  if(!K.google.connected(k)){const go=await K.ui.confirm(T('سيفتح المتصفح لتسجيل الدخول بحساب جوجل ومنح «بوصلة» صلاحية قراءة '+SV[k].n+' فقط. هل تريد المتابعة؟','The browser opens so you can sign in with Google and let Bawsala read your '+SV[k].n+' (read-only). Continue?'));if(!go)return;if(!await K.google.connect(k))return}
  const old=$('gsv');if(old)old.remove();
  const d=document.createElement('div');d.id='gsv';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);
  d.innerHTML='<div class="sc-top"><button class="sc-ib" id="gsvB">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+SV[k].n+'</div><button class="sc-ib" id="gsvX" title="'+T('فصل','Disconnect')+'">'+K.icon('log-out',20)+'</button></div><div class="sc-body"><div class="note" style="padding:20px">'+T('جارٍ التحميل…','Loading…')+'</div></div>';
  $('gsvB').onclick=()=>d.remove();
  $('gsvX').onclick=async()=>{if(await K.ui.confirm(T('فصل '+SV[k].n+'؟','Disconnect '+SV[k].n+'?'),true)){K.google.disconnect(k);d.remove();if(K.settings&&K.settings.rerender)K.settings.rerender()}};
  const items=await K.google.list(k);const body=d.querySelector('.sc-body');
  if(items.err){body.innerHTML='<div class="note" style="padding:20px;white-space:pre-wrap">⚠ '+esc(items.err)+'</div>';return}
  if(!items.length){body.innerHTML='<div class="note" style="padding:20px">'+T('لا توجد عناصر.','Nothing here.')+'</div>';return}
  body.innerHTML='<div class="st-act"><button id="gsvAI" style="--c:#4d6bfe">'+K.icon('sparkles',22)+'<span>'+T('اسأل الذكاء عن هذه القائمة','Ask the AI about this list')+'</span></button></div>'
   +items.map((x,i)=>'<button class="sc-h gsv-i" data-i="'+i+'" style="width:100%;text-align:start"><span class="t"><b>'+esc(x.t)+'</b><small>'+esc(x.s)+'</small></span></button>').join('');
  body.querySelectorAll('.gsv-i').forEach(b=>b.onclick=()=>{const u=items[+b.dataset.i].u;if(u)K.native.open(u)});
  $('gsvAI').onclick=()=>{d.remove();const txt=items.map(x=>'- '+x.t+' ('+x.s+')').join('\n');
   if(K.settings&&K.settings.close)try{K.settings.close()}catch(e){}
   const ta=$('ta');if(ta){ta.value=T('هذه بياناتي من '+SV[k].n+'. لخّصها ونبّهني لأهم ما فيها:\n','This is my '+SV[k].n+' data. Summarize it and flag what matters:\n')+txt;ta.dispatchEvent(new Event('input'));ta.focus()}}
 }
};
})();
