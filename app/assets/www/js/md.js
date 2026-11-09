/* Small Markdown renderer: headings, lists, tables, quotes, code boxes, links, **bold**, `code`, [n] citations. */
window.K=window.K||{};
(function(){
const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
function inline(s,hasSrc){
 s=esc(s);
 const codes=[];s=s.replace(/`([^`]+)`/g,(m,c)=>{codes.push(c);return '\u0001'+(codes.length-1)+'\u0002'});
 s=s.replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>').replace(/(^|[^*])\*([^*\s][^*]*)\*/g,'$1<em>$2</em>');
 s=s.replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g,'<a href="$2" data-url="$2">$1</a>');
 if(hasSrc)s=s.replace(/\[(\d{1,2})\]/g,'<sup class="cite" data-i="$1">$1</sup>');
 return s.replace(/\u0001(\d+)\u0002/g,(m,i)=>'<code>'+codes[i]+'</code>');
}
K.md=function(text,hasSrc){
 const lines=text.split('\n');let out='',i=0,para=[];
 const flush=()=>{if(para.length){out+='<p>'+para.map(l=>inline(l,hasSrc)).join('<br>')+'</p>';para=[]}};
 while(i<lines.length){
  let l=lines[i];
  if(/^\s*```/.test(l)){flush();const lang=l.replace(/^\s*```/,'').trim();const buf=[];i++;
   while(i<lines.length&&!/^\s*```/.test(lines[i])){buf.push(lines[i]);i++}i++;
   out+='<div class="codebox"><div class="ch"><span>'+esc(lang||'code')+'<em class="ln">'+buf.length+' '+(K.lang()==='ar'?'سطر':'lines')+'</em></span><button class="cp" data-copy>'+K.icon('copy',15)+'<span>'+K.t('copy')+'</span></button></div><pre><code>'+esc(buf.join('\n'))+'</code></pre></div>';continue}
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
K.mdEmoji=function(text,hasSrc){return K.emojify(K.md(text,hasSrc))};
})();
