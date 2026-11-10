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
 /* commit files to a repository as "Bawsala" (files: {path:text}) */
 async push(repo,files,msg){
  const out=[];for(const path in files){
   let sha;try{const r=await K.gh.api('/repos/'+repo+'/contents/'+encodeURI(path));sha=r.sha}catch(e){}
   const body={message:msg||'Bawsala: update',content:btoa(unescape(encodeURIComponent(files[path]))),committer:{name:'Bawsala',email:'bawsala@users.noreply.github.com'}};if(sha)body.sha=sha;
   const r=await fetch('https://api.github.com/repos/'+repo+'/contents/'+encodeURI(path),{method:'PUT',headers:{'Authorization':'Bearer '+K.gh.tok(),'Accept':'application/vnd.github+json','Content-Type':'application/json'},body:JSON.stringify(body)});
   if(!r.ok)throw new Error('HTTP '+r.status);out.push(path)}
  return out},
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
K.STACKS=[['web','HTML / CSS / JavaScript','Single-file web app (default)',''],
 ['kotlin','Kotlin — Android (Jetpack Compose)','Android app in Kotlin with Jetpack Compose, Gradle Kotlin DSL (build.gradle.kts, settings.gradle.kts), AndroidManifest.xml, MVVM, Room, Retrofit/Ktor'],
 ['swift','Swift — iOS (SwiftUI)','iOS app in Swift with SwiftUI, MVVM, async/await, SwiftData, Swift Package Manager'],
 ['flutter','Dart — Flutter (Android + iOS)','Flutter app in Dart: pubspec.yaml, lib/ folders (screens, widgets, models, services), Riverpod or Bloc'],
 ['rn','TypeScript — React Native (Expo)','React Native app with Expo and TypeScript: package.json, app.json, tsconfig.json, src/ folders, React Navigation'],
 ['react','TypeScript — React + Vite','Web app with React, TypeScript and Vite: package.json, tsconfig.json, vite.config.ts, src/ components, Tailwind CSS'],
 ['next','TypeScript — Next.js','Full-stack Next.js (App Router) with TypeScript, Tailwind, API routes'],
 ['node','TypeScript — Node.js (NestJS/Express)','Backend API in TypeScript with Node.js: package.json, tsconfig, src/ modules/controllers/services, validation, tests'],
 ['python','Python — FastAPI','Backend in Python with FastAPI: requirements.txt, app/ package (routers, models, schemas, services), pytest tests, Dockerfile'],
 ['go','Go','Backend in Go: go.mod, cmd/ and internal/ packages, HTTP handlers, tests, Dockerfile'],
 ['rust','Rust','Rust project with Cargo.toml, src/main.rs and modules, error handling with Result, tests'],
 ['java','Java — Spring Boot','Spring Boot backend in Java: pom.xml, controllers/services/repositories, JPA entities, tests']];
K.persona=function(){
 const p=K.pref,st=+p.get('reply_style','1'),lg=p.get('reply_lang','auto'),nm=p.get('nickname','').trim(),ab=p.get('about_me','').trim(),a=['Always be polite and respectful to the user: never insult, curse, or call them names (even jokingly, even in a dialect). Keep a warm, friendly tone.'];
const MP={enc:'',apps:'Role: senior mobile/app engineer. Help the user design and build apps (Android, iOS, Flutter, React Native): architecture, complete working code, debugging and publishing. Ask a short clarifying question when requirements are unclear.',biz:'Role: business strategist. Help the user plan a commercial project: idea validation, business model, market and competitors, pricing, marketing, simple financial projections and a step-by-step action plan.',web:'Role: senior web developer. Help build websites: HTML/CSS/JS and frameworks, responsive design, SEO, performance and hosting. Give complete, working code.'};
 const md=K.state&&K.state.cur&&K.state.cur.mode;if(md&&MP[md])a.push(MP[md]);{const st=K.STACKS.find(x=>x[0]===K.pref.get('dev_stack','web'));if(st&&st[0]!=='web'&&(md==='apps'||(K.intent&&K.state.cur&&K.state.cur.msgs.length&&K.intent.build((K.state.cur.msgs.filter(m=>m.role==='user').slice(-1)[0]||{}).c))))a.push('STACK: build with '+st[2]+'. This overrides the single-HTML rule. Output the whole project as ONE fenced block starting with ```app:<Project Name>, containing every file, each file introduced by a line exactly like === path/to/file.ext === . Include build/config files, a README.md with run instructions, and real production-quality code (no placeholders). The in-app preview only works for web stacks, so mention how to run it.')}if(K.state&&K.state.cur&&K.state.cur.enc)a.push(K.enc.persona(K.state.cur.enc));if(K.state&&K.state.cur&&K.state.cur.stu)a.push(K.students.persona(K.state.cur));
 if(nm)a.push('Address the user as "'+nm+'".');
 if(ab)a.push('About the user: '+ab);

 try{const m=JSON.parse(p.get('memories','[]'));if(m.length)a.push('Remember these facts about the user:\n'+m.map(x=>'- '+x).join('\n'))}catch(e){}
 a.push(['Keep answers short and to the point.','','Give detailed, well-structured answers with examples.'][st]||'');
 if(lg==='ar')a.push('Always answer in Arabic.');else if(lg==='en')a.push('Always answer in English.');
 return a.filter(Boolean).join('\n');
};
