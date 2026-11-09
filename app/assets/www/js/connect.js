/* Integrations: GitHub (token based) — list repositories and use them as chat context */
window.K=window.K||{};
K.gh={
 tok(){return K.pref.get('gh_token','')},
 sel(){try{return JSON.parse(K.pref.get('gh_sel','[]'))}catch(e){return []}},
 setSel(a){K.pref.set('gh_sel',JSON.stringify(a))},
 toggle(full){const s=K.gh.sel(),i=s.indexOf(full);if(i>=0)s.splice(i,1);else s.push(full);K.gh.setSel(s);return i<0},
 async api(path,raw){
  const r=await fetch('https://api.github.com'+path,{headers:{'Authorization':'Bearer '+K.gh.tok(),'Accept':raw?'application/vnd.github.raw':'application/vnd.github+json'}});
  if(!r.ok)throw new Error('HTTP '+r.status);return raw?r.text():r.json()},
 user(){return K.gh.api('/user')},
 repos(){return K.gh.api('/user/repos?sort=updated&per_page=40')},
 /* text block with README + top-level files for the attached repositories */
 async context(names){
  const out=[];
  for(const n of names.slice(0,3)){
   let readme='',files='';
   try{readme=(await K.gh.api('/repos/'+n+'/readme',true)).slice(0,5000)}catch(e){}
   try{const l=await K.gh.api('/repos/'+n+'/contents/');files=l.map(f=>f.name+(f.type==='dir'?'/':'')).slice(0,80).join(', ')}catch(e){}
   out.push('GitHub repository: '+n+'\nTop-level files: '+(files||'(unavailable)')+'\nREADME:\n'+(readme||'(none)'));
  }
  return out.join('\n\n---\n\n');
 }
};

/* system-prompt additions from Settings -> Personalization */
K.persona=function(){
 const p=K.pref,st=+p.get('reply_style','1'),lg=p.get('reply_lang','auto'),nm=p.get('nickname','').trim(),ab=p.get('about_me','').trim(),a=[];
const MP={enc:'',apps:'Role: senior mobile/app engineer. Help the user design and build apps (Android, iOS, Flutter, React Native): architecture, complete working code, debugging and publishing. Ask a short clarifying question when requirements are unclear.',biz:'Role: business strategist. Help the user plan a commercial project: idea validation, business model, market and competitors, pricing, marketing, simple financial projections and a step-by-step action plan.',web:'Role: senior web developer. Help build websites: HTML/CSS/JS and frameworks, responsive design, SEO, performance and hosting. Give complete, working code.'};
 const md=K.state&&K.state.cur&&K.state.cur.mode;if(md&&MP[md])a.push(MP[md]);if(K.state&&K.state.cur&&K.state.cur.enc)a.push(K.enc.persona(K.state.cur.enc));
 if(nm)a.push('Address the user as "'+nm+'".');
 if(ab)a.push('About the user: '+ab);

 try{const m=JSON.parse(p.get('memories','[]'));if(m.length)a.push('Remember these facts about the user:\n'+m.map(x=>'- '+x).join('\n'))}catch(e){}
 a.push(['Keep answers short and to the point.','','Give detailed, well-structured answers with examples.'][st]||'');
 if(lg==='ar')a.push('Always answer in Arabic.');else if(lg==='en')a.push('Always answer in English.');
 return a.filter(Boolean).join('\n');
};
