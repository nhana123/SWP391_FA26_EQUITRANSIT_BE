const fs = require('fs');
const path = require('path');
const sharp = require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const out = __dirname;
const E = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
const definitions = [
 {id:'01-customer',actor:'Customer',title:'Submit Transport Request',view:'TransportRequestView',controller:'TransportOrderController',service:'TransportOrderService',pre:'Signed in as CUSTOMER; horse belongs to the customer.',steps:[
  {action:'Create transport request',call:'create(OrderDraftRequest)',reply:'OrderResponse [DRAFT]',note:'POST /api/transport-orders'},
  {kind:'opt',guard:'Customer edits the saved draft',steps:[{action:'Save changes',call:'saveDraft(id, request)',reply:'OrderResponse + recordVersion',note:'PUT /api/transport-orders/{id}/draft'}]},
  {action:'Submit request',call:'submit(id, OrderSubmitRequest)',reply:'OrderRevisionResponse [SUBMITTED]',note:'POST /api/transport-orders/{id}/submit'},
  {kind:'opt',guard:'Revision is allowed before protected commitments',steps:[{action:'Revise request and provide reason',call:'revise(id, OrderRevisionRequest)',reply:'OrderRevisionResponse [new revision]',note:'POST /api/transport-orders/{id}/revisions'}]},
 ],foot:'Submission requests Manager assessment. It does not confirm a booking or accept a quotation.'},
 {id:'02-manager',actor:'Manager',title:'Assess Request & Assign Case Team',view:'RequestAssessmentView',controller:'ManagerAssessmentController',service:'OrderAssessmentService',pre:'Manager has reviewed the current submitted revision and selected active staff.',steps:[
  {kind:'alt',guard:'decision = APPROVED; Specialist and Coordinator selected',steps:[{action:'Approve request and assign team',call:'decide(id, AssessmentRequest)',reply:'AssessmentReceipt [APPROVED]',note:'POST /api/manager/transport-orders/{id}/decisions'}],elseGuard:'decision = REJECTED or CHANGES_REQUESTED; reason supplied',otherwise:[{action:'Reject or request changes',call:'decide(id, AssessmentRequest)',reply:'AssessmentReceipt [decision recorded]'}]},
  {kind:'opt',guard:'Approved case remains eligible for reassignment',steps:[{action:'Replace Specialist or Coordinator',call:'assign(id, role, CaseAssignmentRequest)',reply:'AssessmentReceipt [assignment updated]',note:'PUT /api/manager/transport-orders/{id}/assignments/{role}'}]},
 ],foot:'Approval and initial team assignment are atomic. Payment allocation is a separate Manager workflow.'},
 {id:'03-coordinator',actor:'Coordinator',title:'Manage Operational Plan',view:'OperationalPlanView',controller:'OperationalPlanController',service:'OperationalPlanService',pre:'Assigned COORDINATOR; order is APPROVED or IN_PREPARATION.',steps:[
  {action:'Select Manage Operational Plan',call:'list(orderId, page, size)',reply:'PlanPageResponse',note:'GET /api/transport-orders/{orderId}/plans'},
  {kind:'opt',guard:'Create Draft selected',steps:[{action:'Create draft route and legs',call:'createDraft(orderId, PlanDraftRequest)',reply:'PlanDetailResponse [DRAFT]',note:'AIR legs may carry estimatedAirfare; a price estimate is not a flight reservation.'}]},
  {kind:'opt',guard:'Resource preparation selected; route includes ROAD and AIR legs',steps:[{action:'Assign vehicle and driver to ROAD leg',call:'assignRoad(orderId, planId, legId, body)',reply:'PlanDetailResponse'}, {action:'Assign shared stall to AIR leg',call:'assignStall(orderId, planId, legId, body)',reply:'PlanDetailResponse'}, {action:'Record confirmed flight reservation',call:'reserveFlight(orderId, planId, legId, body)',reply:'PlanDetailResponse'}]},
  {kind:'opt',guard:'Confirm selected; accepted quote and all resources ready',steps:[{action:'Confirm operational plan',call:'confirm(orderId, planId, PlanConfirmRequest)',reply:'PlanDetailResponse [CONFIRMED]',note:'POST /api/transport-orders/{orderId}/plans/{planId}/confirm'}]},
 ],foot:'Required quarantine reservations and insurance must also pass confirmation checks. Failed checks return 409.'},
 {id:'04-specialist',actor:'Specialist',title:'Review Documents & Clear Compliance Phase',view:'ComplianceWorkspaceView',controller:'ComplianceController',service:'ComplianceService',pre:'Assigned SPECIALIST. Customer has submitted evidence, or Specialist uploads on their behalf.',steps:[
  {action:'Open compliance checklist',call:'overview(order, phase, page, size, true)',reply:'ComplianceOverview [requirements + files + feedback]',note:'GET /api/transport-orders/{order}/compliance?phase=...&bootstrap=true'},
  {kind:'opt',guard:'Specialist submits a document for the customer',steps:[{action:'Upload file and submit requirement',call:'uploadAndSubmit(order, id, body)',reply:'ComplianceReceipt [submissionId + fileKey]',note:'POST .../compliance/requirements/{id}/upload-and-submit; one transaction'}]},
  {kind:'loop',guard:'For each current submission requiring review',steps:[{action:'Approve or reject with feedback',call:'review(order, id, ComplianceReviewRequest)',reply:'ComplianceReceipt [review recorded]',note:'POST .../compliance/submissions/{id}/reviews'}]},
  {kind:'alt',guard:'All required documents approved and other clearance checks pass',steps:[{action:'Clear selected phase',call:'clear(order, ComplianceClearanceRequest)',reply:'ComplianceReceipt [clearance recorded]',note:'POST .../compliance/clearances; default authority procedure may be created'}],elseGuard:'Evidence is rejected, missing, expired or otherwise invalid',otherwise:[{action:'Read blockers and latest feedback',call:'overview(order, phase, page, size, false)',reply:'ComplianceOverview [not cleared + reasons]'}]},
 ],foot:'Apply independently to PRE_EXPORT, ARRIVAL and QUARANTINE_RELEASE. Customer can read feedback and resubmit.'},
 {id:'05-driver',actor:'Driver',title:'Record Intermediate Handover or Final Delivery',view:'TripHandoverView',controller:'DriverHandoverController',service:'TripHandoverService',pre:'Assigned DRIVER; current leg has departed. Signature and photo file keys were uploaded through HandoverFileController.',steps:[
  {action:'Open handover history for assigned leg',call:'getHandovers(legId)',serviceCall:'getHandoversForLeg(legId)',reply:'List<TripHandoverResponse>',note:'GET /api/driver/legs/{legId}/handovers'},
  {kind:'alt',guard:'Intermediate custody transfer',steps:[{action:'Record transfer to next carrier or quarantine',call:'recordIntermediate(legId, request)',serviceCall:'recordIntermediateByDriver(legId, request)',reply:'201 TripHandoverResponse',note:'POST /api/driver/legs/{legId}/intermediate-handover; next leg stays CONFIRMED'}],elseGuard:'Last leg; final delivery to authorized receiver',otherwise:[{action:'Record final delivery with signature and photo',call:'recordFinalDelivery(legId, request)',serviceCall:'recordFinalDeliveryByDriver(legId, request)',reply:'200 OperationalCompletionReceipt [DELIVERED]',note:'POST /api/driver/legs/{legId}/final-delivery'}]},
 ],foot:'Final delivery requires QUARANTINE_RELEASE clearance, receiver authority and files. Manager closes DELIVERED orders separately.'},
];
function render(d) {
 const W=1640, xs=[115,470,945,1410], top=185, parts=[], boxes=[]; let y=270; let num=0;
 const text=(x,yy,s,size=16,anchor='middle',extra='')=>`<text x="${x}" y="${yy}" font-size="${size}" text-anchor="${anchor}" ${extra}>${E(s)}</text>`;
 const line=(x1,y1,x2,y2,extra='')=>`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" ${extra}/>`;
 function message(a,b,yy,label,ret=false) {
  const x1=xs[a]+(a?6:0),x2=xs[b]+(b?6:0);
  parts.push(line(x1,yy,x2,yy,`stroke="#26343d" stroke-width="1.5" ${ret?'stroke-dasharray="6 4"':''} marker-end="url(#${ret?'open':'arrow'})"`));
  parts.push(text((x1+x2)/2,yy-10,label,15));
 }
 function action(s) {
  if(s.note){parts.push(`<rect x="${xs[1]-75}" y="${y-8}" width="1190" height="32" fill="#fffdf3" stroke="#d9d7c8"/>`);parts.push(text(xs[1]-62,y+13,s.note,13,'start'));y+=57;}
  const start=y; num++;
  parts.push(`<rect x="${xs[1]}" y="${start-5}" width="12" height="177" fill="#87d3f4" stroke="#455d6a"/><rect x="${xs[2]}" y="${start+37}" width="12" height="91" fill="#87d3f4" stroke="#455d6a"/><rect x="${xs[3]}" y="${start+79}" width="12" height="51" fill="#87d3f4" stroke="#455d6a"/>`);
  message(0,1,start,`${num}. ${s.action}`);message(1,2,start+42,s.call);message(2,3,start+84,s.serviceCall||s.call);message(3,2,start+126,s.reply,true);message(2,1,start+168,s.reply,true);
  y+=205;
 }
 function steps(ss){for(const s of ss){if(!s.kind){action(s);continue;}const sy=y; y+=57; steps(s.steps);let split=null;if(s.otherwise){split=y;y+=74;steps(s.otherwise);}boxes.push({sy,ey:y+5,kind:s.kind,guard:s.guard,split,elseGuard:s.elseGuard});y+=35;}}
 steps(d.steps); const H=y+125;
 const frames=boxes.map(b=>`<rect x="45" y="${b.sy}" width="1550" height="${b.ey-b.sy}" fill="none" stroke="#283a46" stroke-width="1.5"/><path d="M45 ${b.sy} h62 v21 l-11 10 H45Z" fill="white" stroke="#283a46"/>${text(56,b.sy+21,b.kind,15,'start','font-weight="bold"')}${text(128,b.sy+23,'['+b.guard+']',15,'start')}${b.split?line(45,b.split,1595,b.split,'stroke="#283a46" stroke-dasharray="8 5"')+text(128,b.split+25,'['+b.elseGuard+']',15,'start'):''}`).join('');
 let svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs><pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse"><path d="M20 0H0V20" fill="none" stroke="#eaf0f4" stroke-width=".7"/></pattern><marker id="arrow" markerWidth="9" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8Z" fill="#26343d"/></marker><marker id="open" markerWidth="9" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="#26343d" stroke-width="1.3"/></marker></defs><rect width="100%" height="100%" fill="white"/><rect x="20" y="145" width="1600" height="${H-210}" fill="url(#grid)"/><g font-family="Arial, sans-serif" fill="#182f40">${text(45,43,'EQUITRANSIT / '+d.actor.toUpperCase(),16,'start','font-weight="bold" fill="#245c78"')}${text(45,82,d.title,27,'start','font-weight="bold"')}${text(45,114,d.pre,14,'start')}`;
 const labels=[d.actor,d.view,d.controller,d.service];
 xs.forEach((x,i)=>{svg+=line(x+6,top+44,x+6,H-94,'stroke="#6d7a83" stroke-width="1.2" stroke-dasharray="6 5"');if(i===0){svg+=`<circle cx="${x+6}" cy="${top-20}" r="9" fill="#87d3f4" stroke="#344c5c"/>`+line(x+6,top-11,x+6,top+14,'stroke="#344c5c" stroke-width="1.7"')+line(x-10,top,x+22,top,'stroke="#344c5c" stroke-width="1.7"')+line(x+6,top+14,x-9,top+33,'stroke="#344c5c" stroke-width="1.7"')+line(x+6,top+14,x+21,top+33,'stroke="#344c5c" stroke-width="1.7"')+text(x+6,top+58,d.actor,16);}else{svg+=`<rect x="${x-162}" y="${top-25}" width="336" height="64" fill="#87d3f4" stroke="#455d6a" stroke-width="1.5"/>`+text(x+6,top+12,': '+labels[i],17,'middle','font-weight="bold"');}});
 svg+=frames+parts.join('')+text(45,H-44,d.foot,14,'start')+text(45,H-18,'UI view names are conceptual boundaries; Controller / Service names and methods follow the Backend source.',12,'start','fill="#5b6c77"')+'</g></svg>';
 return {svg,W,H};
}
(async()=>{
 const rendered=[];
 for(const d of definitions){const r=render(d);fs.writeFileSync(path.join(out,d.id+'.svg'),r.svg);await sharp(Buffer.from(r.svg)).png().toFile(path.join(out,d.id+'.png'));rendered.push({...d,...r});}
 fs.writeFileSync(path.join(out,'diagrams.json'),JSON.stringify(definitions,null,2));
 const data=JSON.stringify(rendered.map(d=>({id:d.id,actor:d.actor,title:d.title,svg:d.svg}))).replace(/</g,'\\u003c');
 fs.writeFileSync(path.join(out,'EquiTransit-Sequence-Diagrams.html'),`<!doctype html><html lang="vi"><meta charset="utf-8"><title>EquiTransit Sequence Diagrams</title><style>body{margin:0;background:#eef3f6;font:15px Arial}header{position:sticky;top:0;padding:16px 24px;background:#17394b;color:white;display:flex;gap:16px;align-items:center;z-index:2}select,button,a{font:inherit;padding:8px;border-radius:5px}main{padding:24px}#canvas{width:max-content;margin:auto;background:white;box-shadow:0 4px 24px #0001}#canvas svg{display:block;height:auto}#help{padding:8px 24px;color:#53636d}a{color:white}</style><header><b>EquiTransit · Sequence diagrams</b><select id="page"></select><button id="minus">−</button><span id="zoom">70%</span><button id="plus">+</button><button id="fit">Fit width</button><a id="download">Download SVG</a></header><div id="help">5 actors · Actor → View → Controller → Service · Scroll to read; use + / − to zoom.</div><main><div id="canvas"></div></main><script>const pages=${data};let scale=.7;const sel=document.querySelector('#page'),can=document.querySelector('#canvas');pages.forEach((p,i)=>sel.add(new Option(p.actor+' — '+p.title,i)));function size(){can.firstElementChild.style.width=(1640*scale)+'px';document.querySelector('#zoom').textContent=Math.round(scale*100)+'%'}function show(){const p=pages[sel.value||0];can.innerHTML=p.svg;size();const a=document.querySelector('#download');if(a.href)URL.revokeObjectURL(a.href);a.href=URL.createObjectURL(new Blob([p.svg],{type:'image/svg+xml'}));a.download=p.id+'.svg'}sel.onchange=show;document.querySelector('#minus').onclick=()=>{scale=Math.max(.3,scale-.1);size()};document.querySelector('#plus').onclick=()=>{scale=Math.min(2,scale+.1);size()};document.querySelector('#fit').onclick=()=>{scale=Math.min(1,(innerWidth-65)/1640);size()};show();</script></html>`);
 const thumbs=await Promise.all(rendered.map(async(d,i)=>({input:await sharp(Buffer.from(d.svg)).resize({width:492}).png().toBuffer(),left:(i%3)*520+14,top:Math.floor(i/3)*900+14})));
 await sharp({create:{width:1560,height:1800,channels:3,background:'#edf2f6'}}).composite(thumbs).png().toFile(path.join(out,'preview.png'));
 fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(rendered.map(({id,actor,title,W,H})=>({id,actor,title,width:W,height:H})),null,2));
 console.log('Generated '+rendered.length+' SVG / PNG diagrams and offline HTML viewer.');
})();



