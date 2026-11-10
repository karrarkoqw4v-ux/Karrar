/* Reply dialect + voice profiles: 28 dialect/language profiles x voice styles, mapped to the device's own text-to-speech voices */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const D=[
 ['auto','تلقائي (حسب كلامك)','Automatic (follows you)','ar',''],
 ['iraq','العراقية','Iraqi','ar-IQ','Iraqi Arabic dialect (شلونك، هسه، اكو، ماكو، شكو، يمعود، گلب)'],
 ['syria','السورية','Syrian','ar-SY','Syrian (Levantine) Arabic dialect (شو، هلق، كتير، منيح)'],
 ['yemen','اليمنية','Yemeni','ar-YE','Yemeni Arabic dialect (ايش، دحين، ذحين، يا شيخ)'],
 ['saudi','السعودية (الخليجية)','Saudi (Gulf)','ar-SA','Saudi Gulf Arabic dialect (وش، ايش، الحين، يالله)'],
 ['kuwait','الكويتية','Kuwaiti','ar-KW','Kuwaiti Arabic dialect (شلونك، شخبارك، هالحين، وايد)'],
 ['uae','الإماراتية','Emirati','ar-AE','Emirati Arabic dialect (شو، الحين، مرّه، يا شيخ)'],
 ['qatar','القطرية','Qatari','ar-QA','Qatari Arabic dialect (شخبارك، الحين، مرّة)'],
 ['bahrain','البحرينية','Bahraini','ar-BH','Bahraini Arabic dialect (شخبارك، هالحين، وايد)'],
 ['oman','العُمانية','Omani','ar-OM','Omani Arabic dialect (شو، هسه، مرّة)'],
 ['egypt','المصرية','Egyptian','ar-EG','Egyptian Arabic dialect (ازيك، دلوقتي، قوي، كده)'],
 ['lebanon','اللبنانية','Lebanese','ar-LB','Lebanese Arabic dialect (كيفك، هلأ، كتير، شو)'],
 ['jordan','الأردنية','Jordanian','ar-JO','Jordanian Arabic dialect (كيفك، هسا، كثير، شو)'],
 ['palestine','الفلسطينية','Palestinian','ar-PS','Palestinian Arabic dialect (كيفك، هلأ، كتير، شو)'],
 ['morocco','المغربية','Moroccan','ar-MA','Moroccan Darija (كيداير، دابا، بزاف، واخا)'],
 ['algeria','الجزائرية','Algerian','ar-DZ','Algerian Arabic dialect (واش، دروك، برك، بزاف)'],
 ['tunisia','التونسية','Tunisian','ar-TN','Tunisian Arabic dialect (شنوة، توا، برشا، يعيشك)'],
 ['libya','الليبية','Libyan','ar-LY','Libyan Arabic dialect (شن، توا، برشا)'],
 ['sudan','السودانية','Sudanese','ar-SD','Sudanese Arabic dialect (شنو، هسي، ياخ)'],
 ['msa','الفصحى','Modern Standard Arabic','ar','Modern Standard Arabic (clear, formal-friendly)'],
 ['en-US','الإنجليزية (أمريكا)','English (US)','en-US','American English'],
 ['en-GB','الإنجليزية (بريطانيا)','English (UK)','en-GB','British English'],
 ['fr','الفرنسية','French','fr-FR','French'],
 ['de','الألمانية','German','de-DE','German'],
 ['es','الإسبانية','Spanish','es-ES','Spanish'],
 ['it','الإيطالية','Italian','it-IT','Italian'],
 ['tr','التركية','Turkish','tr-TR','Turkish'],
 ['ru','الروسية','Russian','ru-RU','Russian'],
 ['pt','البرتغالية','Portuguese','pt-BR','Portuguese']];
const STY=[['m',T('ذكر','Male'),.82],['f',T('أنثى','Female'),1.14],['c',T('طفل','Child'),1.4]];
const IQ=/(شلونك|شلونكم|شكو|ماكو|اكو|هسه|هسّه|يمعود|لهسه|شنو|وين|ليش|گلت|دا اشتغل|انطيني|اريد اسألك|شكد|حبيبي|عمي|خوش)/;
K.dialect={
 list:D,
 cur(){return K.pref.get('reply_dialect','auto')},
 /* system-prompt hint for the model */
 hint(text){
  let id=K.dialect.cur();
  if(id==='auto'){if(IQ.test(text||''))id='iraq';else return ''}
  const d=D.find(x=>x[0]===id);if(!d||id==='auto')return '';
  if(id==='msa'||/^en|fr|de|es|it|tr|ru|pt$/.test(id))return 'Reply in '+d[4]+'.';
  return 'Reply in a warm, natural '+d[4]+'. Keep technical terms, code and file contents in standard form. Sound like a friendly local, not a translation. Stay polite and respectful at all times: never insult the user, never call them names (no كلب، حمار، etc.), never curse or use rude slang, not even jokingly. Greet with warm words like هلا بيك / أهلاً أخوي / حبيبي.'},
 apply(id,style){
  const d=D.find(x=>x[0]===id)||D[0];K.pref.set('reply_dialect',id);
  const sty=STY.find(x=>x[0]===(style||K.pref.get('voice_style','m')))||STY[0];K.pref.set('voice_style',sty[0]);
  K.pref.set('tts_locale',d[3]);K.pref.set('tts_pitch',String(sty[2]));
  K.pref.set('voice_lang',/^(en|fr|de|es|it|tr|ru|pt)/.test(d[3])?'en':'ar')},
 open(){
  const old=$('dlg');if(old)old.remove();
  const d=document.createElement('div');d.id='dlg';d.className='sc';d.dir=K.lang()==='ar'?'rtl':'ltr';document.body.appendChild(d);
  const paint=async()=>{
   const cur=K.dialect.cur(),sty=K.pref.get('voice_style','m'),loc=K.pref.get('tts_locale',''),rate=K.pref.get('tts_rate','1.0');
   let vs=[];try{vs=await K.native.ttsVoices()||[]}catch(e){}
   const lang=(loc||'ar').split('-')[0];const mine=vs.filter(v=>String(v.locale).split('-')[0]===lang);
   const cv=K.pref.get('tts_voice','');
   d.innerHTML='<div class="sc-top"><button class="sc-ib" id="dgBack">'+K.icon('chevron-right',24)+'</button><div class="sc-title">'+T('اللهجة والصوت','Dialect & voice')+'</div><span class="sc-ib ph"></span></div><div class="sc-body">'
    +'<div class="sc-note" style="margin-top:0">'+T('اللهجة تغيّر طريقة ردّ الذكاء الاصطناعي ('+D.length+' ملفاً صوتياً بين لهجات ولغات). نطق الصوت يعتمد على أصوات القراءة المثبّتة في هاتفك؛ لا يوجد في أندرويد صوت بلهجة عراقية حقيقية، فيُقرأ الكلام العراقي بصوت عربي عادي.','The dialect changes how the AI answers ('+D.length+' dialect/language profiles). Pronunciation depends on the text-to-speech voices installed on your phone; Android has no true Iraqi-accent voice, so dialect text is read by a standard Arabic voice.')+'</div>'
    +'<div class="sc-sect">'+T('اللهجة / اللغة','Dialect / language')+'</div><div class="dg-grid">'+D.map(x=>'<button class="dg-ch'+(cur===x[0]?' on':'')+'" data-d="'+x[0]+'">'+esc(T(x[1],x[2]))+'</button>').join('')+'</div>'
    +'<div class="sc-sect">'+T('نمط الصوت','Voice style')+'</div><div class="dg-grid">'+STY.map(x=>'<button class="dg-ch'+(sty===x[0]?' on':'')+'" data-s="'+x[0]+'">'+x[1]+'</button>').join('')+'</div>'
    +'<div class="sc-sect">'+T('سرعة القراءة','Reading speed')+'</div><div class="dg-grid">'+[['0.8',T('بطيئة','Slow')],['1.0',T('عادية','Normal')],['1.2',T('سريعة','Fast')]].map(x=>'<button class="dg-ch'+(rate===x[0]?' on':'')+'" data-r="'+x[0]+'">'+x[1]+'</button>').join('')+'</div>'
    +'<div class="sc-sect">'+T('أصوات هاتفك لهذه اللغة','Your phone voices for this language')+' <span class="n">'+mine.length+'</span></div>'
    +(mine.length?mine.slice(0,40).map((v,i)=>'<button class="dg-v'+(cv===v.name?' on':'')+'" data-v="'+esc(v.name)+'"><span dir="ltr">'+esc(v.name)+'</span><small dir="ltr">'+esc(v.locale)+(v.net?' · online':'')+'</small></button>').join(''):'<div class="sc-empty"><span>'+T('لا توجد أصوات مثبّتة لهذه اللغة. ثبّت صوتاً من إعدادات الهاتف ← إخراج النص إلى كلام.','No installed voices for this language. Install one in Phone settings → Text-to-speech.')+'</span></div>')
    +'<button class="cd-go" id="dgTest" style="margin-top:16px;width:100%">'+T('تجربة الصوت','Test voice')+'</button></div>';
   $('dgBack').onclick=()=>{d.classList.add('out');setTimeout(()=>d.remove(),240)};
   d.querySelectorAll('[data-d]').forEach(b=>b.onclick=()=>{K.dialect.apply(b.dataset.d);K.pref.set('tts_voice','');paint()});
   d.querySelectorAll('[data-s]').forEach(b=>b.onclick=()=>{K.dialect.apply(K.dialect.cur(),b.dataset.s);paint()});
   d.querySelectorAll('[data-r]').forEach(b=>b.onclick=()=>{K.pref.set('tts_rate',b.dataset.r);paint()});
   d.querySelectorAll('[data-v]').forEach(b=>b.onclick=()=>{K.pref.set('tts_voice',b.dataset.v);K.native.speak(sample());paint()});
   $('dgTest').onclick=()=>K.native.speak(sample())};
  const sample=()=>{const id=K.dialect.cur();return ({iraq:'هلا بيك حبيبي، شلونك؟ هسه اكدر اساعدك بأي شي تريده.',syria:'أهلين فيك، كيفك؟ هلق فيني ساعدك بأي شي بدك ياه.',yemen:'مرحبا بك، كيف حالك؟ دحين اقدر اساعدك في اي شيء.',egypt:'أهلاً بيك، ازيك؟ دلوقتي أقدر أساعدك في أي حاجة.',morocco:'مرحبا بيك، كيداير؟ دابا نقدر نعاونك فشي حاجة.','en-US':'Hello! How can I help you today?','en-GB':'Hello! How can I help you today?',fr:'Bonjour ! Comment puis-je vous aider ?',de:'Hallo! Wie kann ich Ihnen helfen?',es:'¡Hola! ¿En qué puedo ayudarte?',it:'Ciao! Come posso aiutarti?',tr:'Merhaba! Size nasıl yardımcı olabilirim?',ru:'Здравствуйте! Чем могу помочь?',pt:'Olá! Como posso ajudar?'})[id]||'أهلاً بك، أنا بوصلة. كيف أستطيع مساعدتك اليوم؟'};
  paint()},
 closeIfOpen(){const d=$('dlg');if(!d)return false;d.classList.add('out');setTimeout(()=>d.remove(),240);return true}
};
})();
