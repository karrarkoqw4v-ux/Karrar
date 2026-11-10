/* Open-source model catalog (43 models across 8 providers): pick one, the app uses it first and falls back to the others */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const P={groq:['Groq','console.groq.com/keys'],openrouter:['OpenRouter (free)','openrouter.ai/keys'],hf:['Hugging Face','huggingface.co/settings/tokens'],cerebras:['Cerebras','cloud.cerebras.ai'],mistral:['Mistral','console.mistral.ai/api-keys'],gemini:['Google AI Studio (Gemma)','aistudio.google.com/apikey'],github:['GitHub Models','github.com/settings/tokens'],pollinations:['Pollinations (no key)','']};
const M=[
 ['groq','Llama 3.3 70B','llama-3.3-70b-versatile'],['groq','Llama 3.1 8B Instant','llama-3.1-8b-instant'],['groq','GPT-OSS 120B','openai/gpt-oss-120b'],['groq','GPT-OSS 20B','openai/gpt-oss-20b'],['groq','Qwen3 32B','qwen/qwen3-32b'],['groq','Llama 4 Scout','meta-llama/llama-4-scout-17b-16e-instruct'],['groq','Kimi K2','moonshotai/kimi-k2-instruct'],
 ['openrouter','Llama 3.3 70B','meta-llama/llama-3.3-70b-instruct:free'],['openrouter','DeepSeek R1','deepseek/deepseek-r1:free'],['openrouter','DeepSeek V3','deepseek/deepseek-chat-v3-0324:free'],['openrouter','Qwen3 235B','qwen/qwen3-235b-a22b:free'],['openrouter','Qwen 2.5 72B','qwen/qwen-2.5-72b-instruct:free'],['openrouter','Gemma 3 27B','google/gemma-3-27b-it:free'],['openrouter','Mistral Small 3.1','mistralai/mistral-small-3.1-24b-instruct:free'],['openrouter','GPT-OSS 20B','openai/gpt-oss-20b:free'],['openrouter','GLM 4.5 Air','z-ai/glm-4.5-air:free'],['openrouter','Kimi K2','moonshotai/kimi-k2:free'],
 ['hf','Llama 3.3 70B','meta-llama/Llama-3.3-70B-Instruct'],['hf','Qwen 2.5 72B','Qwen/Qwen2.5-72B-Instruct'],['hf','Qwen3 32B','Qwen/Qwen3-32B'],['hf','DeepSeek R1','deepseek-ai/DeepSeek-R1'],['hf','DeepSeek V3','deepseek-ai/DeepSeek-V3'],['hf','Gemma 3 27B','google/gemma-3-27b-it'],['hf','Mistral Small 3.1','mistralai/Mistral-Small-3.1-24B-Instruct-2503'],
 ['cerebras','Llama 3.3 70B','llama-3.3-70b'],['cerebras','Llama 3.1 8B','llama3.1-8b'],['cerebras','Qwen3 32B','qwen-3-32b'],['cerebras','GPT-OSS 120B','gpt-oss-120b'],
 ['mistral','Mistral Nemo','open-mistral-nemo'],['mistral','Mixtral 8x22B','open-mixtral-8x22b'],['mistral','Mistral Small','mistral-small-latest'],
 ['gemini','Gemma 3 27B','gemma-3-27b-it'],['gemini','Gemma 3 12B','gemma-3-12b-it'],['gemini','Gemma 3 4B','gemma-3-4b-it'],
 ['github','Llama 3.3 70B','meta/Llama-3.3-70B-Instruct'],['github','Llama 3.1 405B','meta/Meta-Llama-3.1-405B-Instruct'],['github','DeepSeek R1','deepseek/DeepSeek-R1'],['github','Phi-4','microsoft/Phi-4'],['github','Mistral Nemo','mistral-ai/Mistral-Nemo'],
 ['pollinations','Llama (Pollinations)','llama'],['pollinations','Mistral (Pollinations)','mistral'],['pollinations','DeepSeek (Pollinations)','deepseek'],['pollinations','Qwen Coder (Pollinations)','qwen-coder']];
K.catalog={
 list:M,count(){return M.length},
 cur(){return [K.pref.get('pref_provider',''),K.pref.get('pref_model','')]},
 open(){
  const old=$('mcat');if(old)old.remove();
  const d=document.createElement('div');d.id='mcat';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);
  let q='';
  const paint=()=>{
   const [cp,cm]=K.catalog.cur();
   const rows=M.filter(x=>!q||(x[1]+' '+x[2]+' '+P[x[0]][0]).toLowerCase().includes(q));
   const groups=Object.keys(P).filter(k=>rows.some(x=>x[0]===k));
   d.innerHTML='<div class="sc-top"><button class="sc-ib" id="mcBack">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('كتالوج النماذج','Model catalog')+' ('+M.length+')</div><span class="sc-ib ph"></span></div><div class="sc-body">'
    +'<div class="sc-note" style="margin-top:0">'+T('كل النماذج مفتوحة المصدر. اختر نموذجاً ليُستخدم أولاً، وإن تعذّر ينتقل التطبيق بصمت لغيره. النماذج بمزوّد يحتاج مفتاحاً مجانياً: يُطلب منك مرة واحدة.','All models are open-weights. Pick one to be used first; if it fails the app silently moves on to the others. Providers that need a free key ask for it once.')+'</div>'
    +'<div class="sc-in" style="margin:6px 0 4px"><input id="mcQ" placeholder="'+T('بحث عن نموذج…','Search a model…')+'" value="'+esc(q)+'"></div>'
    +groups.map(k=>{const has=k==='pollinations'||!!K.pref.get('fk_'+k,'');return '<div class="sc-sect">'+P[k][0]+' <span class="n">'+rows.filter(x=>x[0]===k).length+'</span>'+(has?'':' <span style="color:#ff9f0a;font-weight:700">'+T('يحتاج مفتاحاً','needs key')+'</span>')+'</div>'
     +rows.filter(x=>x[0]===k).map(x=>'<button class="mc-r'+(cp===x[0]&&cm===x[2]?' on':'')+'" data-p="'+x[0]+'" data-m="'+esc(x[2])+'"><div><b>'+esc(x[1])+'</b><small dir="ltr">'+esc(x[2])+'</small></div>'+(cp===x[0]&&cm===x[2]?K.icon('check',20,2.8):'')+'</button>').join('')}).join('')
    +'<button class="cd-sec" id="mcAuto" style="width:100%;margin-top:14px">'+T('الاختيار التلقائي (الأفضل المتاح)','Automatic (best available)')+'</button></div>';
   $('mcBack').onclick=()=>{d.classList.add('out');setTimeout(()=>d.remove(),240)};
   $('mcQ').oninput=e=>{q=e.target.value.trim().toLowerCase();const pos=e.target.selectionStart;paint();const i=$('mcQ');i.focus();i.setSelectionRange(pos,pos)};
   $('mcAuto').onclick=()=>{K.pref.set('pref_provider','');K.pref.set('pref_model','');K.toast(T('تم: اختيار تلقائي','Done: automatic'));paint()};
   d.querySelectorAll('.mc-r').forEach(b=>b.onclick=async()=>{const p=b.dataset.p;
    if(p!=='pollinations'&&!K.pref.get('fk_'+p,'')){const v=await K.ui.prompt({title:P[p][0]+' — API key',secret:true,hint:P[p][1]});if(!v)return;K.pref.set('fk_'+p,v.trim())}
    K.pref.set('pref_provider',p);K.pref.set('pref_model',b.dataset.m);K.native.haptic();K.toast(T('سيُستخدم هذا النموذج أولاً','This model will be used first'));paint()})};
  paint()},
 closeIfOpen(){const d=$('mcat');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true}
};
})();
