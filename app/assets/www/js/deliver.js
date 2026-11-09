/* Files the assistant sends: ```file:name.ext blocks become download cards (text formats, .zip bundles, .docx, .xlsx) */
window.K=window.K||{};
(function(){
const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const MIME={txt:'text/plain',md:'text/markdown',csv:'text/csv',json:'application/json',html:'text/html',htm:'text/html',css:'text/css',js:'text/javascript',xml:'application/xml',svg:'image/svg+xml',zip:'application/zip',docx:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',xlsx:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',py:'text/x-python',java:'text/x-java',kt:'text/x-kotlin',sql:'application/sql',yaml:'text/yaml',yml:'text/yaml',sh:'application/x-sh'};
const BIN=/\.(apk|aab|exe|dmg|pdf|png|jpe?g|gif|webp|mp3|mp4|mov|pptx|rar|7z|iso|dex|so)$/i;
const reg={};let n=0;
const T=(a,e)=>K.lang()==='ar'?a:e;
const xe=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const colL=i=>{let s='';i++;while(i>0){const m=(i-1)%26;s=String.fromCharCode(65+m)+s;i=Math.floor((i-1)/26)}return s};
function b64(u8){let s='';for(let i=0;i<u8.length;i+=0x8000)s+=String.fromCharCode.apply(null,u8.subarray(i,i+0x8000));return btoa(s)}
function utf8(t){return b64(new TextEncoder().encode(t))}
async function zipBundle(content){
 await K.files.load('js/vendor/jszip.min.js');const z=new JSZip();
 const parts=content.split(/^=== *(.+?) *===\s*$/m);
 if(parts.length<3){z.file('file.txt',content)}
 else for(let i=1;i<parts.length;i+=2){z.file(parts[i].trim().replace(/^\/+/,''),(parts[i+1]||'').replace(/^\n/,''))}
 return z.generateAsync({type:'uint8array',compression:'DEFLATE'});
}
async function docx(content){
 await K.files.load('js/vendor/jszip.min.js');const z=new JSZip(),rtl=/[\u0600-\u06FF]/.test(content);
 const paras=content.split(/\n/).map(l=>{const h=/^(#{1,3})\s+(.*)/.exec(l);const t=h?h[2]:l.replace(/\*\*/g,'');
  return '<w:p><w:pPr>'+(rtl?'<w:bidi/>':'')+'</w:pPr><w:r><w:rPr>'+(h?'<w:b/><w:sz w:val="'+(40-h[1].length*4)+'"/>':'')+(rtl?'<w:rtl/>':'')+'</w:rPr><w:t xml:space="preserve">'+xe(t)+'</w:t></w:r></w:p>'}).join('');
 z.file('[Content_Types].xml','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>');
 z.file('_rels/.rels','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>');
 z.file('word/document.xml','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'+paras+'</w:body></w:document>');
 return z.generateAsync({type:'uint8array',compression:'DEFLATE'});
}
async function xlsx(content){
 await K.files.load('js/vendor/jszip.min.js');const z=new JSZip();
 const rows=content.trim().split(/\r?\n/).map(l=>l.split(/\t|,(?=(?:[^"]*"[^"]*")*[^"]*$)/).map(c=>c.trim().replace(/^"|"$/g,'')));
 const sd=rows.map((r,i)=>'<row r="'+(i+1)+'">'+r.map((c,j)=>{const ref=colL(j)+(i+1);return (c!==''&&!isNaN(c))?'<c r="'+ref+'"><v>'+c+'</v></c>':'<c r="'+ref+'" t="inlineStr"><is><t xml:space="preserve">'+xe(c)+'</t></is></c>'}).join('')+'</row>').join('');
 z.file('[Content_Types].xml','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>');
 z.file('_rels/.rels','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>');
 z.file('xl/workbook.xml','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>');
 z.file('xl/_rels/workbook.xml.rels','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>');
 z.file('xl/worksheets/sheet1.xml','<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'+sd+'</sheetData></worksheet>');
 return z.generateAsync({type:'uint8array',compression:'DEFLATE'});
}
async function build(name,content){
 const e=K.files.ext(name);
 if(e==='zip')return {b64:b64(await zipBundle(content)),mime:MIME.zip};
 if(e==='docx')return {b64:b64(await docx(content)),mime:MIME.docx};
 if(e==='xlsx')return {b64:b64(await xlsx(content)),mime:MIME.xlsx};
 return {b64:utf8((e==='csv'?'\ufeff':'')+content),mime:MIME[e]||'text/plain'};
}
K.files.box=function(name,content){
 name=name.replace(/[\\/:*?"<>|]/g,'_').slice(0,80)||'file.txt';
 const id='f'+(++n);reg[id]={name,content};
 const k=K.files.kind(name),size=new Blob([content]).size;
 const bad=BIN.test(name);
 return '<div class="filebox'+(bad?' bad':'')+'" style="--fc:'+(bad?'#ff9f0a':k[1])+'"><span class="ft">'+esc(k[0])+'</span><div class="fn"><b>'+esc(name)+'</b><small>'+(bad?T('لا يمكن إنشاء هذا النوع من نص','Cannot be created from text'):K.files.size(size))+'</small></div>'
  +(bad?'':'<button class="dl" data-fid="'+id+'">'+K.icon('arrow-up',16,2.6).replace('<svg ','<svg style="transform:rotate(180deg)" ')+'<span>'+T('تحميل','Download')+'</span></button>')+'</div>';
};
K.files.save=async function(id,btn){
 const f=reg[id];if(!f)return;
 const sp=btn&&btn.querySelector('span');if(sp)sp.textContent='…';
 try{
  const r=await build(f.name,f.content);
  const res=await K.native.saveFile(f.name,r.mime,r.b64);
  if(btn){btn.closest('.filebox').classList.add('ok');if(sp)sp.textContent=T('تم الحفظ','Saved');setTimeout(()=>{btn.closest('.filebox')&&btn.closest('.filebox').classList.remove('ok');if(sp)sp.textContent=T('تحميل','Download')},2500)}
  K.toast(res&&res.indexOf('err')===0?T('تعذّر الحفظ','Could not save'):T('تم الحفظ في التنزيلات','Saved to Downloads'));
 }catch(e){if(sp)sp.textContent=T('تحميل','Download');K.toast(T('تعذّر إنشاء الملف','Could not build the file'))}
};
})();
