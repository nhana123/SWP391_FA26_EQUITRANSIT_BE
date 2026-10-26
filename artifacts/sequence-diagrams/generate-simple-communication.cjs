const path=require('path');
const sharp=require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const out=path.join(__dirname,'..','communication-diagrams','EquiTransit-Communication-Simple.png');
const E=s=>s.replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
const t=(x,y,s,size=23,anchor='middle',extra='')=>`<text x="${x}" y="${y}" font-size="${size}" text-anchor="${anchor}" ${extra}>${E(s)}</text>`;
const line=(x,y,a,b,extra='')=>`<line x1="${x}" y1="${y}" x2="${a}" y2="${b}" ${extra}/>`;
function actor(x,y,name){return `<circle cx="${x}" cy="${y-43}" r="12" fill="#85d1f1" stroke="#222" stroke-width="2"/>`+line(x,y-31,x,y+13,'stroke="#222" stroke-width="2"')+line(x-25,y-12,x+25,y-12,'stroke="#222" stroke-width="2"')+line(x,y+13,x-24,y+46,'stroke="#222" stroke-width="2"')+line(x,y+13,x+24,y+46,'stroke="#222" stroke-width="2"')+t(x,y+79,name,27,'middle','font-weight="bold"');}
function box(x,y,stereo,name,width=500){return `<rect x="${x-width/2}" y="${y-50}" width="${width}" height="100" fill="#85d1f1" stroke="#222" stroke-width="2"/>`+t(x,y-13,'«'+stereo+'»',20)+t(x,y+23,name,25,'middle','font-weight="bold" text-decoration="underline"');}
function arrow(x,y,dx,dy){const n=Math.hypot(dx,dy);dx/=n;dy/=n;return line(x-dx*22,y-dy*22,x+dx*22,y+dy*22,'stroke="#111" stroke-width="2" marker-end="url(#arrow)"');}
const W=2600,H=2100;
let s=`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs><marker id="arrow" markerWidth="9" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8Z" fill="#111"/></marker></defs><rect width="100%" height="100%" fill="white"/><g font-family="Arial, sans-serif" fill="#111">`;
s+=t(W/2,65,'EquiTransit — Integrated Communication Diagram',39,'middle','font-weight="bold"')+t(W/2,108,'Simplified COMET analysis view · 5 actors · Aggregated messages',23);
// Five short actor links, then a simple tree of collaboration links.
s+=line(210,350,440,350,'stroke="#333" stroke-width="2"')+line(2390,350,2160,350,'stroke="#333" stroke-width="2"')+line(210,1110,440,1110,'stroke="#333" stroke-width="2"')+line(2390,1110,2160,1110,'stroke="#333" stroke-width="2"')+line(1300,1770,1300,1610,'stroke="#333" stroke-width="2"');
s+=`<path d="M940 350H1050V750H1150V785M1660 350H1550V750H1450V785M940 1110H1050V940H1150V885M1660 1110H1550V940H1450V885M1300 1510V885" fill="none" stroke="#333" stroke-width="2"/>`;
// Junction dots explicitly indicate shared structural links.

s+=actor(155,350,'Customer')+actor(2445,350,'Manager')+actor(155,1110,'Coordinator')+actor(2445,1110,'Specialist')+actor(1300,1825,'Driver');
s+=box(690,350,'coordinator','customer : CustomerControl')+box(1910,350,'coordinator','manager : ManagerControl')+box(690,1110,'coordinator','planning : PlanningControl')+box(1910,1110,'coordinator','compliance : ComplianceControl')+box(1300,1560,'coordinator','execution : TripControl')+box(1300,835,'entity','shipment : ShipmentCase',570);
// Each family is a separate use-case interaction; numbers are nested calls.
s+=t(690,193,'R1: Submit request · Q3: Accept quote',25)+t(690,235,'P1: Report transfer · L1: Submit files',25)+t(690,277,'H3: Confirm / dispute delivery',25)+arrow(305,335,1,0);
s+=t(1910,193,'A1: Assess and assign · Q2: Issue quote',25)+t(1910,235,'P2: Reconcile / allocate · D1: Authorize',25)+t(1910,277,'C1[DELIVERED]: Close order',25)+arrow(2290,335,-1,0);
s+=t(690,1210,'O1: Draft plan and assign resources',25)+t(690,1252,'Q1: Prepare quote · O2[ready]: Confirm plan',25)+arrow(305,1095,1,0);
s+=t(1910,1210,'L2*: Review documents and give feedback',25)+t(1910,1252,'L3[approved, eligible]: Clear phase',25)+arrow(2290,1095,-1,0);
s+=t(1600,1740,'D2[authorized, fit]: Receive / depart',25,'start')+t(1600,1782,'E1*: Record progress / intermediate handover',25,'start')+t(1600,1824,'H2[release cleared]: Final delivery',25,'start')+arrow(1320,1730,0,-1);
// Aggregated internal messages sit beside their individual link segments.
s+=arrow(1070,525,0,1)+t(1018,484,'R1.1 / Q3.1 / P1.1 / L1.1 / H3.1',22,'end')+t(1018,521,'Update request, choices and evidence',22,'end');
s+=arrow(1530,525,0,1)+t(1582,484,'A1.1 / Q2.1 / P2.1 / D1.1 / C1.1',22,'start')+t(1582,521,'Record decisions, payment and status',22,'start');
s+=arrow(1070,1000,0,-1)+t(1018,958,'O1.1 / Q1.1 / O2.1',22,'end')+t(1018,995,'Save route, price and commitments',22,'end');
s+=arrow(1530,1000,0,-1)+t(1582,958,'L2.1* / L3.1[all checks pass]',22,'start')+t(1582,995,'Record review and phase clearance',22,'start');
s+=arrow(1320,1370,0,-1)+t(1370,1350,'D2.1 / E1.1* / H2.1',22,'start')+t(1370,1387,'Update leg, handover and delivery',22,'start');
s+=`<rect x="440" y="1935" width="1720" height="125" rx="8" fill="#f6f9fb" stroke="#cbd8df"/>`+t(1300,1972,'ShipmentCase aggregates Order, Revision, Quote, Payment, Plan, Compliance and Handover entities.',21)+t(1300,2007,'Prefixes identify use-case families, not one global timeline. [guard] = condition; * = iteration.',21)+t(1300,2042,'Conceptual analysis objects; UI, repositories and detailed sub-interactions are collapsed for readability.',21);
s+='</g></svg>';
sharp(Buffer.from(s)).png().toFile(out).then(()=>console.log(out));


