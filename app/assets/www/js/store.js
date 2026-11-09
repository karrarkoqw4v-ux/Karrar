window.K=window.K||{};
K.pref={
 get(k,d){return K.native.getPref(k,d)},set(k,v){K.native.setPref(k,v)},
 bool(k,d){return K.native.getPref(k,d?'1':'0')==='1'},setBool(k,v){K.native.setPref(k,v?'1':'0')}
};
K.db={
 convs:[],
 uid(){return K.pref.get('user_email','')||'guest'},
 key(){return 'convs_'+this.uid()},
 load(){try{this.convs=JSON.parse(localStorage.getItem(this.key())||'[]')}catch(e){this.convs=[]}
  const days=+K.pref.get('auto_del','0');if(days>0){const lim=Date.now()-days*864e5;this.convs=this.convs.filter(c=>c.pinned||c.ts>=lim)}},
 save(){try{localStorage.setItem(this.key(),K.pref.bool('save_hist',true)?JSON.stringify(this.convs.filter(c=>c.msgs.length)):'[]')}catch(e){}},
 find(id){return this.convs.find(c=>c.id===id)||null},
 create(){return {id:'c'+Date.now().toString(36)+Math.random().toString(36).slice(2,6),title:'',ts:Date.now(),pinned:false,msgs:[]}},
 remove(c){const i=this.convs.indexOf(c);if(i>=0)this.convs.splice(i,1);this.save()}
};
K.toast=function(m){const t=document.getElementById('toast');if(!t)return;const d=document.createElement('div');d.textContent=m;t.appendChild(d);setTimeout(()=>d.remove(),2300)};
