/* URBO speech for NyxVision: Braitisk IPA engine + speech (eSpeak NG, GPL-3.0, in tts/) */
window.D=window.D||{};(function(){try{const x=new XMLHttpRequest();x.open('GET','tts/braitisk-speech.json',false);x.send();if(x.status==200||x.status==0)window.D.braitisk=JSON.parse(x.responseText)}catch(e){}})();
/* Braitisk orthography → IPA (JavaScript twin of braitisk_ipa.py; same rule table from ipa_rules.json) */
const BTIPA=(()=>{let R=null,FORMS=null,EXC=null,VTH=null,STATUS=null;const FRONT=new Set('eiyéèêëîïæ'),VOW=new Set('aeiouyâêîôûàèéëïöüåäœòù');
 function init(){if(R)return;const G=D.braitisk.g2p;R=G.rules.map(r=>[r.grapheme,r.ipa,r.status,r.rule,r.context]).map((r,i)=>[...r,i]).sort((a,b)=>b[0].length-a[0].length||a[5]-b[5]);
  STATUS={};G.rules.forEach(r=>STATUS[r.rule]=r.status);if(STATUS['S-th']==='approved')STATUS['S-th-voiced']='approved';EXC=G.exceptions;VTH=new Set(G.voiced_th);const END=new Map(G.endings);
  FORMS={};Object.entries(G.verbs).forEach(([inf,t])=>{const st=inf.slice(0,-2);if(!(inf in FORMS))FORMS[inf]=[st,'eʁ',''];
   ['present','past','subjunctive','future','conditional'].forEach(tn=>t[tn].forEach(f=>{const base=['future','conditional'].includes(tn)?inf:st;const e=f.slice(base.length);if(END.has(e)&&f.startsWith(base)&&!(f in FORMS))FORMS[f]=[base,END.get(e),tn]}));
   t.imperative.forEach(f=>{const e=f.slice(st.length);if(END.has(e)&&!(f in FORMS))FORMS[f]=[st,END.get(e),'present']})})}
 function ok(ctx,w,i,g){const n=w[i+g.length]||'',p=i>0?w[i-1]:'';switch(ctx){case'':return true;case'V':return VOW.has(n);case'nV':return !VOW.has(n);case'F':return FRONT.has(n);case'nF':return !FRONT.has(n);case'$':return n==='';case'CV':return p!==''&&!VOW.has(p)&&VOW.has(n)}return true}
 function letters(w,stop){stop=stop??w.length;let i=0;const out=[],used=[];while(i<stop){let hit=false;for(const [g,ipa,st,rid,ctx] of R){if(w.startsWith(g,i)&&ok(ctx,w,i,g)){out.push(ipa);used.push(rid);i+=g.length;hit=true;break}}
  if(!hit){if("-’'".includes(w[i])){i++;continue}out.push(w[i]);used.push('UNKNOWN:'+w[i]);i++}}return[out.join(''),used]}
 function word(w){w=w.normalize('NFC').toLowerCase();if(w in EXC)return[EXC[w],['EXC:'+w]];
  if(w in FORMS){const [base,e,tn]=FORMS[w];const stem=['future','conditional'].includes(tn)?base.slice(0,-2):base;const [b,u]=letters(w,stem.length);return[b+(stem!==base?'eʁ':'')+e,[...u,'E-ending']]}
  let [ipa,used]=letters(w);if(VTH.has(w)){ipa=ipa.replace(/θ/g,'ð');used=used.map(x=>x==='S-th'?'S-th-voiced':x)}return[ipa,used]}
 function phrase(t){init();const toks=t.normalize('NFC').replace(/-/g,' ').match(/[\p{L}]+(?:[’'][\p{L}]+)?|\d+/gu)||[];const parts=[],used=[];
  toks.forEach(k=>{k=k.replace(/[’']/g,'');if(/^\d+$/.test(k))return;const [p,u]=word(k);parts.push(p);used.push(...u)});
  const flags=[...new Set(used.filter(u=>u.startsWith('UNKNOWN')||STATUS[u]==='proposed'||(u==='S-th-voiced'&&STATUS['S-th']!=='approved')))].sort();
  const exc=toks.length===1&&used.length&&used[0].startsWith('EXC:');return{ipa:'/'+parts.join(' ')+'/',status:exc?'established':flags.length?'provisional':'rule-derived',flags}}
 return{phrase,desc:r=>D.braitisk.g2p.desc[r]||r}})();

/* URBO speech: Braitisk text-to-speech built on the orthography→IPA engine (BTIPA).
   Two engines:
   - "natural": the device's own voices (Web Speech). Braitisk is read through a French respelling generated from the IPA.
   - "exact": eSpeak NG (WebAssembly, GPL-3.0) fed the IPA itself as phonemes, so every device sounds the same.
   English and the other official languages use the device voice for that language, with eSpeak NG as the fallback.
   Station voices are unnamed presets; none is an in-world character. */
const BPTTS=(()=>{
 const BASE=(()=>{try{const s=document.currentScript&&document.currentScript.src;if(s)return s.replace(/[^/]*$/,'')}catch(e){}return ''})();
 let TTSDIR=BASE+'tts/';
 /* ---------- IPA → eSpeak NG phonemes (French phoneme table; every symbol checked against eSpeak's own phoneme events) ---------- */
 const PH=[['tʃ','tS'],['dʒ','dZ'],['ai̯','aj'],['aʊ','aw'],['oʊ','ow'],['eɪ','ej'],['ɔɪ','Oj'],['øy̯','Yy-'],['ɥ','y-'],
  ['i','i'],['y','y'],['u','u'],['e','e'],['ɛ','E'],['ø','Y'],['œ','W'],['ə','@'],['a','a'],['o','o'],['ɔ','O'],['ɪ','I'],['ʊ','u'],
  ['p','p'],['b','b'],['t','t'],['d','d'],['k','k'],['ɡ','g'],['g','g'],['f','f'],['v','v'],['θ','T'],['ð','D'],['s','s'],['z','z'],
  ['ʃ','S'],['ʒ','Z'],['h','h'],['ʁ','r'],['m','m'],['n','n'],['ŋ','N'],['ɲ','n^'],['l','l'],['j','j'],['w','w'],['ˈ','ˈ']].sort((a,b)=>b[0].length-a[0].length);
 const VOWELS=new Set(['ai̯','aʊ','oʊ','eɪ','ɔɪ','øy̯','i','y','u','e','ɛ','ø','œ','ə','a','o','ɔ','ɪ','ʊ']);
 function toks(ipa){const out=[];let i=0;ipa=ipa.normalize('NFC');while(i<ipa.length){let hit=false;for(const [k] of PH){if(ipa.startsWith(k,i)){out.push(k);i+=k.length;hit=true;break}}if(!hit)i++}return out}
 const PHM=new Map(PH);
 function wordEspeak(ipa){const t=toks(ipa);let out='',stress=false;for(let x=0;x<t.length;x++){const k=t[x];if(k==='ˈ'){stress=true;continue}if(stress&&VOWELS.has(k)){out+="'";stress=false}out+=PHM.get(k);if(k==='i'&&VOWELS.has(t[x+1]))out+='j'}return out}
 function ipaWords(text){const r=BTIPA.phrase(text);return r.ipa.replace(/^\/|\/$/g,'').split(' ').filter(Boolean)}
 /* Braitisk sentence → eSpeak input: phonemes in [[ ]], punctuation kept outside for pauses and intonation */
 function espeakInput(text){return splitClauses(text).map(([c,p])=>{const w=ipaWords(c).map(wordEspeak).filter(Boolean);return w.length?'[['+w.join(' ')+']]'+(p||''):''}).filter(Boolean).join(' ')}
 function splitClauses(text){const out=[];const rx=/([^.,;:!?…]+)([.,;:!?…]*)/g;let m;while((m=rx.exec(text)))if(m[1].trim())out.push([m[1].trim(),m[2]]);return out}
 /* ---------- IPA → French respelling (for device voices; an approximation by design) ---------- */
 const FR={i:'i',y:'u',u:'ou',e:'é',ɛ:'è',ø:'eu',œ:'eu',a:'a',o:'ô',ɔ:'o','oʊ':'ô','aʊ':'aou','ai̯':'aï','eɪ':'éï','ɔɪ':'oï','øy̯':'euï',ɪ:'i',ʊ:'ou',
  p:'p',b:'b',t:'t',d:'d',k:'k',f:'f',v:'v',θ:'s',ð:'z',z:'z',ʃ:'ch',ʒ:'j',h:'h',ʁ:'r',m:'m',n:'n',ŋ:'ng',ɲ:'gn',l:'l',w:'ou',ɥ:'u','tʃ':'tch','dʒ':'dj'};
 function wordFrench(ipa){const t=toks(ipa).filter(k=>k!=='ˈ');let s='';
  t.forEach((k,i)=>{const p=t[i-1],n=t[i+1],pv=p&&VOWELS.has(p),nv=n&&VOWELS.has(n);let o;
   if(k==='ə')o=i===t.length-1?(t.length<=2?'e':'eu'):'e';
   else if(k==='i'||k==='y')o=(pv?(k==='i'?'ï':'ü'):FR[k]);
   else if(k==='s')o=(pv&&nv)?'ss':'s';
   else if(k==='ɡ'||k==='g')o=(n&&['i','e','ɛ','y','ə','ɪ','eɪ'].includes(n))?'gu':'g';
   else if(k==='k')o=(n==='w')?'k':'k';
   else if(k==='t')o=(n==='i'||n==='j')?'th':'t';
   else if(k==='j')o=pv?'y':(i===0?'y':'i');
   else if((k==='n'||k==='m')&&pv&&!nv)o=k+k;
   else o=FR[k]||'';
   s+=o});
  const last=t[t.length-1];if(last&&!VOWELS.has(last))s+='e';return s}
 function frenchInput(text){return splitClauses(text).map(([c,p])=>ipaWords(c).map(wordFrench).join(' ')+(p||'')).join(' ')}
 /* ---------- mixed text: which parts are Braitisk ---------- */
 let BPRX=null;
 function bpPhrases(){if(BPRX)return BPRX;let DD=null;try{DD=D}catch(e){DD=window.D}const L=(DD&&DD.braitisk&&DD.braitisk.labels)||window.BPW||[];const vals=[...new Set(L.map(x=>x[1]))].filter(v=>v&&v.length>1).sort((a,b)=>b.length-a.length);
  const ex=x=>x.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');BPRX=vals.length?new RegExp('(?<![\\p{L}])('+vals.map(ex).join('|')+')(?![\\p{L}])','gu'):null;return BPRX}
 /* split text shown in Braitisk mode into [{lang:'bp'|'en',text}] using the approved site labels that were substituted */
 function segments(text,isBp){if(!isBp)return[{lang:'en',text}];const rx=bpPhrases();if(!rx)return[{lang:'en',text}];const out=[];let last=0;text.replace(rx,(m,_g,off)=>{if(off>last)out.push({lang:'en',text:text.slice(last,off)});out.push({lang:'bp',text:m});last=off+m.length;return m});
  if(last<text.length)out.push({lang:'en',text:text.slice(last)});
  const merged=[];out.forEach(s=>{if(!/[\p{L}\d]/u.test(s.text)){if(merged.length)merged[merged.length-1].text+=s.text;return}const p=merged[merged.length-1];if(p&&p.lang===s.lang)p.text+=s.text;else merged.push({...s})});return merged}
 /* ---------- text clean-up for broadcast English ---------- */
 function cleanEn(t){return t.replace(/[\u{1F000}-\u{1FFFF}☀-➿️]/gu,' ').replace(/(\d)\s*°/g,'$1 degrees').replace(/\bmph\b/g,'miles per hour').replace(/(\d)\s*%/g,'$1 percent').replace(/\s*[•·|]\s*/g,'. ').replace(/\s+/g,' ').trim()}
 /* ---------- station voices (unnamed presets) ---------- */
 const VOICES={dictionary:{label:'Dictionary voice',rate:0.85,pitch:1,es:{v:'fr',rate:140,pitch:50}},
  news:{label:'UBN News voice',rate:1,pitch:1,es:{v:'fr+f2',rate:165,pitch:55}},
  bnn:{label:'BNN voice',rate:1.02,pitch:0.95,es:{v:'fr+m3',rate:170,pitch:45}},
  sport:{label:'UBN Sport voice',rate:1.1,pitch:1.05,es:{v:'fr+m7',rate:185,pitch:50}},
  radio:{label:'Radio voice',rate:1,pitch:0.9,es:{v:'fr+m2',rate:160,pitch:40}},
  alert:{label:'Alert voice',rate:0.9,pitch:1,es:{v:'fr+f3',rate:145,pitch:50}}};
 const LANGTAG={en:'en-US',fr:'fr-FR',es:'es-ES',nl:'nl-NL',gd:'gd-GB',bp:'fr-FR'};
 const ESLANG={en:'en-us',fr:'fr',es:'es',nl:'nl',gd:'gd'};
 /* ---------- engines ---------- */
 const SS=typeof speechSynthesis!=='undefined'?speechSynthesis:null;
 function voicesFor(tag){if(!SS)return[];const v=SS.getVoices();const l=tag.toLowerCase(),b=l.split('-')[0];return v.filter(x=>x.lang.toLowerCase().replace('_','-')===l).concat(v.filter(x=>x.lang.toLowerCase().replace('_','-').split('-')[0]===b&&x.lang.toLowerCase().replace('_','-')!==l))}
 if(SS&&SS.addEventListener)SS.addEventListener('voiceschanged',()=>{});
 let ES=null,ESW=null,AC=null;
 async function espeak(){if(ESW)return ESW;if(!ES)ES=(async()=>{const dir=new URL(TTSDIR,location.href).href;const mod=await import(dir+'espeak-ng.js');const m=await mod.default({urboDataURL:dir+'espeak-ng-urbo.bin'});ESW=new m.eSpeakNGWorker();return ESW})();return ES}
 function ctx(){if(!AC){const C=window.AudioContext||window.webkitAudioContext;AC=new C()}if(AC.state==='suspended')AC.resume();return AC}
 let token=0,curSrc=null;
 async function playEspeak(input,esv,lang,my){const w=await espeak();if(my!==token)return;w.set_voice(lang==='bp'?esv.v:(ESLANG[lang]||'en-us')+(esv.v.includes('+')?esv.v.slice(esv.v.indexOf('+')):''),'',0,0,'');w.set_rate(esv.rate);w.set_pitch(esv.pitch);
  const chunks=[];w.synthesize(input,(d)=>{if(d&&d.length)chunks.push(d);return 0});const n=chunks.reduce((a,c)=>a+c.length,0);if(!n||my!==token)return;
  const A=ctx(),buf=A.createBuffer(1,n,w.samplerate),ch=buf.getChannelData(0);let o=0;for(const c of chunks){for(let i=0;i<c.length;i++)ch[o+i]=c[i]/32768;o+=c.length}
  await new Promise(res=>{const s=A.createBufferSource();s.buffer=buf;s.connect(A.destination);s.onended=()=>{if(curSrc===s)curSrc=null;res()};curSrc=s;s.start()})}
 function playDevice(text,tag,vo,my){return new Promise(res=>{const vs=voicesFor(tag);if(!vs.length)return res(false);const u=new SpeechSynthesisUtterance(text);u.lang=tag;try{u.voice=vs[0]}catch(e){}u.rate=vo.rate;u.pitch=vo.pitch;let done=false;const fin=ok=>{if(!done){done=true;res(ok)}};u.onend=()=>fin(true);u.onerror=()=>fin(true);SS.speak(u);
   const t=setInterval(()=>{if(my!==token){clearInterval(t);fin(true)}else if(!SS.speaking&&!SS.pending){clearInterval(t);fin(true)}},400)})}
 /* ---------- settings ---------- */
 const LS='bptts-mode';
 function mode(){try{return localStorage.getItem(LS)||'auto'}catch(e){return 'auto'}}
 function setMode(m){try{localStorage.setItem(LS,m)}catch(e){}}
 function useExact(context){const m=mode();if(m==='exact')return true;if(m==='natural')return !voicesFor('fr-FR').length;return context==='learn'||!voicesFor('fr-FR').length}
 /* ---------- public API ---------- */
 let onstate=()=>{},busy=false;
 function stop(){token++;busy=false;if(SS)SS.cancel();try{curSrc&&curSrc.stop()}catch(e){}curSrc=null;onstate(false)}
 /* items: [{lang,text}] ; opts: {voice, context:'learn'|'media'} */
 async function say(items,opts={}){stop();const my=++token;busy=true;onstate(true);const vo=VOICES[opts.voice||'dictionary']||VOICES.dictionary;const exact=useExact(opts.context||'media');
  if(SS)SS.getVoices();
  try{for(const it of items){if(my!==token)break;const text=(it.text||'').trim();if(!text)continue;
    if(it.lang==='bp'){if(exact){await playEspeak(espeakInput(text),vo.es,'bp',my)}else{const ok=await playDevice(frenchInput(text),'fr-FR',vo,my);if(!ok)await playEspeak(espeakInput(text),vo.es,'bp',my)}}
    else{const t=it.lang==='en'?cleanEn(text):text.replace(/[\u{1F000}-\u{1FFFF}☀-➿️]/gu,' ');if(!/[\p{L}\d]/u.test(t))continue;const ok=await playDevice(t,LANGTAG[it.lang]||'en-US',vo,my);if(!ok)await playEspeak(t,vo.es,it.lang,my)}}}
  catch(e){console.warn('BPTTS',e)}
  if(my===token){busy=false;onstate(false)}}
 function sayBp(text,opts){return say([{lang:'bp',text}],Object.assign({context:'learn'},opts))}
 return{say,sayBp,stop,segments,espeakInput,frenchInput,wordEspeak,wordFrench,VOICES,mode,setMode,
  setDir:d=>{TTSDIR=d},busy:()=>busy,hasDevice:t=>voicesFor(t||'fr-FR').length>0,onState:f=>{onstate=f},cleanEn,
  ready:()=>espeak().then(()=>true,()=>false)}})();


/* ---- URBO speech controls (shared by the site and NyxVision) ---- */
const TTSUI=(()=>{
 const LANGCODE={English:'en',French:'fr',Spanish:'es',Dutch:'nl','Scottish Gaelic':'gd'};
 let CFG={};const isBp=()=>CFG.isBp?CFG.isBp():(typeof TERR_LANG!=='undefined'&&TERR_LANG==='bp');
 let curBtn=null;
 BPTTS.onState(on=>{document.querySelectorAll('.saying').forEach(b=>{b.classList.remove('saying');b.removeAttribute('aria-pressed')});if(on&&curBtn){curBtn.classList.add('saying');if(curBtn.matches('[data-uwsay]'))curBtn.setAttribute('aria-pressed','true')}if(!on)curBtn=null});
 /* visible text with spaces between inline pieces and line breaks between blocks */
 function domText(el){const out=[];(function walk(n){if(n.nodeType===3){out.push(n.nodeValue);return}if(n.nodeType!==1)return;const cs=getComputedStyle(n);if(cs.display==='none'||cs.visibility==='hidden'||n.matches('svg,img,canvas,script,style,thead,.bnn-sim,.say,.sayl'))return;
   const d=cs.display,blk=!/^inline/.test(d)||d==='inline-block'||d==='inline-flex';const cell=d==='table-cell';if(blk&&!cell)out.push('\n');[...n.childNodes].forEach(c=>{walk(c);if(c.nodeType===1)out.push(cell?' ':' ')});if(cell)out.push(', ');if(blk&&!cell)out.push('\n')})(el);
  return out.join('').split('\n').map(l=>l.replace(/\s+/g,' ').replace(/(,\s*)+$/,'').replace(/^\s*,\s*/,'').trim()).filter(l=>/[\p{L}\d]/u.test(l)).join('. ').replace(/\.\s*\./g,'.')}
 function items(text){return BPTTS.segments(text,isBp())}
 /* what is on the TV stage right now */
 function stageRoot(){return document.querySelector('.uwstage>div')||[...document.querySelectorAll('#bnn,#usport,#utr,#uwx')].find(e=>e.offsetParent!==null&&e.offsetWidth>0)}
 function stageRead(){const root=stageRoot();if(!root||!root.isConnected){const osd=document.querySelector('#tvscr');return osd?{voice:'news',text:domText(osd)}:null}
  const id=root.id,q=s=>root.querySelector(s);
  if(id==='bnn'){const st=q('.bnn-story');return{voice:'bnn',text:st?domText(st):''}}
  if(id==='usport'){return{voice:'sport',text:[q('.us-seg'),q('.us-main')].filter(Boolean).map(domText).join('. ')}}
  const ttl=q('.uwx-ttl')||q('.uwx-logo');return{voice:'news',text:[ttl,q('.uwx-panel'),q('.utr-board')].filter(Boolean).map(domText).join('. ')}}
 let last='',auto=false,mo=null,tmo=null;
 function readStage(){const r=stageRead();if(!r||!r.text)return;last=r.text;BPTTS.say(items(r.text),{voice:r.voice,context:'media'})}
 function watch(){if(mo)mo.disconnect();mo=null;if(!auto)return;const r0=stageRoot(),w=document.querySelector('.uwstage')||document.querySelector('#tvscr')||(r0&&r0.parentElement);if(!w)return;
  mo=new MutationObserver(()=>{clearTimeout(tmo);tmo=setTimeout(tick,700)});mo.observe(w,{subtree:true,childList:true,characterData:true})}
 function tick(){if(!auto)return;const r=stageRead();if(!r||!r.text||r.text===last)return;if(BPTTS.busy()){clearTimeout(tmo);tmo=setTimeout(tick,800);return}curBtn=document.querySelector('[data-uwsay]');readStage()}
 function setAuto(on){auto=on;document.querySelectorAll('[data-uwauto]').forEach(c=>c.checked=on);if(on){curBtn=document.querySelector('[data-uwsay]');readStage()}watch()}
 function radioRead(){if(typeof MD!=='function')return;const x=MD().radio[window.__rd.i];return [x.name,x.fmt,x.market].filter(Boolean).join('. ')}
 function alertRead(){if(typeof ALTXT==='undefined')return null;const A=window.__al,t=(ALTXT[A.kind]||{})[A.lang];if(!t||!A.on)return null;return{lang:LANGCODE[A.lang]||'en',text:t[0]+'. '+A.area+'. '+t[1]}}
 document.addEventListener('click',e=>{const tg=e.target;if(!tg.closest)return;
  const s=tg.closest('[data-say]');if(s){e.preventDefault();e.stopPropagation();if(curBtn===s){BPTTS.stop();return}curBtn=s;BPTTS.sayBp(s.dataset.say,{voice:'dictionary'});return}
  const m=tg.closest('[data-ttsmode]');if(m){BPTTS.setMode(m.dataset.ttsmode);document.querySelectorAll('[data-ttsmode]').forEach(b=>b.setAttribute('aria-pressed',b===m));return}
  const u=tg.closest('[data-uwsay]');if(u){e.preventDefault();if(curBtn===u){BPTTS.stop();if(auto)setAuto(false);return}curBtn=u;readStage();return}
  const r=tg.closest('[data-rdsay]');if(r){e.preventDefault();if(curBtn===r){BPTTS.stop();return}curBtn=r;BPTTS.say(items(radioRead()),{voice:'radio',context:'media'});return}
  const a=tg.closest('[data-alsay]');if(a){e.preventDefault();if(curBtn===a){BPTTS.stop();return}const t=alertRead();if(!t)return;curBtn=a;BPTTS.say([t],{voice:'alert',context:'media'});return}},true);
 document.addEventListener('change',e=>{if(e.target.matches&&e.target.matches('[data-uwauto]'))setAuto(e.target.checked)});
 addEventListener('hashchange',()=>{BPTTS.stop();last='';if(auto)setTimeout(watch,300)});
 return{readStage,setAuto,domText,stageRead,watch,auto:()=>auto,toggleAuto:()=>{setAuto(!auto);return auto},config:c=>{CFG=Object.assign(CFG,c)}}})();

window.BPTTS=BPTTS;window.TTSUI=TTSUI;
