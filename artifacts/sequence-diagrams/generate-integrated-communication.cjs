const fs=require('fs');
const path=require('path');
const base='C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/';
const sharp=require(base+'sharp');
const out=path.join(__dirname,'..','communication-diagrams');
const q=JSON.stringify;
const entities=[
 ['horse','horse','Horse'],['order','order','TransportOrder'],
 ['revision','revision','OrderRevision'],['quote','quote','Quotation'],
 ['payment','payment','PaymentRecord'],['plan','plan','TransportPlan'],
 ['dossier','dossier','ComplianceDossier'],['leg','leg','TripLeg'],
 ['handover','handover','TripHandover']
];
const roles=[
 ['customer','Customer','customerView','CustomerWorkspace','requestControl','CustomerWorkflowControl'],
 ['manager','Manager','managerView','ManagerWorkspace','caseControl','ManagerWorkflowControl'],
 ['coordinator','Coordinator','planningView','JourneyStudio','planningControl','JourneyPlanningControl'],
 ['specialist','Specialist','legalView','ComplianceWorkspace','legalControl','LegalComplianceControl'],
 ['driver','Driver','tripView','DriverWorkspace','tripControl','TripExecutionControl']
];
const edges=[];
function edge(a,b,lines){edges.push({a,b,label:lines.join('\n')});}
edge('customer','customerView',['R1: Submit / revise request','Q3: Accept quotation','P1: Report transfer','L1: Upload and submit document','H3: Confirm / dispute delivery']);
edge('customerView','requestControl',['R1.1: submitRequest(data)','Q3.1: acceptQuote(quoteId)','P1.1: reportTransfer(proof)','L1.1: submitEvidence(file)','H3.1: recordDecision(decision)']);
edge('manager','managerView',['A1: Assess and assign team','Q2: Approve / issue quotation','P2: Reconcile and allocate','D1: Authorize departure','C1: Close delivered order']);
edge('managerView','caseControl',['A1.1: decideAndAssign(input)','Q2.1: issueQuote(quoteId)','P2.1: reconcileAndAllocate(input)','D1.1: authorizeDeparture(orderId)','C1.1[DELIVERED]: closeOrder(id)']);
edge('coordinator','planningView',['O1: Draft route / assign resources','Q1: Prepare quotation','O2: Confirm operational plan']);
edge('planningView','planningControl',['O1.1: savePlanAndResources(input)','Q1.1: prepareQuotation(input)','O2.1[ready]: confirmPlan(input)']);
edge('specialist','legalView',['L2*: Review phase submissions','L3[eligible]: Clear selected phase']);
edge('legalView','legalControl',['L2.1*: reviewEvidence(decision, notes)','L3.1[approved]: clearPhase(phase)']);
edge('driver','tripView',['D2: Receive horse / depart','E1*: Record trip progress','H1: Record custody handover','H2[final leg]: Record final delivery']);
edge('tripView','tripControl',['D2.1[authorized, fit]: depart(input)','E1.1*: recordMilestoneOrWelfare(input)','H1.1: handOverToNextCarrier(input)','H2.1[release cleared]: deliver(input)']);
edge('requestControl','horse',['R1.1.1: readOwnedHorse()']);
edge('requestControl','order',['R1.1.2: create / submit / revise()']);
edge('requestControl','revision',['R1.1.3: captureImmutableSnapshot()']);
edge('requestControl','quote',['Q3.1.1[issued, valid]: accept()']);
edge('requestControl','payment',['P1.1.1: saveReportAndProof()']);
edge('requestControl','dossier',['L1.1.1: appendDocumentAndSubmission()']);
edge('requestControl','handover',['H3.1.1: appendCustomerDecision()']);
edge('caseControl','order',['A1.1.1[approved]: assignTeamAndApprove()','D1.1.1[gates pass]: authorize()','C1.1.1[DELIVERED]: close()']);
edge('caseControl','quote',['Q2.1.1: approveAndIssue()']);
edge('caseControl','payment',['P2.1.1: reconcileBankReceipt()','P2.1.2: allocateDeposit()']);
edge('caseControl','order',['P2.1.3[allocated]: setInPreparation()']);
edge('planningControl','revision',['Q1.1.1: readCurrentServiceChoices()']);
edge('planningControl','plan',['O1.1.1: saveRouteAndAssignments()','O2.1.2[commitments ready]: confirm()']);
edge('planningControl','quote',['Q1.1.2: priceAndCreateDraft()','O2.1.1: verifyAcceptedQuote()']);
edge('legalControl','dossier',['L2.1.1*: appendReviewAndFeedback()','L3.1.2[all checks pass]: recordClearance()']);
edge('legalControl','plan',['L3.1.1: readRouteAndTransitCountries()']);
edge('tripControl','order',['D2.1.1: verifyDepartureAuthorization()','D2.1.2[departed]: setInTransit()','H2.1.4: setDelivered()']);
edge('tripControl','leg',['E1.1.1*: appendProgressAndWelfare()','H1.1.1: completeCurrentLeg()','H1.1.2: retainNextLegConfirmed()']);
edge('tripControl','dossier',['H2.1.1: verifyQuarantineRelease()']);
edge('tripControl','handover',['H1.1.3: saveIntermediateHandover()','H2.1.2: validateReceiverAndSaveDelivery()']);
edge('tripControl','plan',['H2.1.3: completePlan()']);
const esc=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
function label(stereo,obj,cls){return q(`«${stereo}»\n${obj} : ${cls}`);}
(async()=>{
 const viz=await require(base+'@viz-js/viz').instance();
 let dot='digraph integrated { graph [rankdir=LR, bgcolor="white", nodesep=1.1, ranksep=1.5, pad=0.5, splines=spline, outputorder=edgesfirst]; node [shape=box, style=filled, fillcolor="#89d4f4", color="#425d70", penwidth=1.5, fontname="Arial", fontsize=18, margin="0.22,0.18"]; edge [fontname="Arial", fontsize=16, fontcolor="#243f53", color="#667d8e", penwidth=1.4, arrowhead=vee, arrowsize=0.7];';
 for(const [id,name,view,viewCls,control,controlCls] of roles){dot+=`${id} [shape=plain, label=${q('\n\n\n'+name)}, width=1.3, height=1.5, fontsize=22]; ${view} [label=${label('user interaction',view,viewCls)}]; ${control} [label=${label('coordinator',control,controlCls)}, fillcolor="#b0ddf2"];`;}
 for(const [id,obj,cls] of entities)dot+=`${id} [label=${label('entity',obj,cls)}, fillcolor="#e5f0f9"];`;
 for(const which of [0,2,4])dot+='{rank=same;'+roles.map(r=>r[which]).join(';')+';}';
 dot+='{rank=same;'+entities.map(r=>r[0]).join(';')+';}';
 for(const e of edges)dot+=`${e.a}->${e.b} [label=${q(e.label)}];`;
 // Keep actor and boundary rows aligned in a stable top-to-bottom order.
 
 
 dot+='}';
 // Combine message families sharing the same pair into one structural link.
 const combined=new Map();
 for(const e of edges){const key=e.a+'->'+e.b;const prev=combined.get(key);
  if(prev)prev.label+='\n'+e.label;else combined.set(key,{...e});}
 dot=dot.slice(0,dot.indexOf('customer->customerView'))+
  [...combined.values()].map(e=>`${e.a}->${e.b} [label=${q(e.label)}];`).join('')+'}';
 dot=dot.replace('splines=spline','splines=polyline')
  .replace('arrowhead=vee','arrowhead=none')
  .replaceAll('#243f53','#111111').replaceAll('#667d8e','#222222')
  .replaceAll('#425d70','#222222');
 const layout=viz.renderJSON(dot);
 const svgViz=await require(base+'@viz-js/viz').instance();
 let raw=svgViz.renderString(dot,{format:'svg'});
 // Replace each actor's text-only node with a UML stick figure.
 for(const [id,name] of roles){const o=layout.objects.find(o=>o.name===id);const [x,y]=o.pos.split(',').map(Number);const cy=-y-19;
  const a=`<g id="actor-${id}"><title>${id}</title><circle cx="${x}" cy="${cy-21}" r="8" fill="#89d4f4" stroke="#425d70" stroke-width="1.5"/><path d="M${x} ${cy-13}V${cy+16}M${x-17} ${cy-2}H${x+17}M${x} ${cy+16}L${x-16} ${cy+39}M${x} ${cy+16}L${x+16} ${cy+39}" fill="none" stroke="#425d70" stroke-width="1.7"/><text x="${x}" y="${cy+65}" text-anchor="middle" font-family="Arial" font-size="22" fill="#183647">${name}</text></g>`;
  raw=raw.replace(new RegExp('<g id="node\\d+" class="node">\\s*<title>'+id+'<\\/title>[\\s\\S]*?<\\/g>'),a);
 }
 // Small directed message arrows run along links; links have no arrowheads.
 // Graphviz gives each routed polyline as consecutive cubic Bezier segments.
 for(const e of layout.edges||[]){
  const pts=(e._draw_||[]).find(x=>x.op==='b'||x.op==='B')?.points;
  if(!pts||pts.length<4)continue;
  const candidates=[];
  for(let j=0;j+3<pts.length;j+=3){const p=pts.slice(j,j+4);const t=.48,u=1-t;
   const x=u*u*u*p[0][0]+3*u*u*t*p[1][0]+3*u*t*t*p[2][0]+t*t*t*p[3][0];
   const y=u*u*u*p[0][1]+3*u*u*t*p[1][1]+3*u*t*t*p[2][1]+t*t*t*p[3][1];
   const dx=3*u*u*(p[1][0]-p[0][0])+6*u*t*(p[2][0]-p[1][0])+3*t*t*(p[3][0]-p[2][0]);
   const dy=3*u*u*(p[1][1]-p[0][1])+6*u*t*(p[2][1]-p[1][1])+3*t*t*(p[3][1]-p[2][1]);
   const len=Math.hypot(p[3][0]-p[0][0],p[3][1]-p[0][1]);
   candidates.push({x,y,dx,dy,len});
  }
  const a=candidates.sort((a,b)=>b.len-a.len)[0];
  if(!a)continue;
  const len=Math.hypot(a.dx,a.dy)||1,ux=a.dx/len,uy=-a.dy/len;
  // Offset below/right of the link, away from Graphviz's label above it.
  const nx=-uy,ny=ux,cx=a.x+nx*9,cy=-a.y+ny*9;
  const x1=cx-ux*16,y1=cy-uy*16,x2=cx+ux*16,y2=cy+uy*16;
  const arrow=`<path d="M${x1},${y1}L${x2},${y2}" fill="none" stroke="#111" stroke-width="1.8"/><path d="M${x2},${y2}L${x2-ux*9+nx*4.5},${y2-uy*9+ny*4.5}L${x2-ux*9-nx*4.5},${y2-uy*9-ny*4.5}Z" fill="#111"/>`;
  const title=(layout.objects||[]).find(x=>x._gvid===e.tail)?.name+'&#45;&gt;'+(layout.objects||[]).find(x=>x._gvid===e.head)?.name;
  raw=raw.replace(new RegExp('(<g id="edge\\d+" class="edge">\\s*<title>'+title+'<\\/title>[\\s\\S]*?)(<\\/g>)'),'$1'+arrow+'$2');
 }
 raw=raw.replace(/<g id="node\d+" class="node">[\s\S]*?<\/g>/g,g=>g
  .replace(/fill="#[0-9a-fA-F]{6}"/g,'fill="url(#objectBlue)"'));
 raw=raw.replace(/<text([^>]*)>([^<]* : [^<]*)<\/text>/g,
  '<text$1 text-decoration="underline" font-weight="bold">$2</text>');
 const vb=raw.match(/viewBox="([^"]+)"/)[1].split(' ').map(Number),w=vb[2],h=vb[3];
 const inner=raw.slice(raw.indexOf('>',raw.indexOf('<svg'))+1,raw.lastIndexOf('</svg>'));
 const H=h+440;
 const notes=[
  'Integrated analysis model • Main use-case families across all five actors • Messages are aggregated per link.',
  'R: Request   A: Assessment   Q: Quotation   P: Payment   O: Operational plan   L: Compliance   D: Departure   E: Execution   H: Handover   C: Closure',
  'Read each prefix separately: R1 → R1.1 → R1.1.1. Prefixes identify use-case families, not one global timeline. * = iteration; [guard] = condition.',
  '«user interaction» = UI boundary; «coordinator» = analysis control object; «entity» = domain object. These labels do not claim matching Java classes.',
  'Shared entity instances connect the workflows. Database wrappers, detailed files, audit calls, replies and administrative use cases are collapsed at this level.',
  'L applies separately to PRE_EXPORT, ARRIVAL and QUARANTINE_RELEASE. Final delivery needs release clearance; Manager closes DELIVERED orders.'
 ];
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${H}" viewBox="0 0 ${w} ${H}"><defs><linearGradient id="objectBlue" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9cdef7"/><stop offset="1" stop-color="#73c6e9"/></linearGradient></defs><rect width="100%" height="100%" fill="white"/><g font-family="Arial" fill="#111"><text x="${w/2}" y="45" font-size="28" font-weight="bold" text-anchor="middle">EquiTransit — Integrated Communication Diagram</text><text x="${w/2}" y="78" font-size="17" text-anchor="middle">COMET / UML · 5 actors · Analysis objects, links and numbered messages</text></g><g transform="translate(0,110)">${inner}</g><g font-family="Arial" fill="#333">${notes.map((t,i)=>`<text x="35" y="${h+160+i*37}" font-size="16">${esc(t)}</text>`).join('')}</g></svg>`;
 // Original vector is used internally; the requested deliverable is PNG only.
 const targetW=Math.min(7200,Math.round(w*2));
 await sharp(Buffer.from(svg)).resize({width:targetW}).png().toFile(path.join(out,'EquiTransit-Integrated-Communication-COMET.png'));
 fs.writeFileSync(path.join(out,'integrated-communication-qa.json'),JSON.stringify({objects:roles.length*3+entities.length,actors:5,links:edges.length,width:targetW,sourceWidth:w,sourceHeight:H,analysisModel:true},null,2));
 console.log(JSON.stringify({png:'EquiTransit-Integrated-Communication.png',w,h,objects:24,links:edges.length}));
})();






