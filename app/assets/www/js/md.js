/* Small Markdown renderer: headings, lists, tables, quotes, code boxes, links, **bold**, `code`, [n] citations. */
window.K=window.K||{};
(function(){
const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
function inline(s,hasSrc){
 s=esc(s);
 const codes=[];s=s.replace(/`([^`]+)`/g,(m,c)=>{codes.push(c);return '\u0001'+(codes.length-1)+'\u0002'});
 s=s.replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>').replace(/(^|[^*])\*([^*\s][^*]*)\*/g,'$1<em>$2</em>');
 s=s.replace(/&lt;a\s[^&]*?href=(?:&quot;|")(https?:[^"&\s]+)(?:&quot;|")[^&]*?&gt;(.*?)&lt;\/a&gt;/gi,'[$2]($1)');
 s=s.replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g,'<a href="$2" data-url="$2">$1</a>');
 s=s.replace(/<a [^>]*>.*?<\/a>|(https?:\/\/[^\s<>"')\]&]+[^\s<>"')\]&.,;:!؟،])/g,function(m,u){if(!u)return m;let h=u;try{h=new URL(u).hostname.replace(/^www\./,'')}catch(e){}return '<a href="'+u+'" data-url="'+u+'" class="lnk">'+(K.lang()==='ar'?'فتح · ':'Open · ')+h+'</a>'});
 if(hasSrc)s=s.replace(/\[(\d{1,2})\]/g,'<sup class="cite" data-i="$1">$1</sup>');
 return s.replace(/\u0001(\d+)\u0002/g,(m,i)=>'<code>'+codes[i]+'</code>');
}
const mdBase=function(text,hasSrc){
 const lines=text.split('\n');let out='',i=0,para=[];
 const flush=()=>{if(para.length){out+='<p>'+para.map(l=>inline(l,hasSrc)).join('<br>')+'</p>';para=[]}};
 while(i<lines.length){
  let l=lines[i];
  if(/^\s*```/.test(l)){flush();const lang=l.replace(/^\s*```/,'').trim();const buf=[];i++;
   while(i<lines.length&&!/^\s*```/.test(lines[i])){buf.push(lines[i]);i++}i++;
   const am=/^app[:=]\s*(.+)$/i.exec(lang);if(am&&K.apps){out+=K.apps.card(am[1].trim(),buf.join('\n'));continue}
   const fm=/^file[:=]\s*(.+)$/i.exec(lang);
   if(fm&&K.files&&K.files.box){out+=K.files.box(fm[1].trim(),buf.join('\n'));continue}
   out+='<div class="codebox"><div class="ch"><span>'+esc(lang||'code')+'</span><span class="cpg"><button class="cp" data-save>'+K.icon('arrow-up',15).replace('<svg ','<svg style="transform:rotate(180deg)" ')+'<span>'+(K.lang()==='ar'?'حفظ':'Save')+'</span></button><button class="cp" data-copy>'+K.icon('copy',15)+'<span>'+K.t('copy')+'</span></button></span></div><pre><code>'+(K.hl?K.hl(buf.join('\n'),lang):esc(buf.join('\n')))+'</code></pre></div>';continue}
  let m;
  if((m=l.match(/^(#{1,3})\s+(.*)/))){flush();out+='<h'+m[1].length+'>'+inline(m[2],hasSrc)+'</h'+m[1].length+'>';i++;continue}
  if(/^\s*(---|\*\*\*)\s*$/.test(l)){flush();out+='<hr>';i++;continue}
  if(/^\s*>\s?/.test(l)){flush();const b=[];while(i<lines.length&&/^\s*>\s?/.test(lines[i])){b.push(lines[i].replace(/^\s*>\s?/,''));i++}const f=(b[0]||'').trim();const cl=/^(⚠|🚨|❗)/.test(f)?'warn':/^(💡|✨|🔥)/.test(f)?'tip':/^(✅|✔|🎯)/.test(f)?'ok':/^(❌|⛔|🚫)/.test(f)?'bad':/^(ℹ|📌|📝|📖)/.test(f)?'info':'';out+='<blockquote'+(cl?' class="co '+cl+'"':'')+'>'+b.map(x=>inline(x,hasSrc)).join('<br>')+'</blockquote>';continue}
  if(/^\s*[-*•]\s+/.test(l)){flush();let b='';while(i<lines.length&&/^\s*[-*•]\s+/.test(lines[i])){b+='<li>'+inline(lines[i].replace(/^\s*[-*•]\s+/,''),hasSrc)+'</li>';i++}out+='<ul>'+b+'</ul>';continue}
  if(/^\s*\d+[.)]\s+/.test(l)){flush();let b='';while(i<lines.length&&/^\s*\d+[.)]\s+/.test(lines[i])){b+='<li>'+inline(lines[i].replace(/^\s*\d+[.)]\s+/,''),hasSrc)+'</li>';i++}out+='<ol>'+b+'</ol>';continue}
  if(/^\s*\|.*\|\s*$/.test(l)&&i+1<lines.length&&/^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(lines[i+1])){flush();
   const row=r=>r.trim().replace(/^\||\|$/g,'').split('|').map(c=>c.trim());
   const head=row(l);i+=2;let body='';
   while(i<lines.length&&/^\s*\|.*\|\s*$/.test(lines[i])){body+='<tr>'+row(lines[i]).map(c=>'<td>'+inline(c,hasSrc)+'</td>').join('')+'</tr>';i++}
   out+='<table><thead><tr>'+head.map(c=>'<th>'+inline(c,hasSrc)+'</th>').join('')+'</tr></thead><tbody>'+body+'</tbody></table>';continue}
  if(l.trim()===''){flush();i++;continue}
  para.push(l);i++;
 }
 flush();return out;
};
/* ":::doc" blocks render as a writing card (title, copy, share) like a document */
K.md=function(text,hasSrc){
 if(text.indexOf(':::doc')<0)return mdBase(text,hasSrc);
 const re=/(^|\n):::doc[^\n]*\n?([\s\S]*?)(?:\n:::[ \t]*(?=\n|$)|$)/;let out='',rest=text,n=0;
 while(true){const m=re.exec(rest);if(!m)break;
  const pre=rest.slice(0,m.index);if(pre.trim())out+=mdBase(pre,hasSrc);
  const body=m[2].replace(/^\s+/,'');
  out+='<div class="docc"><div class="dc-h"><span class="dc-t"><svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="3" width="16" height="18" rx="3"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>'+(K.lang()==='ar'?'الكتابة':'Writing')+'</span><span class="dc-b"><button data-dcs="'+n+'" title="share"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/></svg></button><button data-dcc="'+n+'" title="copy"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="8" y="8" width="13" height="13" rx="2"/><path d="M4 16V5a2 2 0 0 1 2-2h10"/></svg></button></span></div><div class="dc-body" data-raw="'+encodeURIComponent(body).replace(/"/g,'%22')+'">'+mdBase(body,hasSrc)+'</div></div>';
  n++;rest=rest.slice(m.index+m[0].length)}
 if(rest.trim())out+=mdBase(rest,hasSrc);return out};
K.mdEmoji=function(text,hasSrc){return K.emojify(K.md(text,hasSrc))};
})();
