/* URBO Sport: the UBA season engine and the URBO Sport channel (NyxVision and the URBO portal).
   The season is canon (Bailey, 2026-11-27): every game is generated from a fixed seed, so every device shows
   the same schedule, live scores, results and standings. Seasons 119 onward are simulated; earlier history is fixed lore.
   Calendar: Nyrethian Standard Calendar. Anchor 08/15/0119 0.00 Brightport Time = 2026-11-26T05:00Z. 1 Nyreth day = 26 Earth hours. */
window.UBA_SIM=(function(){
const T=[
 ['royals','Brightport City Royals','Royals','BCR','Brightport','Brightport City','Souvren Arena',32000,11,'#213a74','#e6bf45',0,7.6],
 ['titans','Aurelia Titans','Titans','AUR','Brightport','Aurelia','Aurelia Coliseum',16000,0,'#512a78','#c6aa68',0,5.0],
 ['tides','Trempas Tides','Tides','TRE','Brightport','Trempas','Trempas Ocean Arena',21000,5,'#168b92','#f58b77',0,8.3],
 ['crystals','Portcrystal Crystals','Crystals','POR','Brightport','Portcrystal','Sapphire Arena',12000,2,'#2459a6','#c2dfed',1,5.8],
 ['vikings','Bikbergen Vikings','Vikings','BIK','Brightport','Bikbergen','Bikbergen Nordic Arena',18500,4,'#203e58','#d6e5ed',0,6.3],
 ['cobras','Mami City Cobras','Cobras','MAM','Brightport','Mami City','La Arena del Sol',22000,3,'#a84c2b','#e6c29b',1,6.5],
 ['admirals','Silverstone Admirals','Admirals','SIL','Brightport','Silverstone','Silver Dome',23500,7,'#172c44','#c8cdd4',0,7.0],
 ['guardians','Ft. Deserto Guardians','Guardians','FTD','Brightport','Ft. Deserto','The Fortress',8500,1,'#4b5844','#c8ae80',1,4.8],
 ['lions','Berevo Lions','Lions','BER','Occasue','Berevo','De Zonarena',25000,11,'#a51e2d','#f4bb36',2,8.5],
 ['breakers','Costal City Breakers','Breakers','COS','Occasue','Costal City','Oceanfront Coliseum',23000,2,'#12678b','#e0f1ee',2,6.6],
 ['eclairs','Belkhap Éclairs','Éclairs','BEL','Occasue','Belkhap','Arène de Belkhap',21000,3,'#633363','#e6c36a',2,6.8],
 ['highlanders','Lochwater Highlanders','Highlanders','LOC','Occasue','Lochwater','Highland Arena',16500,2,'#234b3b','#b6c7d8',1,5.6],
 ['mustangs','Polvo Springs Mustangs','Mustangs','POL','Occasue','Polvo Springs','Mustang Arena',9500,0,'#a95f31','#242424',1,4.5],
 ['summits','Highpass Summits','Summits','HIG','Occasue','Highpass','Summit Arena',8000,0,'#496274','#e6ecf0',1,4.0],
 ['lynx','Swisswa Lynx','Lynx','SWI','Occasue','Swisswa','Schoon Heritage Arena',9000,1,'#187e8b','#171717',2,5.0],
 ['wolves','Dunellan Wolves','Wolves','DUN','Occasue','Dunellan','Wolf Den Arena',10000,0,'#85898f','#171717',1,4.3]
].map(r=>({id:r[0],name:r[1],nick:r[2],abbr:r[3],conf:r[4],city:r[5],arena:r[6],cap:r[7],titles:r[8],c1:r[9],c2:r[10],zone:r[11],base:r[12]}));
const BY={};T.forEach(t=>BY[t.id]=t);
const A0=Date.UTC(2026,10,26,5),DAYMS=93600000,BASEDAY=118*300+7*30+14; // absolute NSC day index of the anchor (day 0 = Primus 1, year 1)
const FIRST=119,ZN=['BT','MT','OT'];
const absDay=t=>BASEDAY+(t-A0)/DAYMS, timeOf=d=>A0+(d-BASEDAY)*DAYMS;
function h32(){let h=2166136261;for(const a of arguments){const s=String(a);for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}h^=0x9e3779b9;h=Math.imul(h^(h>>>15),2246822507)}return h>>>0}
function rng(seed){let a=seed>>>0;return()=>{a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
function gauss(r){let u=0,v=0;while(!u)u=r();while(!v)v=r();return Math.sqrt(-2*Math.log(u))*Math.cos(2*Math.PI*v)}
// Season Y: regular season Septimus 1 – Decimus 30 of year Y (one round every two days, 8 games a round),
// All-Star Game Nonus 13, playoffs from Primus 1 of year Y+1 (a game every two days), Finals in Secundus.
const regStart=Y=>(Y-1)*300+180, poStart=Y=>Y*300;
const TIP=[7.4,7.9],DUR=0.095; // local tip-off in Nyreth decimal hours; a game lasts about 0.95 Nyreth hours (2 h 28 min)
const cache={};
function ratings(Y){const r=rng(h32('rate',Y));const o={};T.forEach(t=>o[t.id]=t.base+gauss(r)*0.45);return o}
function tipTime(day,home,slot){const z=BY[home].zone;return timeOf(day+(TIP[slot]-z)/10)} // local time in the home team's zone
function scoreGame(Y,key,home,away,R){const r=rng(h32('g',Y,key));const d=(R[home]-R[away])*3.1+2.6+gauss(r)*11.5;
  let tot=214+gauss(r)*13;let hs=Math.round(tot/2+d/2),as=Math.round(tot/2-d/2);let ot=0;
  while(hs===as){ot++;hs+=6+Math.floor(r()*8);as+=6+Math.floor(r()*8)}
  hs=Math.max(hs,78);as=Math.max(as,78);if(hs===as)hs++;
  const w=[];for(let i=0;i<48+ot*5;i++)w.push(0.6+r());return{hs,as,ot,w}}
function regular(Y){if(cache['r'+Y])return cache['r'+Y];const R=ratings(Y),r=rng(h32('order',Y));
  const ids=T.map(t=>t.id).map(x=>[r(),x]).sort((a,b)=>a[0]-b[0]).map(x=>x[1]);const n=16,rounds=[];
  let arr=ids.slice();for(let k=0;k<15;k++){const m=[];for(let i=0;i<8;i++){let a=arr[i],b=arr[n-1-i];if((k+i)%2)[a,b]=[b,a];m.push([a,b])}rounds.push(m);arr=[arr[0],arr[n-1],...arr.slice(1,n-1)]}
  const games=[];for(let c=0;c<4;c++)for(let k=0;k<15;k++){const rd=c*15+k,day=regStart(Y)+rd*2;
    rounds[k].forEach(([a,b],i)=>{const [home,away]=c%2?[b,a]:[a,b];const g={id:Y+'-r'+rd+'-'+i,season:Y,type:'regular',round:rd+1,home,away,day,slot:i%2};g.t0=tipTime(day,home,g.slot);g.t1=g.t0+DUR*DAYMS;Object.assign(g,scoreGame(Y,g.id,home,away,R));games.push(g)})}
  games.sort((a,b)=>a.t0-b.t0);return cache['r'+Y]=games}
function table(games,upto){const S={};T.forEach(t=>S[t.id]={id:t.id,w:0,l:0,pf:0,pa:0,hw:0,hl:0,aw:0,al:0,last:[],cw:0,cl:0});
  games.forEach(g=>{if(g.t1>upto)return;const H=S[g.home],A=S[g.away],hw=g.hs>g.as;H.pf+=g.hs;H.pa+=g.as;A.pf+=g.as;A.pa+=g.hs;
    if(hw){H.w++;H.hw++;A.l++;A.al++}else{A.w++;A.aw++;H.l++;H.hl++}
    if(BY[g.home].conf===BY[g.away].conf){if(hw){H.cw++;A.cl++}else{A.cw++;H.cl++}}
    H.last.push(hw?'W':'L');A.last.push(hw?'L':'W')});
  Object.values(S).forEach(s=>{const gp=s.w+s.l;s.gp=gp;s.pct=gp?s.w/gp:0;s.diff=s.pf-s.pa;let k=0;const L=s.last;const z=L[L.length-1];for(let i=L.length-1;i>=0&&L[i]===z;i--)k++;s.streak=z?z+k:'';s.l10=L.slice(-10)});
  const conf=c=>Object.values(S).filter(s=>BY[s.id].conf===c).sort((a,b)=>b.pct-a.pct||b.diff-a.diff||BY[a.id].name.localeCompare(BY[b.id].name));
  const out={Brightport:conf('Brightport'),Occasue:conf('Occasue')};['Brightport','Occasue'].forEach(c=>{const top=out[c][0];out[c].forEach(s=>s.gb=((top.w-s.w)+(s.l-top.l))/2)});return out}
function series(Y,rd,hi,lo,start,R){const games=[];let wh=0,wl=0;const pat=[1,1,0,0,1,0,1];
  for(let g=0;g<7&&wh<4&&wl<4;g++){const home=pat[g]?hi:lo,away=pat[g]?lo:hi,day=start+g*2;const G={id:Y+'-p'+rd+'-'+hi+'-'+g,season:Y,type:'playoff',round:rd,game:g+1,home,away,day,slot:1};
    G.t0=tipTime(day,home,1);G.t1=G.t0+DUR*DAYMS;Object.assign(G,scoreGame(Y,G.id,home,away,R));(G.hs>G.as)===(home===hi)?wh++:wl++;G.sw=[wh,wl];games.push(G)}
  return{hi,lo,games,winner:wh===4?hi:lo,wh,wl}}
function playoffs(Y){if(cache['p'+Y])return cache['p'+Y];const R=ratings(Y);Object.keys(R).forEach(k=>R[k]+=0.15);
  const st=table(regular(Y),Infinity),P={seeds:{},rounds:[]};const S0=poStart(Y);
  ['Brightport','Occasue'].forEach(c=>P.seeds[c]=st[c].slice(0,4).map(s=>s.id));
  const sf=[];['Brightport','Occasue'].forEach(c=>{const s=P.seeds[c];sf.push(series(Y,1,s[0],s[3],S0,R),series(Y,1,s[1],s[2],S0,R))});
  const seed=id=>{for(const c in P.seeds){const i=P.seeds[c].indexOf(id);if(i>=0)return i}return 9};
  const cf=[series(Y,2,...[sf[0].winner,sf[1].winner].sort((a,b)=>seed(a)-seed(b)),S0+15,R),series(Y,2,...[sf[2].winner,sf[3].winner].sort((a,b)=>seed(a)-seed(b)),S0+15,R)];
  const sc=id=>st[BY[id].conf].find(s=>s.id===id);const fa=cf[0].winner,fb=cf[1].winner;const [hi,lo]=(sc(fa).pct>sc(fb).pct||(sc(fa).pct===sc(fb).pct&&sc(fa).diff>=sc(fb).diff))?[fa,fb]:[fb,fa];
  const fin=series(Y,3,hi,lo,S0+34,R);P.rounds=[sf,cf,[fin]];P.champion=fin.winner;P.runnerup=fin.winner===hi?lo:hi;
  P.games=[...sf,...cf,fin].flatMap(s=>s.games).sort((a,b)=>a.t0-b.t0);return cache['p'+Y]=P}
function allstar(Y){const day=regStart(Y)+72;const g={id:Y+'-allstar',season:Y,type:'allstar',home:'bp-all',away:'oc-all',day,slot:1,venue:'Trempas Ocean Arena'};g.t0=timeOf(day+(7.9-0)/10);g.t1=g.t0+DUR*1.1*DAYMS;
  const r=rng(h32('as',Y));g.hs=150+Math.floor(r()*30);g.as=150+Math.floor(r()*30);if(g.hs===g.as)g.hs+=2;g.ot=0;g.w=[];for(let i=0;i<48;i++)g.w.push(0.6+r());return g}
function seasonAt(t){const d=absDay(t);let Y=Math.floor((d-180)/300)+1;if(Y<FIRST)Y=FIRST;return Y}
function phase(t){const Y=seasonAt(t),d=absDay(t);if(d<regStart(Y))return{Y,ph:'pre'};if(d<poStart(Y))return{Y,ph:'regular'};
  const P=playoffs(Y);const end=P.rounds[2][0].games.slice(-1)[0].t1;if(t<end+DAYMS)return{Y,ph:'playoffs'};return{Y,ph:'off'}}
function live(g,t){if(t<g.t0)return{st:'pre',h:0,a:0};if(t>=g.t1)return{st:'final',h:g.hs,a:g.as,ot:g.ot};
  const p=(t-g.t0)/(g.t1-g.t0),n=g.w.length,k=Math.floor(p*n);let sw=0,tw=0;g.w.forEach((x,i)=>{tw+=x;if(i<k)sw+=x});const f=sw/tw;
  const regMins=48,min=p*n;let q,clock;if(min<regMins){q=Math.floor(min/12)+1;const left=12-(min%12);clock=Math.floor(left)+':'+String(Math.floor((left%1)*60)).padStart(2,'0')}else{q='OT'+(Math.floor((min-regMins)/5)+1);const left=5-((min-regMins)%5);clock=Math.floor(left)+':'+String(Math.floor((left%1)*60)).padStart(2,'0')}
  return{st:'live',h:Math.floor(g.hs*f),a:Math.floor(g.as*f),q,clock}}
function gamesOn(t,zone){const Y=seasonAt(t);const d=Math.floor(absDay(t)+(zone||0)/10);let gs=regular(Y).filter(g=>g.day===d);
  const P=playoffs(Y);gs=gs.concat(P.games.filter(g=>g.day===d));const A=allstar(Y);if(A.day===d)gs.push(A);return gs.sort((a,b)=>a.t0-b.t0)}
function allGames(Y){return regular(Y).concat(playoffs(Y).games,[allstar(Y)]).sort((a,b)=>a.t0-b.t0)}
function nextDay(t){const Y=seasonAt(t);const g=allGames(Y).find(g=>g.t0>t);return g?g.day:null}
function lastDay(t){const Y=seasonAt(t);const gs=allGames(Y).filter(g=>g.t1<=t);return gs.length?gs[gs.length-1].day:null}
const MN=['Primus','Secundus','Tertius','Quartus','Quintus','Sextus','Septimus','Octavus','Nonus','Decimus'],WD=['Caeloris','Nixis','Mercuris','Veneris','Martis','Aurethis','Silvaris','Jovis','Uranis','Neptunis'];
function dateOf(d){d=Math.floor(d);const Y=Math.floor(d/300)+1,doy=d-(Y-1)*300,M=Math.floor(doy/30),D=doy%30+1;return{Y,M,D,label:WD[(D-1)%10]+', '+MN[M]+' '+D+', '+Y+' NSC',short:MN[M]+' '+D}}
function clockOf(t,zone){const d=absDay(t)+(zone||0)/10,f=d-Math.floor(d),h=f*10,H=Math.floor(h),m=Math.floor((h-H)*100+1e-6);return H+'.'+String(m).padStart(2,'0')}
function tipLabel(g){const z=g.type==='allstar'?0:BY[g.home].zone;return clockOf(g.t0,z)+' '+ZN[z]}
function teamGames(Y,id){return allGames(Y).filter(g=>g.home===id||g.away===id)}
return{T,BY,ZN,absDay,timeOf,regular,playoffs,allstar,table,seasonAt,phase,live,gamesOn,allGames,nextDay,lastDay,dateOf,clockOf,tipLabel,teamGames,regStart,poStart,FIRST,
  label:Y=>'Season '+Y,last:{Y:118,champion:'lions',runnerup:'royals',sweep:'4–0'}};
})();

/* ---- URBO Sport channel (NyxVision channel 12, and the UBA Live page on the portal) ---- */
window.USPORT=function(A){
const U=window.UBA_SIM,X=window.URBO_SHARED;if(!U)return null;
const S=A.S,CH=A.CH,has=A.has,locked=A.locked,esc=A.esc,stage=A.stage,ui=A.ui;const CB=A.crestBase||'img/uba/crests/';
const crest=(id,cls)=>id==='bp-all'?'<i class="us-flag" style="background:#f4c21c">☀</i>':id==='oc-all'?'<i class="us-flag" style="background:#c8262c">☀</i>':'<img class="'+(cls||'us-cr')+'" src="'+CB+id+'.svg" alt="">';
const nm=id=>id==='bp-all'?'Brightport All-Stars':id==='oc-all'?'Occasue All-Stars':U.BY[id].name;
const ab=id=>id==='bp-all'?'BP':id==='oc-all'?'OC':U.BY[id].abbr;
(function(){if(document.getElementById('us-css'))return;const s=document.createElement('style');s.id='us-css';s.textContent=`
#usport{position:absolute;inset:0;overflow:hidden;color:#fff;font-family:"Fira Sans","Segoe UI",Tahoma,sans-serif;background:radial-gradient(ellipse at 30% 0%,#26324f,#0b1020 70%)}
#usport .us-top{position:absolute;left:0;right:0;top:0;height:54px;display:flex;align-items:center;gap:12px;padding:0 18px;background:linear-gradient(90deg,#c8262c 0 50%,#f4c21c 50%);box-shadow:0 2px 0 #ee6a1a}
#usport .us-logo{font:900 26px/1 "Fira Sans",sans-serif;letter-spacing:.04em;color:#fff;text-shadow:0 2px 0 rgba(0,0,0,.35)}#usport .us-logo span{color:#1b1b1b;text-shadow:none}
#usport .us-uba{height:40px}#usport .us-seg{margin-left:auto;font:700 15px sans-serif;color:#1b1b1b;text-transform:uppercase;letter-spacing:.08em}
#usport .us-clk{font:700 15px sans-serif;color:#1b1b1b;background:rgba(255,255,255,.65);padding:4px 10px;border-radius:6px}
#usport .us-main{position:absolute;left:18px;right:18px;top:68px;bottom:52px}
#usport h2{margin:0 0 8px;font:800 26px sans-serif;letter-spacing:.01em}#usport .us-sub{font:600 14px sans-serif;color:#b9c3dc;margin:-4px 0 10px}
#usport .us-games{display:grid;grid-template-columns:1fr 1fr;gap:8px}
#usport .us-g{display:grid;grid-template-columns:1fr auto;align-items:center;background:rgba(255,255,255,.08);border-radius:8px;padding:6px 10px;border-left:5px solid #ee6a1a}
#usport .us-g .r{display:flex;align-items:center;gap:8px;font:700 17px sans-serif;height:30px}#usport .us-g .r b{margin-left:auto;font:800 22px sans-serif;min-width:44px;text-align:right}
#usport .us-g .r.win{color:#fff}#usport .us-g .r.lose{color:#9aa6c2}#usport .us-st{grid-column:1/3;font:700 12px sans-serif;color:#f4c21c;text-transform:uppercase;letter-spacing:.06em}
#usport .us-st.live{color:#ff6b5b}#usport .us-cr{width:26px;height:26px;object-fit:contain}.us-flag{display:inline-grid;place-items:center;width:26px;height:26px;border-radius:5px;color:#ee6a1a;font-style:normal;font-weight:900}
#usport table{width:100%;border-collapse:collapse;font:600 15px sans-serif}#usport td,#usport th{padding:3px 6px;text-align:right}#usport th{font:700 12px sans-serif;color:#b9c3dc;text-transform:uppercase}#usport td:nth-child(2),#usport th:nth-child(2){text-align:left}
#usport tr.cut td{border-bottom:2px dashed #ee6a1a}#usport .us-two{display:grid;grid-template-columns:1fr 1fr;gap:18px}
#usport .us-spot{display:grid;grid-template-columns:200px 1fr;gap:20px;align-items:center}#usport .us-spot img{width:200px;height:200px;object-fit:contain}
#usport .us-spot h3{font:900 40px/1.05 sans-serif;margin:0}#usport .us-slogan{font:italic 700 22px sans-serif;margin:6px 0 10px}
#usport .us-pills{display:flex;gap:10px;flex-wrap:wrap}#usport .us-pills div{background:rgba(255,255,255,.1);border-radius:8px;padding:6px 12px;font:600 14px sans-serif}#usport .us-pills b{display:block;font:800 22px sans-serif}
#usport .us-l10{display:flex;gap:3px}#usport .us-l10 i{width:16px;height:16px;border-radius:3px;font:700 11px/16px sans-serif;text-align:center;font-style:normal}
#usport .us-bk{display:grid;grid-template-columns:1fr 1fr 1fr;gap:14px;align-items:center}#usport .us-ser{background:rgba(255,255,255,.08);border-radius:8px;padding:6px 10px;margin:6px 0}
#usport .us-ser div{display:flex;align-items:center;gap:6px;font:700 15px sans-serif}#usport .us-ser b{margin-left:auto}
#usport .us-cup{display:grid;grid-template-columns:220px 1fr;gap:24px;align-items:center}#usport .us-cup img{width:220px}
#usport .us-bot{position:absolute;left:0;right:0;bottom:0;height:40px;background:#0a0d18;border-top:3px solid #ee6a1a;overflow:hidden;white-space:nowrap}
#usport .us-bot span{display:inline-block;padding-left:100%;animation:uscr 70s linear infinite;font:700 17px/37px sans-serif}@keyframes uscr{to{transform:translateX(-100%)}}
#usport .us-in{animation:usin .5s ease}@keyframes usin{from{opacity:0;transform:translateY(8px)}}`;document.head.appendChild(s)})();
let el=null,raf=0,last=0,pk='',seg=-1;
function ensure(){if(el)return;el=document.createElement('div');el.id='usport';el.hidden=true;
  el.innerHTML='<div class="us-top"><img class="us-uba" src="'+CB.replace('crests/','league/uba-logo.png')+'" alt=""><div class="us-logo">URBO <span>SPORT</span></div><div class="us-seg"></div><div class="us-clk"></div></div><div class="us-main"></div><div class="us-bot"><span></span></div>';
  stage.insertBefore(el,ui)}
function gameCard(g,t){const L=U.live(g,t);const hw=L.st==='final'&&L.h>L.a,aw=L.st==='final'&&L.a>L.h;
  const st=L.st==='live'?'● Live · '+(typeof L.q==='number'?'Q'+L.q:L.q)+' '+L.clock:L.st==='final'?'Final'+(L.ot?' / '+(L.ot>1?L.ot:'')+'OT':''):U.tipLabel(g);
  const lab=g.type==='playoff'?' · '+(['','Conf. semifinal','Conf. final','UBA Finals'][g.round])+' G'+g.game:g.type==='allstar'?' · All-Star Game':'';
  return '<div class="us-g" style="border-left-color:'+(g.type==='allstar'?'#ee6a1a':U.BY[g.home].c1)+'"><div class="us-st '+(L.st==='live'?'live':'')+'">'+esc(st+lab)+'</div><div><div class="r '+(aw?'win':hw?'lose':'')+'">'+crest(g.away)+esc(nm(g.away))+'<b>'+(L.st==='pre'?'':L.a)+'</b></div><div class="r '+(hw?'win':aw?'lose':'')+'">'+crest(g.home)+esc(nm(g.home))+'<b>'+(L.st==='pre'?'':L.h)+'</b></div></div></div>'}
function standings(c,t){const Y=U.seasonAt(t),st=U.table(U.regular(Y),t)[c];return '<h2>'+c+' Conference</h2><div class="us-sub">'+U.label(Y)+' standings · top four reach the playoffs</div><table><tr><th>#</th><th>Team</th><th>W</th><th>L</th><th>Pct</th><th>GB</th><th>Strk</th></tr>'+st.map((s,i)=>'<tr'+(i===3?' class="cut"':'')+'><td>'+(i+1)+'</td><td>'+crest(s.id)+' '+esc(U.BY[s.id].name)+'</td><td>'+s.w+'</td><td>'+s.l+'</td><td>'+(s.gp?s.pct.toFixed(3).replace(/^0/,''):'.000')+'</td><td>'+(s.gb?s.gb:'–')+'</td><td>'+(s.streak||'–')+'</td></tr>').join('')+'</table>'}
function slate(t){let gs=U.gamesOn(t,0);let title="Today's games";if(!gs.length||gs.every(g=>t>g.t1+0.4*86400000)){const nd=U.nextDay(t);const ld=U.lastDay(t);
    if(gs.length===0&&ld!=null&&(nd==null||U.absDay(t)-ld<1.2)){gs=U.allGames(U.seasonAt(t)).filter(g=>g.day===ld);title='Latest results'}else if(nd!=null){gs=U.allGames(U.seasonAt(t)).filter(g=>g.day===nd);title='Next game day'}}
  if(!gs.length)return null;const d=U.dateOf(gs[0].day);return '<h2>'+title+'</h2><div class="us-sub">'+d.label+'</div><div class="us-games">'+gs.slice(0,8).map(g=>gameCard(g,t)).join('')+'</div>'}
function spot(t,i){const Y=U.seasonAt(t),tm=U.T[i%16],st=U.table(U.regular(Y),t),s=st[tm.conf].find(x=>x.id===tm.id),rk=st[tm.conf].indexOf(s)+1;
  const nx=U.teamGames(Y,tm.id).find(g=>g.t1>t);return '<div class="us-spot" style="--c:'+tm.c1+'"><img src="'+CB+tm.id+'.svg" alt=""><div><div class="us-sub">Team spotlight · '+tm.conf+' Conference</div><h3>'+esc(tm.name)+'</h3><div class="us-slogan" style="color:'+(lum(tm.c2)>0.25?tm.c2:lum(tm.c1)>0.25?tm.c1:'#fff')+'">'+esc(SLOG[tm.id]||'')+'</div>'+
   '<div class="us-pills"><div><b>'+s.w+'–'+s.l+'</b>Record</div><div><b>'+rk+(['st','nd','rd'][rk-1]||'th')+'</b>In conference</div><div><b>'+tm.titles+'</b>Titles</div><div><b>'+tm.cap.toLocaleString('en-US')+'</b>'+esc(tm.arena)+'</div></div>'+
   '<p style="font:600 15px sans-serif;margin:10px 0 4px">Last ten: </p><div class="us-l10">'+s.l10.map(x=>'<i style="background:'+(x==='W'?'#2e9d55':'#a23b3b')+'">'+x+'</i>').join('')+'</div>'+(nx?'<p style="font:600 15px sans-serif">Next: '+(nx.home===tm.id?'vs ':'at ')+esc(nm(nx.home===tm.id?nx.away:nx.home))+' · '+U.dateOf(nx.day).short+', '+U.tipLabel(nx)+'</p>':'')+'</div></div>'}
function bracket(t){const Y=U.seasonAt(t),P=U.playoffs(Y);const ser=s=>{const vis=s.games.filter(g=>g.t1<=t);let a=0,b=0;vis.forEach(g=>{(g.hs>g.as)===(g.home===s.hi)?a++:b++});const done=a===4||b===4;
   return '<div class="us-ser"><div>'+crest(s.hi)+esc(U.BY[s.hi].nick)+'<b>'+a+'</b></div><div>'+crest(s.lo)+esc(U.BY[s.lo].nick)+'<b>'+b+'</b></div></div>'};
  const r=P.rounds;return '<h2>UBA Playoffs</h2><div class="us-sub">'+U.label(Y)+' · every series best of seven</div><div class="us-bk"><div><div class="us-sub">Brightport</div>'+ser(r[0][0])+ser(r[0][1])+(t>r[1][0].games[0].t0-DAYW?ser(r[1][0]):'')+'</div><div style="text-align:center">'+(t>r[2][0].games[0].t0-DAYW?'<div class="us-sub">UBA Finals</div>'+ser(r[2][0]):'<img src="'+CB.replace('crests/','league/nyreth-cup.svg')+'" style="width:150px" alt="">')+'</div><div><div class="us-sub">Occasue</div>'+ser(r[0][2])+ser(r[0][3])+(t>r[1][1].games[0].t0-DAYW?ser(r[1][1]):'')+'</div></div>'}
const DAYW=93600000*1.5;
function cup(t){const ph=U.phase(t);let line;if(ph.ph==='off'){const P=U.playoffs(ph.Y);line='<h3 style="font:900 34px sans-serif;margin:0">'+esc(U.BY[P.champion].name)+'</h3><p style="font:600 18px sans-serif">'+U.label(ph.Y)+' champions, beating the '+esc(U.BY[P.runnerup].name)+' in the UBA Finals. The next season tips off on Septimus 1.</p>'}
  else line='<h3 style="font:900 34px sans-serif;margin:0">Berevo Lions</h3><p style="font:600 18px sans-serif">Defending champions. They swept the Brightport City Royals 4–0 in last season’s UBA Finals for their 11th title.</p>';
  return '<div class="us-cup"><img src="'+CB.replace('crests/','league/nyreth-cup.svg')+'" alt=""><div><div class="us-sub">The Nyreth Cup</div>'+line+'<p style="font:600 15px sans-serif;color:#b9c3dc">Presented jointly by the Souvren of Brightport and the President of Occasue.</p></div></div>'}
const SLOG={titans:'Rise, Titans, Rise!',tides:'Ride the Tide!',crystals:'Unbreakable!',vikings:'Fear the North!',cobras:'¡Vamos, Cobras!',admirals:'Rule the Waves!',guardians:'Defend the Fortress!',lions:'Wij zijn de Leeuwen!',breakers:'Unleash the Wave!',eclairs:'Allez, les Éclairs !',highlanders:'Highland Pride!',mustangs:'Run Wild!',summits:'Rise Above!',lynx:'Fierce and Free!',wolves:'Strength in the Pack!'};
const SEG=11000;const lum=h=>{const n=parseInt(h.slice(1),16);return((n>>16)*0.299+((n>>8)&255)*0.587+(n&255)*0.114)/255};
function segs(t){const ph=U.phase(t).ph,k=Math.floor(t/SEG);const list=[];const sl=slate(t);if(sl)list.push(['Scores',sl]);
  if(ph==='playoffs')list.push(['Playoffs',bracket(t)]);
  if(ph==='regular'||ph==='playoffs'){list.push(['Standings',standings('Brightport',t)]);if(sl)list.push(['Scores',sl]);list.push(['Standings',standings('Occasue',t)])}
  list.push(['Spotlight',spot(t,Math.floor(t/(SEG*7))%16)]);list.push(['Nyreth Cup',cup(t)]);return list}
function ticker(t){const Y=U.seasonAt(t);const done=U.allGames(Y).filter(g=>g.t1<=t).slice(-12);const lv=U.gamesOn(t,0).filter(g=>t>=g.t0&&t<g.t1);
  const f=g=>{const L=U.live(g,t);return ab(g.away)+' '+L.a+' – '+L.h+' '+ab(g.home)+(L.st==='live'?' (live)':'')};return [...lv.map(f),...done.reverse().map(f)].join('   •   ')||'URBO Sport · the home of the UBA'}
function draw(t){const k=Math.floor(t/SEG),L=segs(t),i=k%L.length,[name,html]=L[i];const ck=el.querySelector('.us-clk'),cs=U.clockOf(t,0)+' BT · '+U.dateOf(U.absDay(t)).short;if(ck.textContent!==cs)ck.textContent=cs;
  const m=el.querySelector('.us-main');const key=k+'|'+html.length;if(key!==pk){const fresh=seg!==k;seg=k;pk=key;m.innerHTML=html;el.querySelector('.us-seg').textContent=name;if(fresh){m.classList.remove('us-in');void m.offsetWidth;m.classList.add('us-in')}
    const tk=el.querySelector('.us-bot span'),tt=ticker(t);if(tk.textContent!==tt)tk.textContent=tt}}
function loop(ts){raf=requestAnimationFrame(loop);if(ts-last<500)return;last=ts;try{draw(Date.now())}catch(e){console.error(e)}}
function sync(){const on=S.screen==='watch'&&has(S.ch)&&!!CH[S.ch].usport&&!locked(S.ch)&&!A.paused();
  if(on){ensure();if(el.hidden){el.hidden=false;pk='';cancelAnimationFrame(raf);raf=requestAnimationFrame(loop)}}
  else if(el&&!el.hidden){el.hidden=true;cancelAnimationFrame(raf);raf=0}}
document.addEventListener('visibilitychange',()=>{try{sync()}catch(e){}});
return{sync};
};
