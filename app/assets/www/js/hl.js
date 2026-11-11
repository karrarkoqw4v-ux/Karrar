/* Tiny syntax highlighter: colours keywords, strings, numbers, comments, functions, types per language */
window.K=window.K||{};
(function(){
const W=s=>new Set(s.split(/\s+/));
const KW={
 js:W('break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new of return super switch this throw try typeof var void while with yield async await static get set null undefined true false from as interface type enum implements public private protected readonly namespace declare abstract'),
 py:W('False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield self print match case'),
 java:W('abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var true false null record sealed permits'),
 kt:W('fun val var when object companion data sealed override open suspend inline class interface if else for while do return break continue try catch finally throw is in as null true false this super import package private public protected internal abstract final enum lateinit by init constructor typealias'),
 c:W('auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while class namespace template typename public private protected virtual override new delete this nullptr true false bool using try catch throw include define string var foreach foreach in is as null lock readonly async await get set'),
 go:W('break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var nil true false iota'),
 rs:W('as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while'),
 php:W('abstract and array as break callable case catch class clone const continue declare default do echo else elseif empty extends final finally fn for foreach function global if implements include instanceof interface isset list namespace new null or print private protected public require return static switch throw trait true false try use var while yield'),
 swift:W('associatedtype class deinit enum extension fileprivate func import init inout internal let open operator private protocol public static struct subscript typealias var break case continue default defer do else fallthrough for guard if in repeat return switch where while as catch false is nil rethrows super self throw throws true try async await'),
 dart:W('abstract as assert async await break case catch class const continue covariant default deferred do dynamic else enum export extends extension external factory false final finally for Function get hide if implements import in interface is late library mixin new null on operator part required rethrow return set show static super switch sync this throw true try typedef var void while with yield'),
 sql:W('select from where group by order having insert into values update set delete create table alter drop index view join inner left right outer full on as and or not null is in like between distinct limit offset union all case when then else end primary key foreign references default unique constraint int varchar text date timestamp boolean count sum avg min max asc desc exists'),
 sh:W('if then else elif fi for while do done case esac function in return exit echo cd ls cat grep sed awk export source local read set unset sudo apt pip npm git curl chmod mkdir rm cp mv true false'),
 rb:W('alias and begin break case class def defined do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield puts require attr_accessor')
};
const ALIAS={javascript:'js',js:'js',jsx:'js',ts:'js',typescript:'js',tsx:'js',node:'js',mjs:'js',python:'py',py:'py',java:'java',kotlin:'kt',kt:'kt',kts:'kt',c:'c',h:'c',cpp:'c','c++':'c',cc:'c',hpp:'c',cs:'c',csharp:'c','c#':'c',go:'go',golang:'go',rust:'rs',rs:'rs',php:'php',swift:'swift',dart:'dart',sql:'sql',bash:'sh',sh:'sh',shell:'sh',zsh:'sh',ruby:'rb',rb:'rb',json:'json',jsonc:'json',yaml:'yaml',yml:'yaml',html:'html',xml:'html',svg:'html',vue:'html',css:'css',scss:'css',less:'css',gradle:'java',toml:'yaml',ini:'yaml',properties:'yaml'};
const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const sp=(c,t)=>'<span class="tk-'+c+'">'+esc(t)+'</span>';
function generic(code,L){
 const hashC=L==='py'||L==='sh'||L==='rb'||L==='yaml',dashC=L==='sql';
 const cm=hashC?'#[^\\n]*':dashC?'--[^\\n]*|\\/\\*[\\s\\S]*?\\*\\/':'\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?\\*\\/';
 const str=(L==='py'?'"""[\\s\\S]*?"""|\'\'\'[\\s\\S]*?\'\'\'|':'')+'"(?:\\\\.|[^"\\\\\\n])*"|\'(?:\\\\.|[^\'\\\\\\n])*\'|`(?:\\\\.|[^`\\\\])*`';
 const re=new RegExp('('+cm+')|('+str+')|(\\b0x[0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)|([A-Za-z_$][\\w$]*)|(\\s+)|([\\s\\S])','g');
 const kw=KW[L]||KW.js,ci=L==='sql';let out='',m,prev='';
 while((m=re.exec(code))){
  if(m[1])out+=sp('c',m[1]);
  else if(m[2])out+=(L==='yaml'&&/^\s*:/.test(code.slice(re.lastIndex,re.lastIndex+3)))?sp('p',m[2]):sp('s',m[2]);
  else if(m[3])out+=sp('n',m[3]);
  else if(m[4]){const w=m[4];const nx=code.charAt(re.lastIndex);
   if(kw.has(ci?w.toLowerCase():w))out+=sp('k',w);
   else if(prev==='.'&&nx!=='(')out+=sp('p',w);
   else if(nx==='(')out+=sp('f',w);
   else if(/^[A-Z][A-Za-z0-9_]*$/.test(w)&&w.length>1&&w!==w.toUpperCase())out+=sp('t',w);
   else if(L==='yaml'&&nx===':')out+=sp('p',w);
   else out+=esc(w);
   prev='';continue}
  else if(m[5])out+=esc(m[5]);
  else{const ch=m[6];out+=/[{}()\[\];,.:<>=+\-*\/%!&|^~?]/.test(ch)?'<span class="tk-o">'+esc(ch)+'</span>':esc(ch);prev=ch;continue}
  prev=''
 }
 return out;
}
function json(code){
 return code.replace(/("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|([^"\-\dtfn]+|[\s\S])/g,(m,s,colon,kw,n,o)=>s?(colon?sp('p',s)+esc(colon):sp('s',s)):kw?sp('k',kw):n?sp('n',n):esc(o||m));
}
function html(code){
 const re=/(<!--[\s\S]*?-->)|(<\/?)([A-Za-z][\w:.-]*)|("[^"]*"|'[^']*')|([\w:@.-]+)(?==)|(\/?>)|([\s\S])/g;let m,out='',inTag=false;
 while((m=re.exec(code))){
  if(m[1])out+=sp('c',m[1]);
  else if(m[2]){inTag=true;out+=sp('o',m[2])+sp('g',m[3])}
  else if(m[4])out+=inTag?sp('s',m[4]):esc(m[4]);
  else if(m[5])out+=inTag?sp('p',m[5]):esc(m[5]);
  else if(m[6]){inTag=false;out+=sp('o',m[6])}
  else out+=esc(m[7]);
 }
 return out;
}
function css(code){
 return code.replace(/(\/\*[\s\S]*?\*\/)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|(#[0-9a-fA-F]{3,8}\b)|(@[\w-]+)|(-?\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|s|ms|deg|fr)?\b)|([\w-]+)(?=\s*:)|([.#][\w-]+|\b[a-z][\w-]*(?=[\s,{.:#\[>+~]*[,{]))|([\s\S])/g,(m,c,s,hx,at,n,p,sel,o)=>c?sp('c',c):s?sp('s',s):hx?sp('n',hx):at?sp('k',at):n?sp('n',n):p?sp('p',p):sel?sp('t',sel):esc(o));
}
K.hl=function(code,lang){
 const L=ALIAS[(lang||'').toLowerCase().split(/[\s:{]/)[0]];
 if(code.length>30000||!L)return esc(code);
 try{return L==='json'?json(code):L==='html'?html(code):L==='css'?css(code):generic(code,L)}catch(e){return esc(code)}
};
})();
