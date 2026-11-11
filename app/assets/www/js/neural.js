/* On-device neural Arabic voice (Piper VITS "kareem" via onnxruntime-web + espeak phonemizer). Works offline; files live in www/neural/.
   Only ONE real speaker exists (male). The "female" voices are the same model with raised pitch + shifted formants (playbackRate), not a different person. */
window.K=window.K||{};
(function(){
const BASE='neural/';
const VOICES=[ /* id, Arabic name, English name, gender, length_scale, noise, playbackRate */
 ['m1','ذكر · طبيعي','Male · natural','m',1.0,.667,1.0],['m2','ذكر · عميق','Male · deep','m',1.05,.5,.9],['m3','ذكر · سريع','Male · fast','m',.85,.667,1.0],['m4','ذكر · هادئ','Male · calm','m',1.15,.45,.95],
 ['f1','أنثى · ناعمة','Female · soft','f',1.0,.55,1.28],['f2','أنثى · واضحة','Female · clear','f',.95,.6,1.34],['f3','أنثى · دافئة','Female · warm','f',1.1,.5,1.22],['f4','أنثى · نشيطة','Female · lively','f',.85,.667,1.38],['f5','أنثى · هادئة','Female · calm','f',1.15,.45,1.26],['f6','أنثى · ناضجة','Female · mature','f',1.05,.5,1.18]];
let S=null,CFG=null,PH=null,dataURL=null,loading=null,ctx=null,cur=null,stopped=false;
const loadScript=src=>new Promise((ok,no)=>{const s=document.createElement('script');s.src=src;s.onload=ok;s.onerror=()=>no(new Error('load '+src));document.head.appendChild(s)});
async function load(prog){
 if(S)return;if(loading)return loading;
 loading=(async()=>{
  prog&&prog(.05);
  if(!window.ort)await loadScript(BASE+'ort.min.js');
  ort.env.wasm.numThreads=1;ort.env.wasm.wasmPaths=new URL(BASE,document.baseURI).href;
  CFG=await (await fetch(BASE+'kareem.json')).json();prog&&prog(.15);
  PH=await import(new URL(BASE+'piper.mjs',document.baseURI).href);
  dataURL=URL.createObjectURL(await (await fetch(BASE+'piper_phonemize.data')).blob());prog&&prog(.4);
  const buf=await (await fetch(BASE+'kareem.onnx')).arrayBuffer();prog&&prog(.8);
  S=await ort.InferenceSession.create(buf,{executionProviders:['wasm']});prog&&prog(1);
 })();
 try{await loading}finally{loading=null}}
function phonemize(text){
 return new Promise(async(res,rej)=>{
  try{const m=await PH.createPiperPhonemize({print:l=>{try{res(JSON.parse(l).phoneme_ids)}catch(e){rej(e)}},printErr:l=>{},locateFile:f=>f.endsWith('.wasm')?BASE+'piper_phonemize.wasm':f.endsWith('.data')?dataURL:f});
   m.callMain(['-l',CFG.espeak.voice,'--input',JSON.stringify([{text:text}]),'--espeak_data','/espeak-ng-data'])}catch(e){rej(e)}})}
async function synth(text,v){
 const ids=await phonemize(text);if(!ids||!ids.length)return null;
 const t=new ort.Tensor('int64',BigInt64Array.from(ids.map(BigInt)),[1,ids.length]);
 const out=await S.run({input:t,input_lengths:new ort.Tensor('int64',BigInt64Array.from([BigInt(ids.length)]),[1]),scales:new ort.Tensor('float32',Float32Array.from([v[5],v[4]*v[6],CFG.inference.noise_w]),[3])});
 return out.output.data}
function clean(t){return String(t||'').replace(/```[\s\S]*?```/g,' ').replace(/`[^`]*`/g,' ').replace(/https?:\/\/\S+/g,' ').replace(/[*_#>|~\[\]\(\)\{\}=+\\\/<]/g,' ').replace(/[\u{1F000}-\u{1FFFF}\u{2600}-\u{27BF}]/gu,' ').replace(/ـ/g,'').replace(/\s+/g,' ').trim()}
function chunks(t){const out=[];(t.match(/[^.!؟?\n،,؛;:]+[.!؟?\n،,؛;:]*/g)||[t]).forEach(s=>{s=s.trim();if(!s)return;const last=out[out.length-1];if(last&&last.length+s.length<90)out[out.length-1]=last+' '+s;else out.push(s)});return out}
const arShare=t=>{const a=(t.match(/[؀-ۿ]/g)||[]).length,l=(t.match(/[A-Za-z؀-ۿ]/g)||[]).length;return l?a/l:0};
function audio(){if(!ctx)ctx=new (window.AudioContext||window.webkitAudioContext)();if(ctx.state==='suspended')ctx.resume();return ctx}
function play(samples,rate){return new Promise(res=>{const c=audio();const b=c.createBuffer(1,samples.length,CFG.audio.sample_rate);b.copyToChannel(samples,0);const s=c.createBufferSource();s.buffer=b;s.playbackRate.value=rate;s.connect(c.destination);cur=s;cur={s:s,res:res};s.onended=()=>{if(cur&&cur.s===s)cur=null;res()};s.start()})}
K.neural={
 voices:VOICES,
 id(){return K.pref.get('neural_voice','m1')},
 enabled(){return K.pref.get('tts_engine','system')==='neural'},
 vo(id){return VOICES.find(v=>v[0]===id)||VOICES[0]},
 load:load,
 loaded(){return !!S},
 /* speaks text; resolves when finished. onStart fires when the first audio begins */
 async speak(text,id,onStart){
  const t=clean(text);if(!t)return;stopped=false;await load();const v=K.neural.vo(id||K.neural.id());
  const cs=chunks(t);let next=synth(cs[0],v),first=true;
  for(let i=0;i<cs.length&&!stopped;i++){
   const smp=await next;next=i+1<cs.length?synth(cs[i+1],v):null;
   if(smp&&!stopped){if(first&&onStart){onStart();first=false}await play(smp,v[6])}}
  return !stopped
 },
 stop(){stopped=true;if(cur){const c=cur;cur=null;try{c.s.onended=null;c.s.stop()}catch(e){}c.res()}},
 shouldUse(text){return K.neural.enabled()&&arShare(clean(text))>=.4}
};
})();
