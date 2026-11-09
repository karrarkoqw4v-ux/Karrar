/* Replaces system emoji with bundled Twemoji SVG images (consistent on every phone). */
window.K=window.K||{};
(function(){
const RE=/(?:\p{Regional_Indicator}{2}|[#*0-9]️?⃣|\p{Extended_Pictographic}(?:️|\p{Emoji_Modifier})?(?:‍\p{Extended_Pictographic}(?:️|\p{Emoji_Modifier})?)*)/gu;
function file(s){const cps=[];for(const ch of s)cps.push(ch.codePointAt(0).toString(16));let a=cps.slice();
 if(s.indexOf('‍')<0)a=a.filter(c=>c!=='fe0f');return a.join('-')}
K.emojify=function(html){
 return html.split(/(<[^>]+>)/).map(function(seg){
  if(seg.charAt(0)==='<')return seg;
  return seg.replace(RE,function(m){const f=file(m);
   const f2=K.emojiSet&&K.emojiSet.has(f)?f:(K.emojiSet&&K.emojiSet.has(f.replace(/-fe0f/g,''))?f.replace(/-fe0f/g,''):null);
   return f2?'<img class="emoji" draggable="false" alt="'+m+'" src="emoji/'+f2+'.svg">':m});
 }).join('');
};
K.emojifyEl=function(el){
 /* replace emoji inside text nodes only (keeps event listeners intact) */
 const w=document.createTreeWalker(el,NodeFilter.SHOW_TEXT,null);const nodes=[];let n;
 while((n=w.nextNode())){if(n.parentNode&&/^(SCRIPT|STYLE|TEXTAREA|INPUT)$/.test(n.parentNode.nodeName))continue;RE.lastIndex=0;if(RE.test(n.nodeValue))nodes.push(n)}
 nodes.forEach(function(t){const html=K.emojify(t.nodeValue.replace(/&/g,'&amp;').replace(/</g,'&lt;'));const tpl=document.createElement('template');tpl.innerHTML=html;t.parentNode.replaceChild(tpl.content,t)});
};
})();
