/* Opening screen: coloured particles fly in and form the logo, light burst, compass spins and settles, name shimmers in */
window.K=window.K||{};
(function(){
const COL=['#e9c99a','#fff6e6','#4d6bfe','#34c759','#ff453a','#ffd60a','#8b5cf6','#22d3ee'];
K.splash={
 show(done){
  const old=document.getElementById('splash');if(old)old.remove();
  const d=document.createElement('div');d.id='splash';d.className='splash';
  let pts='';for(let i=0;i<64;i++){const a=Math.random()*6.283,r=240+Math.random()*340;pts+='<i style="--x:'+Math.round(Math.cos(a)*r)+';--y:'+Math.round(Math.sin(a)*r*1.5)+';--d:'+(Math.random()*.7).toFixed(2)+'s;--c:'+COL[i%COL.length]+';--s:'+(3+Math.random()*5).toFixed(1)+'px"></i>'}
  let fall='';for(let i=0;i<8;i++)fall+='<i style="left:'+(6+i*12)+'%;--c:'+['#34c759','#4d6bfe','#ff453a','#ffd60a'][i%4]+';animation-delay:'+(1.9+(i*3%7)*.12)+'s"></i>';
  const E='emoji/';
  d.innerHTML='<div class="sp-fall">'+fall+'</div><div class="sp-pt">'+pts+'</div><div class="sp-burst"></div><div class="sp-burst b2"></div>'
   +'<div class="sp-c"><div class="sp-mk">'+K.LOGO+'</div><div class="sp-nm">'+K.t('app')+'</div><div class="sp-lat">'+K.t('app_latin')+'</div><div class="sp-tag">'+K.t('l_tagline')+'</div></div>'
   +'<img class="sp-e compass" src="'+E+'1f9ed.svg" width="46"><img class="sp-e sp1" src="'+E+'2728.svg" width="30"><img class="sp-e sp2" src="'+E+'2728.svg" width="22"><img class="sp-e sp3" src="'+E+'1f4ab.svg" width="26">'
   ;
  document.body.appendChild(d);
  let ended=false;const end=()=>{if(ended)return;ended=true;d.classList.add('out');setTimeout(()=>{d.remove();done&&done()},520)};
  d.onclick=end;setTimeout(end,3900);
 }
};
})();
