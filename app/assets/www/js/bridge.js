/* Bridge to the native Android shell (window.Android). In a plain browser mock.js provides the same API. */
window.K=window.K||{};
(function(){
const A=window.Android;const cbs={};let nid=1;
window.__cb=function(id,json){const f=cbs[id];if(!f)return;let d;try{d=JSON.parse(json)}catch(e){d=json}f(d,id)};
function reg(fn,once){const id=nid++;cbs[id]=function(d){if(once!==false)delete cbs[id];fn(d)};return id}
K.native={
 real:!!A,
 getPref(k,d){if(A)return A.getPref(k,d);const v=localStorage.getItem('p_'+k);return v===null?d:v},
 setPref(k,v){if(A)A.setPref(k,String(v));else localStorage.setItem('p_'+k,String(v))},
 hasKey(){return A?A.hasKey():(K.native.getPref('api_key','')!=='')},
 chat(msgs,think,ctx,cb){
   if(!A)return K.mock.chat(msgs,think,ctx,cb);
   const id=reg(function(d){cb(d);if(d.t==='done'||d.t==='err')delete cbs[id]},false);
   A.chat(id,JSON.stringify(msgs),!!think,ctx||'');
   return {cancel(){A.cancel(id);delete cbs[id]}};
 },
 search(q){return new Promise(r=>{if(!A)return K.mock.search(q).then(r);const id=reg(r);A.search(id,q)})},
 test(){return new Promise(r=>{if(!A)return setTimeout(()=>r(null),700);const id=reg(r);A.test(id)})},
 login(e,p){return A?JSON.parse(A.login(e,p)):K.mock.login(e,p)},
 signup(n,e,p,p2){return A?JSON.parse(A.signup(n,e,p,p2)):K.mock.signup(n,e,p,p2)},
 google(){return new Promise(r=>{if(!A)return setTimeout(()=>r({name:'Bawsala',email:'karrar@gmail.com'}),900);const id=reg(r);A.google(id)})},
 googleAccounts(){try{return A&&A.googleAccounts?JSON.parse(A.googleAccounts()):K.mock.googleAccounts()}catch(e){return[]}},
 hasAccount(e){return A?A.hasAccount(e)==='1':K.mock.has(e)},
 resetPassword(e,p,p2){return A?JSON.parse(A.resetPassword(e,p,p2)):K.mock.reset(e,p,p2)},
 guest(){A?A.guest():(K.native.setPref('logged','1'),K.native.setPref('name','مستخدم'),K.native.setPref('user_email',''))},
 logout(){A?A.logout():(K.native.setPref('logged','0'),K.native.setPref('user_email',''))},
 deleteAccount(email){A?A.deleteAccount(email):0},
 pickImages(){return new Promise(r=>{if(!A)return r(K.mock.images(2));const id=reg(r);A.pickImages(id)})},
 camera(){return new Promise(r=>{if(!A)return r(K.mock.images(1));const id=reg(r);A.camera(id)})},
 pickFile(){return new Promise(r=>{if(!A)return r(window.__mockFile||{name:'notes.txt',size:11,b64:btoa('hello world')});const id=reg(r);A.pickFile(id)})},
 gallery(){return new Promise(r=>{if(!A)return r(K.mock.gallery());const id=reg(r);A.gallery(id)})},
 imageData(uri){return new Promise(r=>{if(!A)return r({b64:''});const id=reg(r);A.imageData(id,uri)})},
 voice(){return new Promise(r=>{if(!A)return r('');const id=reg(r);A.voice(id)})},
 speak(t){if(A)A.speak(t);else if(window.__ttsDone)setTimeout(window.__ttsDone,2600)},stopSpeak(){if(A)A.stopSpeak()},
 /* continuous speech recognition with live level (voice mode). handler gets {t:'rms'|'partial'|'final'|'err'} */
 voiceLive(handler){
   if(!A)return K.mock.voiceLive(handler);
   const id=reg(handler,false);A.voiceStart(id);
   return {stop(){A.voiceStop();delete cbs[id]}};
 },
 http(method,url,headers,body){return new Promise(r=>{if(!A||!A.http)return r(K.mock.http(method,url,body));const id=reg(r);A.http(id,method,url,JSON.stringify(headers||{}),body||'')})},
 diag(){return new Promise(r=>{if(!A||!A.diag)return r([{name:'demo',model:'sample',ok:true,ms:120},{name:'pollinations-llama',model:'llama',ok:false,ms:900,msg:'HTTP 403: sample error'}]);const id=reg(r);A.diag(id)})},
 screenshot(){return new Promise(r=>{if(!A||!A.screenshot)return r(K.mock.images(1)[0]);const id=reg(r);A.screenshot(id)})},
 fetchImage(url,bearer,body){return new Promise(r=>{if(!A||!A.fetchImage)return r(null);const id=reg(r);A.fetchImage(id,url,bearer||'',body||'')})},
 scanUrl(url,deep){return new Promise(r=>{if(!A||!A.scanUrl)return r(K.mock.scanUrl(url,deep));const id=reg(r);A.scanUrl(id,url,!!deep)})},
 saveFile(name,mime,b64){return new Promise(async r=>{
  if(A&&A.saveFile){let res=A.saveFile(name,mime,b64);
   if(String(res).indexOf('err')===0&&A.saveAs){res=await new Promise(x=>{const id=reg(x);A.saveAs(id,name,mime,b64)})}
   if(String(res).indexOf('ok:')===0){const pr=res.slice(3).split('|'),ar=K.lang()==='ar';K.snack(ar?'تم الحفظ: '+(pr[0].split('/').slice(-2).join('/')||name):'Saved: '+(pr[0].split('/').slice(-2).join('/')||name),ar?'فتح':'Open',()=>{if(pr[1])A.openUri(pr[1],mime)})}
   r(res);return}
  try{const bin=atob(b64),u=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([u],{type:mime}));a.download=name;document.body.appendChild(a);a.click();setTimeout(()=>a.remove(),500);r('ok:'+name+'|')}catch(e){r('err')}})},
 openUri(uri,mime){if(A&&A.openUri&&uri)A.openUri(uri,mime||'text/html')},
 ttsVoices(){return new Promise(r=>{if(!A||!A.ttsVoices)return r([{name:'ar-xa-x-arz-local',locale:'ar-EG'},{name:'ar-xa-x-ard-local',locale:'ar-SA'},{name:'en-us-x-tpd-local',locale:'en-US'}]);const id=reg(r);A.ttsVoices(id)})},
 copy(t){if(A)A.copy(t);else{try{navigator.clipboard.writeText(t).catch(function(){})}catch(e){}}K.toast(K.t('copied'))},
 share(t){if(A)A.share(t)},
 open(u){if(A)A.open(u);else window.open(u,'_blank')},
 haptic(){if(A&&K.pref.bool('haptic',true))A.haptic()},
 setBars(bg,darkIcons){if(A)A.setBars(bg,darkIcons)},
 exit(){if(A)A.exitApp()},
 model(){return A?A.deviceModel():'Browser'},
 defaults(){return A?JSON.parse(A.defaults()):{}}
};
})();
