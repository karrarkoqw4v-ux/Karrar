/* Dialogs (bottom sheets), lightbox, helpers */
window.K=window.K||{};
(function(){
const ov=()=>document.getElementById('overlay');
K.ui={stack:[],
 close(){const t=this.stack.pop();if(t){t.remove()}return !!t},
 _open(node,onClose){const w=document.createElement('div');w.className='ui-layer';const b=document.createElement('div');b.className='backdrop';w.appendChild(b);w.appendChild(node);
  const done=v=>{if(w.parentNode){const i=K.ui.stack.indexOf(w);if(i>=0)K.ui.stack.splice(i,1);w.remove()}onClose&&onClose(v)};
  w.remove=(function(orig){return function(){orig.call(w);onClose&&onClose(null)}})(w.remove);
  b.onclick=()=>K.ui.close();ov().appendChild(w);this.stack.push(w);w._done=done;return w},
 prompt(o){return new Promise(res=>{
  const d=document.createElement('div');d.className='dlg';
  d.innerHTML='<h3></h3>'+(o.multi?'<textarea></textarea>':'<input '+(o.secret?'type="password"':'type="text"')+' autocomplete="off">')+'<div class="btns"><button class="c"></button><button class="primary s"></button></div>';
  d.querySelector('h3').textContent=o.title||'';const inp=d.querySelector(o.multi?'textarea':'input');inp.value=o.value||'';inp.placeholder=o.hint||'';
  d.querySelector('.c').textContent=K.t('cancel');d.querySelector('.s').textContent=K.t('save');
  let val=null;const w=this._open(d,()=>res(val));
  const fin=v=>{val=v;const i=K.ui.stack.indexOf(w);if(i>=0)K.ui.stack.splice(i,1);w.parentNode&&w.parentNode.removeChild(w);res(val)};
  d.querySelector('.c').onclick=()=>fin(null);d.querySelector('.s').onclick=()=>fin(inp.value.trim());
  w.remove=function(){fin(null)};setTimeout(()=>inp.focus(),120)})},
 choose(title,items,sel){return new Promise(res=>{
  const d=document.createElement('div');d.className='dlg';d.innerHTML='<h3></h3><div class="opts"></div>';d.querySelector('h3').textContent=title;
  const list=d.querySelector('.opts');let fin;
  items.forEach((it,i)=>{const b=document.createElement('button');b.className='opt'+(i===sel?' sel':'');b.innerHTML='<span></span>'+(i===sel?K.icon('check',20):'');b.firstChild.textContent=it;b.onclick=()=>fin(i);list.appendChild(b)});
  const w=this._open(d);fin=v=>{const k=K.ui.stack.indexOf(w);if(k>=0)K.ui.stack.splice(k,1);w.parentNode&&w.parentNode.removeChild(w);res(v)};w.remove=function(){fin(null)}})},
 confirm(msg,danger){return new Promise(res=>{
  const d=document.createElement('div');d.className='dlg';d.innerHTML='<p style="color:var(--text);font-size:1.1em;font-weight:700;margin-bottom:10px"></p><div class="btns"><button class="c"></button><button class="'+(danger?'danger':'primary')+' y"></button></div>';
  d.querySelector('p').textContent=msg;d.querySelector('.c').textContent=K.t('cancel');d.querySelector('.y').textContent=K.t('ok');
  const w=this._open(d);const fin=v=>{const k=K.ui.stack.indexOf(w);if(k>=0)K.ui.stack.splice(k,1);w.parentNode&&w.parentNode.removeChild(w);res(v)};
  d.querySelector('.c').onclick=()=>fin(false);d.querySelector('.y').onclick=()=>fin(true);w.remove=function(){fin(false)}})},
 info(title,body){const d=document.createElement('div');d.className='dlg';d.innerHTML='<h3></h3><p></p><div class="btns"><button class="primary"></button></div>';
  d.querySelector('h3').textContent=title;d.querySelector('p').textContent=body;const b=d.querySelector('button');b.textContent=K.t('ok');
  const w=this._open(d);b.onclick=()=>K.ui.close()},
 lightbox(src){const l=document.createElement('div');l.className='lightbox';l.innerHTML='<img>';l.firstChild.src=src;l.onclick=()=>K.ui.close();this.stack.push(l);ov().appendChild(l)}
};
/* generic escape-hatch used by the Android back button */
K.ui.hasOpen=()=>K.ui.stack.length>0;
})();
