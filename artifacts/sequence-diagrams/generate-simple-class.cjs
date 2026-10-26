const fs=require('fs');const path=require('path');
const sharp=require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const dir=path.join(__dirname,'..','class-diagrams');
const inventory=JSON.parse(fs.readFileSync(path.join(dir,'class-source-inventory.json'),'utf8'));
const E=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
const picks=[
 ['OperationalPlanController',['service'],['createDraft','confirm']],
 ['DepartureGateController',['service'],['getReadiness','depart']],
 ['OperationalPlanService',['plans','quotes'],['createDraft','confirm']],
 ['DepartureGateService',['departures','plans'],['authorize','depart']],
 ['OperationalPlanPolicy',[],['editable','validateLegs']],
 ['DepartureGatePolicy',[],['receiptStatus','gateView']],
 ['PlanRepository',['jdbc'],['findPlan','legs']],
 ['DepartureRepository',['jdbc'],['firstLeg','departFirstLeg']]
];
const W=2300,H=2240,xs=[100,1250],bw=950,ys=[180,670,1160,1650],boxes={};let parts=[];
const text=(x,y,s,size=24,extra='')=>`<text x="${x}" y="${y}" font-size="${size}" ${extra}>${E(s)}</text>`;
function wrap(s,max=62){const result=[];while(s.length>max){let i=s.lastIndexOf(', ',max);if(i<30)i=s.lastIndexOf(' ',max);if(i<20)i=max;else i++;result.push(s.slice(0,i));s='    '+s.slice(i).trimStart();}result.push(s);return result;}
for(let n=0;n<picks.length;n++){
 const [name,attrs,methods]=picks[n],m=inventory[name],x=xs[n%2],y=ys[Math.floor(n/2)];
 const aa=m.attrs.filter(a=>attrs.some(k=>a.text.startsWith('− '+k+' :'))).map(a=>({...a,text:a.text.replace(' {readOnly}','')}));
 const mm=methods.map(k=>m.methods.find(a=>a.name===k));
 if(mm.some(a=>!a))throw new Error('Missing source operation in '+name);
 const rows=mm.flatMap(a=>wrap(a.text).map(t=>({...a,text:t})));
 const ah=aa.length?aa.length*38+28:57,mh=rows.length*34+30,h=80+ah+mh;
 boxes[name]={x,y,w:bw,h};
 parts.push(`<rect x="${x}" y="${y}" width="${bw}" height="${h}" rx="7" fill="#f6d2df" stroke="#111" stroke-width="5"/>`+text(x+bw/2,y+53,name,32,'text-anchor="middle" font-weight="bold"')+`<path d="M${x} ${y+80}H${x+bw}M${x} ${y+80+ah}H${x+bw}" stroke="#111" stroke-width="4"/>`+
  (aa.length?aa.map((a,i)=>text(x+23,y+119+i*38,a.text)).join(''):text(x+23,y+116,'No instance attributes',22,'font-style="italic"'))+
  rows.map((a,i)=>text(x+23,y+80+ah+36+i*34,a.text,23,a.static?'text-decoration="underline"':'')).join(''));
}
const b=n=>boxes[n];let links='';const edge=(d,dashed=false)=>`<path d="${d}" fill="none" stroke="#111" stroke-width="3" ${dashed?'stroke-dasharray="10 7"':''} marker-end="url(#open)"/>`;
for(const [a,c] of [['OperationalPlanController','OperationalPlanService'],['DepartureGateController','DepartureGateService'],['OperationalPlanService','OperationalPlanPolicy'],['DepartureGateService','DepartureGatePolicy']]){const A=b(a),B=b(c),cx=A.x+A.w/2;links+=edge(`M${cx} ${A.y+A.h}V${B.y}`,c.endsWith('Policy'));}
const ps=b('OperationalPlanService'),ds=b('DepartureGateService'),pr=b('PlanRepository'),dr=b('DepartureRepository');
links+=edge(`M${ps.x} ${ps.y+130}H45V${pr.y+45}H${pr.x}`);
links+=edge(`M${ds.x+ds.w} ${ds.y+130}H2255V${dr.y+45}H${dr.x+dr.w}`);
links+=edge(`M${ds.x} ${ds.y+140}H1150V${pr.y+125}H${pr.x+pr.w}`);
const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs><marker id="open" markerWidth="12" markerHeight="11" refX="11" refY="5.5" orient="auto"><path d="M0 0L11 5.5L0 11" fill="none" stroke="#111" stroke-width="1.7"/></marker></defs><rect width="100%" height="100%" fill="white"/><g font-family="Arial, sans-serif" fill="#111">${text(W/2,65,'EquiTransit — Class Diagram',44,'text-anchor="middle" font-weight="bold"')}${text(W/2,110,'Planning & Departure · Simplified view',25,'text-anchor="middle"')}${links}${parts.join('')}${text(100,2100,'+ public    − private    Underlined operation = static',23)}${text(100,2142,'Solid arrow: stored reference · Dashed arrow: uses policy',23)}${text(100,2184,'Selected fields and operations from Backend code; supporting classes and helper methods omitted.',22)}</g></svg>`;
sharp(Buffer.from(svg)).png().toFile(path.join(dir,'EquiTransit-Class-Diagram-Simple.png')).then(()=>console.log('Generated simplified PNG.'));
