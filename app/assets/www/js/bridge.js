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
 google(){return new Promise(r=>{if(!A)return setTimeout(()=>r({name:'Karrar',email:'karrar@gmail.com'}),900);const id=reg(r);A.google(id)})},
 guest(){A?A.guest():(K.native.setPref('logged','1'),K.native.setPref('name','مستخدم'),K.native.setPref('user_email',''))},
 logout(){A?A.logout():(K.native.setPref('logged','0'),K.native.setPref('user_email',''))},
 deleteAccount(email){A?A.deleteAccount(email):0},
 pickImages(){return new Promise(r=>{if(!A)return r(K.mock.images(2));const id=reg(r);A.pickImages(id)})},
 camera(){return new Promise(r=>{if(!A)return r(K.mock.images(1));const id=reg(r);A.camera(id)})},
 pickFile(){return new Promise(r=>{if(!A)return r({name:'notes.txt',text:'hello'});const id=reg(r);A.pickFile(id)})},
 gallery(){return new Promise(r=>{if(!A)return r(K.mock.gallery());const id=reg(r);A.gallery(id)})},
 imageData(uri){return new Promise(r=>{if(!A)return r({b64:''});const id=reg(r);A.imageData(id,uri)})},
 voice(){return new Promise(r=>{if(!A)return r('');const id=reg(r);A.voice(id)})},
 speak(t){if(A)A.speak(t)},stopSpeak(){if(A)A.stopSpeak()},
 copy(t){if(A)A.copy(t);else{try{navigator.clipboard.writeText(t)}catch(e){}}K.toast(K.t('copied'))},
 share(t){if(A)A.share(t)},
 open(u){if(A)A.open(u);else window.open(u,'_blank')},
 haptic(){if(A&&K.pref.bool('haptic',true))A.haptic()},
 setBars(bg,darkIcons){if(A)A.setBars(bg,darkIcons)},
 exit(){if(A)A.exitApp()},
 model(){return A?A.deviceModel():'Browser'},
 defaults(){return A?JSON.parse(A.defaults()):{}}
};
})();
