/* Jailbreak guard: warns, counts, and locks the account after more than 10 attempts (stored on this device) */
window.K=window.K||{};
(function(){
const T=(a,e)=>K.lang()==='ar'?a:e;
const $=id=>document.getElementById(id);
const RE=/(ignore (all |the )?(previous|above|prior) (instructions|rules|prompts)|jailbreak|\bDAN\b|do anything now|developer mode|bypass (the )?(safety|restrictions?|filters?|guardrails?)|disregard (all )?(the )?(rules|policy|guidelines)|pretend (you have|there are) no (rules|restrictions)|act as an? unrestricted|without any restrictions|reveal (your )?(system|hidden) prompt|كسر (ال)?قيود|اكسر (ال)?قيود|تجاوز (ال)?قيود|تخطي (ال)?قيود|فك (ال)?قيود|تجاهل (كل |جميع )?(ال)?(تعليمات|قواعد|قيود)|وضع (ال)?مطور|بدون (أي )?قيود|بلا قيود|تخلّ? عن (ال)?قيود)/i;
K.guard={
 locked(){return K.pref.get('jb_locked','')==='1'},
 count(){return +K.pref.get('jb_count','0')||0},
 test(t){return RE.test(t||'')},
 /* returns the local reply text (and updates the counter / lock) */
 hit(){
  const n=K.guard.count()+1;K.pref.set('jb_count',String(n));
  if(n>10){K.pref.set('jb_locked','1');setTimeout(K.guard.showLock,1200);return T('⛔ **تم قفل حسابك** بسبب محاولاتك المتكررة لكسر قيود الذكاء الاصطناعي.','⛔ **Your account is locked** because of repeated attempts to bypass the AI restrictions.')}
  if(n>=9)return T('⛔ **آخر محاولة لك! يتم قفل حسابك بسبب محاولاتك كسر القيود.** ('+n+' من 10)','⛔ **This is your last attempt! Your account will be locked because of your attempts to bypass the restrictions.** ('+n+' of 10)');
  if(n>=4)return T('⚠️ **تحذير:** محاولاتك المتكررة لكسر القيود مسجّلة، واستمرارها يؤدي إلى قفل حسابك. ('+n+' من 10)','⚠️ **Warning:** your repeated attempts to bypass the restrictions are logged, and continuing will lock your account. ('+n+' of 10)');
  return T('🚫 تم رصد محاولة لكسر قيود الذكاء الاصطناعي، ولا أستطيع تنفيذ هذا الطلب. ('+n+' من 10)','🚫 An attempt to bypass the AI restrictions was detected, and I cannot do that. ('+n+' of 10)')},
 showLock(){
  if($('jbl'))return;const d=document.createElement('div');d.id='jbl';d.className='jbl';
  d.innerHTML='<div class="jb-i"><img src="emoji/1f512.svg" alt=""><h2>'+T('تم قفل حسابك','Your account is locked')+'</h2><p>'+T('بسبب محاولات متكررة لكسر قيود الذكاء الاصطناعي، تم قفل الحساب على هذا الجهاز. للمراجعة تواصل مع المطوّر.','Because of repeated attempts to bypass the AI restrictions, the account is locked on this device. Contact the developer to review it.')+'</p>'
   +'<div class="jb-c"><b>كروري · Karrouri</b><span dir="ltr">karrarkooqwtoi@gmail.com</span><span dir="ltr">TikTok @vwv2e · Instagram @10s.i2 · X @Compassv4</span></div></div>';
  document.body.appendChild(d)},
 boot(){if(K.guard.locked())K.guard.showLock()}
};
})();
