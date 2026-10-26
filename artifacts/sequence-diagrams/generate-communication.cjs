const fs=require('fs');
const path=require('path');
const sharp=require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const dir=path.join(__dirname,'..','communication-diagrams');fs.mkdirSync(dir,{recursive:true});
const base=JSON.parse(fs.readFileSync(path.join(__dirname,'diagrams.json'),'utf8'));
const defs=[
 {title:'Submit Transport Request',steps:[['Submit saved request','submit(id, request)','submit(id, request)']],pre:'Customer owns the saved DRAFT and has a current recordVersion.',result:'An immutable revision is captured; the request becomes SUBMITTED.',note:'Manager assessment, quotation acceptance and booking confirmation are separate interactions.'},
 {title:'Approve Request & Assign Case Team',steps:[['Approve and assign team','decide(id, request)','decide(id, request)']],pre:'Manager reviews the current revision; decision = APPROVED; active Specialist and Coordinator supplied.',result:'Assessment and initial assignments commit atomically; the order becomes APPROVED.',note:'A negative decision uses the same method with REJECTED or CHANGES_REQUESTED and a reason.'},
 {title:'Manage Operational Plan',steps:[['Create draft plan','createDraft(orderId, request)','createDraft(orderId, request)'],['Assign ROAD resources','assignRoad(orderId, planId, legId, body)','assignRoad(orderId, planId, legId, body)'],['[ready] Confirm plan','confirm(orderId, planId, request)','confirm(orderId, planId, request)']],pre:'Assigned Coordinator; order APPROVED or IN_PREPARATION. Example shows a ROAD assignment.',result:'Plan confirmation succeeds only with an accepted quotation and all required resources ready.',note:'AIR stall / flight, quarantine and insurance setup are separate messages when required; draft airfare is not a reservation.'},
 {title:'Review Documents & Clear Phase',steps:[['Open checklist','overview(order, phase, page, size, true)','overview(order, phase, page, size, true)'],['* [each submission] Review evidence','review(order, id, body)','review(order, id, body)'],['[eligible] Clear phase','clear(order, body)','clear(order, body)']],pre:'Assigned Specialist; evidence has been submitted by Customer or Specialist. One compliance phase per interaction.',result:'Clearance is recorded only when required evidence is approved and all other phase checks pass.',note:'Review may APPROVE or REJECT with feedback. Apply separately to PRE_EXPORT, ARRIVAL and QUARANTINE_RELEASE.'},
 {title:'Record Final Delivery',steps:[['Submit final delivery','recordFinalDelivery(legId, request)','recordFinalDeliveryByDriver(legId, request)']],pre:'Assigned Driver; final leg departed; signature and photo file keys already uploaded.',result:'After validation, final delivery completes the leg and plan; order status becomes DELIVERED.',note:'Requires QUARANTINE_RELEASE clearance and authorized receiver. Manager closes the order in a separate interaction.'}
];
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
const txt=(x,y,t,size=18,anchor='middle',extra='')=>`<text x="${x}" y="${y}" text-anchor="${anchor}" font-size="${size}" ${extra}>${esc(t)}</text>`;
const line=(x,y,a,b,extra='')=>`<line x1="${x}" y1="${y}" x2="${a}" y2="${b}" ${extra}/>`;
function node(x,y,name){return `<rect x="${x-190}" y="${y-40}" width="380" height="80" fill="#87d3f4" stroke="#405969" stroke-width="2"/>`+txt(x,y+6,': '+name,19,'middle','text-decoration="underline" font-weight="bold"');}
(async()=>{
 for(let i=0;i<5;i++){
  const b=base[i],d=defs[i],n=d.steps.length;
  let s=`<svg xmlns="http://www.w3.org/2000/svg" width="1840" height="1260" viewBox="0 0 1840 1260"><defs><pattern id="grid" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="#e9eff3" stroke-width=".8"/></pattern><marker id="arrow" markerWidth="9" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8Z" fill="#263d4b"/></marker></defs><rect width="100%" height="100%" fill="white"/><rect x="25" y="150" width="1790" height="850" fill="url(#grid)"/><g font-family="Arial, sans-serif" fill="#183444">`;
  s+=txt(45,44,'EQUITRANSIT / '+b.actor.toUpperCase()+' / COMMUNICATION DIAGRAM',18,'start','fill="#245c78" font-weight="bold"')+txt(45,89,d.title,31,'start','font-weight="bold"')+txt(45,125,d.pre,16,'start');
  // Structural links do not encode elapsed time. Small arrows carry messages.
  s+=line(200,340,1050,340,'stroke="#647c8a" stroke-width="2"')+line(1240,380,1240,780,'stroke="#647c8a" stroke-width="2"')+line(1050,820,450,820,'stroke="#647c8a" stroke-width="2"');
  s+=`<circle cx="180" cy="290" r="13" fill="#87d3f4" stroke="#405969" stroke-width="2"/>`+line(180,303,180,355,'stroke="#405969" stroke-width="2.2"')+line(153,324,207,324,'stroke="#405969" stroke-width="2.2"')+line(180,355,155,389,'stroke="#405969" stroke-width="2.2"')+line(180,355,205,389,'stroke="#405969" stroke-width="2.2"')+txt(180,422,b.actor,21);
  s+=node(1240,340,b.view)+node(1240,820,b.controller)+node(260,820,b.service);
  d.steps.forEach((step,j)=>{
   const k=j+1, y=205+j*44;
   s+=txt(620,y,`${k}: ${step[0]}`,19)+line(840,y+9,900,y+9,'stroke="#263d4b" stroke-width="1.5" marker-end="url(#arrow)"');
   const vy=480+j*92;
   s+=txt(1280,vy,`${k}.1: ${step[1]}`,17,'start')+line(1260,vy-9,1260,vy+24,'stroke="#263d4b" stroke-width="1.5" marker-end="url(#arrow)"');
   const sy=910+j*37;
   s+=txt(750,sy,`${k}.1.1: ${step[2]}`,18)+line(620,sy+11,560,sy+11,'stroke="#263d4b" stroke-width="1.5" marker-end="url(#arrow)"');
  });
  s+=`<rect x="45" y="1040" width="1750" height="155" rx="8" fill="#f4f8fb" stroke="#c9d8e2"/>`+txt(65,1074,'Result: '+d.result,18,'start')+txt(65,1111,d.note,16,'start')+txt(65,1154,'Read: 1 → 1.1 → 1.1.1, then 2 → 2.1 → 2.1.1. [guard] = condition; * = iteration.',16,'start')+txt(45,1230,'View names are conceptual UI boundaries. Controller / Service names and calls follow the Backend source. Replies omitted for clarity.',15,'start','fill="#5b7180"')+'</g></svg>';
  await sharp(Buffer.from(s)).png().toFile(path.join(dir,b.id+'.png'));
 }
 console.log('Created 5 communication diagram PNGs.');
})();
