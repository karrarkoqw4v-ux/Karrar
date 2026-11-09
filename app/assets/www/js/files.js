/* File attachments: detect the format, read its text (PDF / DOCX / PPTX / XLSX / plain text & code) and style the card per type */
window.K=window.K||{};
(function(){
const KINDS={
 pdf:['PDF','#e5484d'],doc:['DOC','#2b6cff'],docx:['DOC','#2b6cff'],rtf:['RTF','#2b6cff'],odt:['ODT','#2b6cff'],
 xls:['XLS','#17a34a'],xlsx:['XLS','#17a34a'],csv:['CSV','#17a34a'],ods:['ODS','#17a34a'],
 ppt:['PPT','#f08c1b'],pptx:['PPT','#f08c1b'],
 txt:['TXT','#8e8e93'],md:['MD','#8e8e93'],log:['LOG','#8e8e93'],
 json:['JSON','#8b5cf6'],xml:['XML','#8b5cf6'],html:['HTML','#e86a33'],css:['CSS','#2a7de1'],js:['JS','#d8b400'],ts:['TS','#2f74c0'],py:['PY','#3b82c4'],java:['JAVA','#d9534f'],kt:['KT','#a45bd6'],c:['C','#5b7bb5'],cpp:['C++','#5b7bb5'],cs:['C#','#8c5bd6'],php:['PHP','#7a86b8'],sql:['SQL','#e0a030'],sh:['SH','#4caf50'],yml:['YML','#c0392b'],yaml:['YML','#c0392b'],
 zip:['ZIP','#e0a030'],rar:['RAR','#e0a030']
};
const TEXT=new Set(['txt','md','log','csv','json','xml','html','htm','css','js','ts','jsx','tsx','py','java','kt','c','cpp','h','cs','php','sql','sh','yml','yaml','ini','env','rb','go','rs','swift','dart','vue','svelte','tex','srt','rtf']);
const MAXCH=60000;
function ext(n){const m=/\.([A-Za-z0-9+#]+)$/.exec(n||'');return m?m[1].toLowerCase():''}
function kind(n){const e=ext(n);return KINDS[e]||[(e||'FILE').toUpperCase().slice(0,4),'#6b7280']}
function size(n){return n<1024?n+' B':n<1048576?Math.round(n/1024)+' KB':(n/1048576).toFixed(1)+' MB'}
function bytes(b64){const bin=atob(b64),u=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);return u}
const loaded={};
function load(src){return loaded[src]||(loaded[src]=new Promise((res,rej)=>{const s=document.createElement('script');s.src=src;s.onload=res;s.onerror=()=>rej(new Error('load '+src));document.head.appendChild(s)}))}
function decode(u){try{return new TextDecoder('utf-8',{fatal:true}).decode(u)}catch(e){try{return new TextDecoder('windows-1256').decode(u)}catch(e2){return new TextDecoder().decode(u)}}}
const PF=/[\uFB50-\uFDFF\uFE70-\uFEFF]/;
function pageText(items){
 items=items.filter(it=>it.str!=='');const lines=[];
 items.forEach(it=>{const y=it.transform[5],x=it.transform[4];let L=lines.find(l=>Math.abs(l.y-y)<3);if(!L){L={y:y,it:[]};lines.push(L)}L.it.push({s:it.str,x:x,w:it.width||0,fs:Math.abs(it.transform[0])||10})});
 lines.sort((a,b)=>b.y-a.y);
 return lines.map(L=>{
  const vis=L.it.some(i=>PF.test(i.s));
  const arr=L.it.slice().sort((a,b)=>vis?b.x-a.x:a.x-b.x);let out='',prev=null;
  arr.forEach(i=>{let t=i.s;if(PF.test(t))t=Array.from(t).reverse().join('');t=t.normalize('NFKC');
   if(prev){const gap=vis?(prev.x-(i.x+i.w)):(i.x-(prev.x+prev.w));if(gap>prev.fs*.18)out+=' '}
   out+=t;prev=i});
  return out}).join('\n');
}
async function zipText(u,pick){await load('js/vendor/jszip.min.js');const z=await JSZip.loadAsync(u);const out=[];
 const names=Object.keys(z.files).filter(pick).sort((a,b)=>a.localeCompare(b,undefined,{numeric:true}));
 for(const n of names){out.push(await z.files[n].async('string'))}return out}
const strip=x=>x.replace(/<w:p[ >][^]*?<\/w:p>/g,p=>p.replace(/<w:tab\/>/g,'\t').replace(/<[^>]+>/g,'')+'\n').replace(/<[^>]+>/g,'').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"');
const tt=x=>(x.match(/<(?:a:t|t)[^>]*>([^<]*)<\/(?:a:t|t)>/g)||[]).map(s=>s.replace(/<[^>]+>/g,'')).join(' ').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>');
K.files={
 ext,kind,size,
 /* f: {name,size,b64}  ->  {text,pages?} or {err:'f_...'} */
 async parse(f){
  const e=ext(f.name),u=bytes(f.b64);let text='',pages=0;
  try{
   if(TEXT.has(e)||(!KINDS[e]&&!/\.(pdf|docx|pptx|xlsx|zip|rar|png|jpe?g|gif|webp|mp3|mp4|apk)$/i.test(f.name)&&!u.slice(0,2000).includes(0))){text=decode(u)}
   else if(e==='pdf'){
    await load('js/vendor/pdf.min.js');pdfjsLib.GlobalWorkerOptions.workerSrc='js/vendor/pdf.worker.min.js';
    const doc=await pdfjsLib.getDocument({data:u}).promise;pages=doc.numPages;const parts=[];
    for(let i=1;i<=Math.min(pages,60)&&parts.join('').length<MAXCH;i++){const pg=await doc.getPage(i);const tc=await pg.getTextContent();parts.push(pageText(tc.items))}
    text=parts.join('\n\n');
   }
   else if(e==='docx'){const x=await zipText(u,n=>n==='word/document.xml');text=strip(x.join('\n'))}
   else if(e==='pptx'){const x=await zipText(u,n=>/^ppt\/slides\/slide\d+\.xml$/.test(n));pages=x.length;text=x.map((s,i)=>'Slide '+(i+1)+': '+tt(s)).join('\n')}
   else if(e==='xlsx'){const x=await zipText(u,n=>n==='xl/sharedStrings.xml');text=x.map(tt).join('\n')}
   else return {err:'f_unsupported'};
  }catch(err){return {err:'f_unsupported'}}
  text=text.replace(/\u0000/g,'').replace(/[ \t]+\n/g,'\n').replace(/\n{3,}/g,'\n\n').trim();
  if(text.length<12)return {err:'f_notext'};
  return {text:text.slice(0,MAXCH),pages};
 },
 /* HTML for a coloured card (used in the composer and in sent messages) */
 card(f,cls){const k=kind(f.name||f.n),n=f.name||f.n,sz=f.size!=null?size(f.size):'',pg=f.pages?' · '+K.t('f_pages',f.pages):'';
  const esc=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  return '<div class="fcard '+(cls||'')+'" style="--fc:'+k[1]+'"><span class="ft">'+esc(k[0])+'</span><span class="fn"><b>'+esc(n)+'</b><small>'+esc(k[0])+(sz?' · '+sz:'')+pg+'</small></span></div>'}
};
})();
