const fs=require('fs');const path=require('path');
const sharp=require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const root=path.resolve(__dirname,'..','..');const out=path.join(root,'artifacts','class-diagrams');fs.mkdirSync(out,{recursive:true});
const java=path.join(root,'src/main/java/com/equitransit/backend');
function stripAnnotations(s){let o='';for(let i=0;i<s.length;){if(s[i]!=='@'){o+=s[i++];continue;}i++;while(/[\w.]/.test(s[i]||''))i++;while(/\s/.test(s[i]||''))i++;if(s[i]==='('){let d=1,quote=null;i++;while(i<s.length&&d){let c=s[i++];if(quote){if(c==='\\')i++;else if(c===quote)quote=null;}else if(c==='"'||c==="'")quote=c;else if(c==='(')d++;else if(c===')')d--;}}}return o;}
function splitParams(s){let items=[],start=0,depth=0;for(let i=0;i<s.length;i++){if('<[('.includes(s[i]))depth++;if('>])'.includes(s[i]))depth--;if(s[i]===','&&depth===0){items.push(s.slice(start,i).trim());start=i+1;}}if(s.slice(start).trim())items.push(s.slice(start).trim());return items;}
function read(cls,folder,stereo){const source=fs.readFileSync(path.join(java,folder,cls+'.java'),'utf8');const clean=stripAnnotations(source.replace(/\/\*[\s\S]*?\*\//g,''));let attrs=[],methods=[];
 for(const m of clean.matchAll(/^    private (static )?(?:final )?([\w<>?,. ]+)\s+(\w+)\s*(?:=|;)/gm)){if(m[2].includes('return'))continue;attrs.push({text:'− '+m[3]+' : '+m[2].trim()+' {readOnly}',static:!!m[1]});}
 const re=/^    public\s+(static\s+)?([\w<>?,. \[\]]+)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws [^{]+)?\{/gm;
 for(const m of clean.matchAll(re)){if(['record','class','enum'].includes(m[2].trim()))continue;const ps=splitParams(m[4].replace(/\s+/g,' ')).map(p=>{const a=p.match(/^(.*\S)\s+(\w+)$/);return a?a[2]+': '+a[1]:p;});methods.push({text:'+ '+m[3]+'('+ps.join(', ')+') : '+m[2].trim(),static:!!m[1],name:m[3]});}
 return {cls,stereo,attrs,methods,source:path.join(folder,cls+'.java')};}
const specs=[['OperationalPlanController','controller','controller'],['DepartureGateController','controller','controller'],['OperationalPlanService','service','service'],['DepartureGateService','service','service'],['OperationalPlanPolicy','service','utility'],['DepartureGatePolicy','service','utility'],['PlanRepository','repository','repository'],['DepartureRepository','repository','repository'],['JourneyGraphService','service','service']];
const models=Object.fromEntries(specs.map(s=>{const o=read(...s);return [o.cls,o];}));
const E=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
const font=22,lineH=34,width=1750;
function wrap(s,max=122){const a=[];while(s.length>max){let at=s.lastIndexOf(', ',max);if(at<max*.5)at=s.lastIndexOf(' ',max);if(at<max*.4)at=max;else at++;a.push(s.slice(0,at));s='    '+s.slice(at).trimStart();}a.push(s);return a;}
function prepare(m,w=width){const max=Math.floor((w-48)/(font*.61));const attrs=m.attrs.flatMap(a=>wrap(a.text,max).map(text=>({...a,text})));const methods=m.methods.flatMap(a=>wrap(a.text,max).map(text=>({...a,text})));return {...m,w,al:attrs,ml:methods,ah:Math.max(1,attrs.length)*lineH+30,mh:Math.max(1,methods.length)*lineH+32,h:100+Math.max(1,attrs.length)*lineH+30+Math.max(1,methods.length)*lineH+32};}
const boxes={};const parts=[];
function txt(x,y,t,size=font,extra=''){return `<text x="${x}" y="${y}" font-size="${size}" ${extra}>${E(t)}</text>`;}
function draw(m,x,y,w=width){m=prepare(m,w);boxes[m.cls]={...m,x,y};let s=`<rect x="${x}" y="${y}" width="${m.w}" height="${m.h}" fill="white" stroke="#202020" stroke-width="2"/><rect x="${x}" y="${y}" width="${m.w}" height="100" fill="#e9f5fc" stroke="#202020" stroke-width="2"/>`+txt(x+m.w/2,y+35,'«'+m.stereo+'»',23,'text-anchor="middle"')+txt(x+m.w/2,y+76,m.cls,30,'text-anchor="middle" font-weight="bold"');
 s+=`<path d="M${x} ${y+100+m.ah}H${x+m.w}" stroke="#202020" stroke-width="1.7"/>`;
 const lines=(a,sy,empty)=>{if(!a.length)return txt(x+23,sy,empty,20,'font-style="italic" fill="#68727b"');return a.map((a,i)=>txt(x+23,sy+i*lineH,a.text,font,a.static?'text-decoration="underline"':'')).join('');};
 s+=`<g font-family="Consolas, monospace">`+lines(m.al,y+135,'No instance attributes')+lines(m.ml,y+135+m.ah,'No public operations')+'</g>';parts.push(s);return m.h;}
function pathSvg(points,kind='association'){return `<path d="${points}" fill="none" stroke="#222" stroke-width="2" ${kind==='dependency'?'stroke-dasharray="10 7"':''} marker-end="url(#open)"/>`;}
const X1=110,X2=2180,W=4040;let y=200;
for(const pair of [['OperationalPlanController','DepartureGateController'],['OperationalPlanService','DepartureGateService'],['OperationalPlanPolicy','DepartureGatePolicy'],['PlanRepository','DepartureRepository']]){const hs=pair.map((n,i)=>draw(models[n],i?X2:X1,y));y+=Math.max(...hs)+220;}
const H=y+220;
const b=n=>boxes[n];let links='';
for(const [a,c] of [['OperationalPlanController','OperationalPlanService'],['DepartureGateController','DepartureGateService']]){const A=b(a),B=b(c);links+=pathSvg(`M${A.x+A.w/2} ${A.y+A.h}V${B.y}`)+txt(A.x+A.w/2+20,(A.y+A.h+B.y)/2,'service  [1]',20);}
for(const [a,c] of [['OperationalPlanService','OperationalPlanPolicy'],['DepartureGateService','DepartureGatePolicy']]){const A=b(a),B=b(c);links+=pathSvg(`M${A.x+A.w/2} ${A.y+A.h}V${B.y}`,'dependency')+txt(A.x+A.w/2+20,(A.y+A.h+B.y)/2,'«use» static policy',20);}
const plan=b('PlanRepository'),dep=b('DepartureRepository'),ps=b('OperationalPlanService'),ds=b('DepartureGateService');
links+=pathSvg(`M${ps.x} ${ps.y+170}H55V${plan.y+65}H${plan.x}`)+txt(65,plan.y-35,'plans  [1]',20);
links+=pathSvg(`M${ds.x+ds.w} ${ds.y+170}H3985V${dep.y+65}H${dep.x+dep.w}`)+txt(dep.x+dep.w-170,dep.y-35,'departures  [1]',20);
links+=pathSvg(`M${ds.x} ${ds.y+190}H2040V${plan.y+130}H${plan.x+plan.w}`)+txt(1875,plan.y+114,'plans [1]',20);
const footer=[
 'Scope: F10 Operational Planning + F11 Departure Gates. All public methods and injected fields of the eight core classes are shown.',
 '+ public; − private; underlined member = static; {readOnly} = final field reference. Ordinary class names are not underlined.',
 'Solid navigable association = stored collaborator; [1] = one required injected collaborator. Dashed dependency = static policy use.',
 'No inheritance or composition is asserted. DI constructors, private helpers, nested repository row records and other dependency boxes are omitted.',
 'JourneyGraphService is referenced by OperationalPlanController.graphs; its full class is supplied on the companion PNG.',
 'Other collaborator types remain explicit in the service attribute compartments; no fictitious entity fields or operations have been added.'
];
const header=(w,h,title,sub)=>`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><defs><marker id="open" markerWidth="13" markerHeight="12" refX="12" refY="6" orient="auto"><path d="M0 0L12 6L0 12" fill="none" stroke="#222" stroke-width="1.7"/></marker></defs><rect width="100%" height="100%" fill="white"/><g font-family="Arial" fill="#111">${txt(w/2,65,title,43,'text-anchor="middle" font-weight="bold"')}${txt(w/2,112,sub,24,'text-anchor="middle"')}`;
(async()=>{
 let svg=header(W,H,'EquiTransit — Design Class Diagram','Core planning and departure classes · Fields, typed operations and code-backed relationships');svg+=links+parts.join('')+footer.map((t,i)=>txt(110,H-195+i*33,t,21)).join('')+'</g></svg>';
 await sharp(Buffer.from(svg)).png().toFile(path.join(out,'EquiTransit-Design-Class-Planning-Departure.png'));
 // A focused companion prevents the graph dependency from being silently omitted.
 parts.length=0;const gm=prepare(models.JourneyGraphService,1800);draw(models.JourneyGraphService,130,190,1800);const GH=gm.h+410;
 let g=header(2060,GH,'EquiTransit — Journey Graph Service','Referenced by OperationalPlanController.graphs; delegates compiled plans to OperationalPlanService');g+=parts.join('')+txt(130,GH-135,'+ public; − private; {readOnly} = final reference. Constructor and private helpers omitted.',20)+txt(130,GH-90,'Stored collaborators include planning: OperationalPlanService and plans: PlanRepository.',20)+txt(130,GH-45,'Names, field types and signatures extracted from the current Backend Java source.',20)+'</g></svg>';
 await sharp(Buffer.from(g)).png().toFile(path.join(out,'EquiTransit-Design-Class-JourneyGraphService.png'));
 fs.writeFileSync(path.join(out,'class-source-inventory.json'),JSON.stringify(models,null,2));
 console.log(JSON.stringify(Object.values(models).map(m=>({class:m.cls,fields:m.attrs.length,methods:m.methods.length})),null,2));
})();
