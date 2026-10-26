const fs=require('fs');
const path=require('path');
const sharp=require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const out=path.join(__dirname,'..','communication-diagrams');
const E=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
const text=(x,y,s,size=24,anchor='start',extra='')=>`<text x="${x}" y="${y}" text-anchor="${anchor}" font-size="${size}" ${extra}>${E(s)}</text>`;
const line=(x,y,a,b,extra='')=>`<line x1="${x}" y1="${y}" x2="${a}" y2="${b}" ${extra}/>`;
function actor(x,y,name){return `<circle cx="${x}" cy="${y-60}" r="17" fill="#86d3f5" stroke="#365469" stroke-width="2.5"/>`+line(x,y-43,x,y+17,'stroke="#365469" stroke-width="3"')+line(x-34,y-17,x+34,y-17,'stroke="#365469" stroke-width="3"')+line(x,y+17,x-31,y+62,'stroke="#365469" stroke-width="3"')+line(x,y+17,x+31,y+62,'stroke="#365469" stroke-width="3"')+text(x,y+110,name,30,'middle','font-weight="bold"');}
function messages(x,y,title,items,dir='right'){
 let s=text(x,y,title,27,'start','fill="#245d7b" font-weight="bold"');
 items.forEach((t,i)=>{const yy=y+54+i*48;s+=text(x+66,yy,t,23);s+=dir==='up'?line(x+20,yy+8,x+20,yy-23,'stroke="#263f51" stroke-width="2" marker-end="url(#arrow)"'):line(dir==='right'?x:x+42,yy-7,dir==='right'?x+42:x,yy-7,'stroke="#263f51" stroke-width="2" marker-end="url(#arrow)"');});return s;
}
let s=`<svg xmlns="http://www.w3.org/2000/svg" width="2800" height="2400" viewBox="0 0 2800 2400"><defs><pattern id="grid" width="28" height="28" patternUnits="userSpaceOnUse"><path d="M28 0H0V28" fill="none" stroke="#e9f0f5" stroke-width=".8"/></pattern><marker id="arrow" markerWidth="10" markerHeight="9" refX="9" refY="4.5" orient="auto"><path d="M0 0L9 4.5L0 9Z" fill="#263f51"/></marker></defs><rect width="100%" height="100%" fill="white"/><rect x="35" y="180" width="2730" height="2070" fill="url(#grid)"/><g font-family="Arial, sans-serif" fill="#183647">`;
s+=text(75,65,'EQUITRANSIT SYSTEM',26,'start','fill="#245d7b" font-weight="bold"')+text(75,120,'System-wide Communication Diagram — 5 Actors',43,'start','font-weight="bold"')+text(75,160,'Main transport lifecycle · Numbered messages · System-level view',23);
const paths=['M225 650H800V920H1100','M2575 650H2000V920H1700','M225 1410H800V1040H1100','M2575 1410H2000V1040H1700','M1400 2100V1130'];
paths.forEach(p=>s+=`<path d="${p}" fill="none" stroke="#698494" stroke-width="2.6"/>`);
s+=actor(180,650,'Customer')+actor(2620,650,'Coordinator')+actor(180,1410,'Manager')+actor(2620,1410,'Specialist')+actor(1400,2190,'Driver');
s+=`<rect x="1100" y="850" width="600" height="280" fill="#86d3f5" stroke="#365469" stroke-width="2.6"/>`+text(1400,915,': EquiTransitSystem',34,'middle','font-weight="bold" text-decoration="underline"')+text(1400,968,'Requests · Assessment · Quotation',23,'middle')+text(1400,1010,'Payments · Planning · Compliance',23,'middle')+text(1400,1052,'Departure · Execution · Handover',23,'middle')+text(1400,1094,'Files · Audit · Order closure',23,'middle');
s+=messages(315,260,'Customer → System',[
 '1: Submit transport request',
 '6: Accept issued quotation',
 '7: Report bank transfer and upload proof',
 '10 / 18: Upload and submit phase documents',
 '21: Confirm or dispute delivery record'
]);
s+=messages(1750,310,'Coordinator → System',[
 '3: Save route / plan draft and airfare estimates',
 '4: Prepare quotation for current revision',
 '13: [resources + accepted quote] Confirm plan'
],'left');
s+=messages(315,1525,'Manager → System',[
 '2: Approve request and assign case team',
 '5: Approve / issue quotation',
 '8: Reconcile bank receipt',
 '9: Allocate deposit → IN_PREPARATION',
 '14: [all departure gates pass] Authorize',
 '22: [DELIVERED] Close order → CLOSED'
]);
s+=messages(1750,1525,'Specialist → System',[
 '11: Review PRE_EXPORT submissions',
 '12: [approved evidence] Clear PRE_EXPORT',
 '19: [eligible] Review / clear ARRIVAL and release'
],'left');
s+=messages(1490,1860,'Driver → System',[
 '15: Record horse receipt and fitness',
 '16: [authorized + fit] Record departure',
 '17: * Record leg progress / intermediate handover',
 '20: [release cleared] Record final delivery'
],'up');
s+=`<rect x="925" y="1190" width="950" height="160" rx="10" fill="white" stroke="#c8d8e3" stroke-width="1.5"/>`+text(1400,1230,'Read message numbers globally: 1 → 2 → ... → 22.',23,'middle')+text(1400,1271,'Each arrow is a message along its actor–system link.',21,'middle')+text(1400,1310,'[guard] = condition · * = iteration · replies omitted',21,'middle');
s+=text(75,2330,'Customer phase uploads: 10 = PRE_EXPORT; 18 = ARRIVAL / QUARANTINE_RELEASE during the journey.',21)+text(75,2368,'Conceptual system participant encapsulates UI, controllers and services. Shared sign-in, profiles and administration are not expanded.',20,'start','fill="#587080"')+'</g></svg>';
fs.mkdirSync(out,{recursive:true});
sharp(Buffer.from(s)).png().toFile(path.join(out,'EquiTransit-System-Communication.png')).then(()=>console.log('Generated system-wide PNG: 2800 × 2400.'));

