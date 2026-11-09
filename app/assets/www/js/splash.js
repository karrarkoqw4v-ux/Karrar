/* Opening screen: logo + name centred, then glide up into the login screen while its buttons fade in */
window.K=window.K||{};
(function(){
K.splash={
 show(done){
  const old=document.getElementById('splash');if(old)old.remove();
  const login=document.getElementById('login'),onLogin=login&&!login.classList.contains('hidden');
  const d=document.createElement('div');d.id='splash';
  d.innerHTML='<div class="sp2-g" id="spg"><div class="sp2-mk">'+K.LOGO+'</div><div class="sp2-nm">'+K.t('app')+'</div></div>';
  document.body.appendChild(d);
  if(onLogin)login.classList.add('sp-wait');
  let ended=false,tm1=0,tm2=0;
  const finish=()=>{if(ended)return;ended=true;clearTimeout(tm1);clearTimeout(tm2);
   if(onLogin){login.classList.remove('sp-wait');reveal()}
   d.remove();done&&done()};
  const reveal=()=>{
   login.querySelectorAll('.lg-top>*,.lg-tag,.lg-btns>*').forEach((e,i)=>{e.style.animation='none';void e.offsetWidth;e.style.animation='fadeUp .55s '+(i*70)+'ms var(--ease) both'})};
  tm1=setTimeout(()=>{
   if(!onLogin){d.classList.add('out');tm2=setTimeout(finish,450);return}
   const g=document.getElementById('spg').querySelector('.sp2-mk').getBoundingClientRect(),
         t=login.querySelector('.lg-mk');
   let dy=0;if(t){dy=t.getBoundingClientRect().top-g.top}
   const sg=document.getElementById('spg');sg.style.transition='transform .75s cubic-bezier(.65,0,.25,1)';sg.style.transform='translateY('+dy+'px)';
   d.classList.add('go');
   login.classList.remove('sp-wait');login.classList.add('sp-logo-hide');reveal();
   tm2=setTimeout(()=>{login.classList.remove('sp-logo-hide');finish()},780);
  },1300);
  d.onclick=finish;
 }
};
})();
