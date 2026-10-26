const fs=require('fs');const path=require('path');
const sharp=require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const out=path.join(__dirname,'..','class-diagrams');
const E=s=>String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
const txt=(x,y,s,size=24,anchor='start',extra='')=>`<text x="${x}" y="${y}" font-size="${size}" text-anchor="${anchor}" ${extra}>${E(s)}</text>`;
function create(W,H,title,sub){const boxes={},nodes=[],edges=[];
 const box=(id,x,y,w,name,attrs=[],methods=[],stereo='')=>{
  const hh=stereo?100:78, ah=attrs.length*35+24,mh=methods.length?methods.length*38+25:0,h=hh+ah+mh;
  const fit=(s,max=24)=>Math.min(max,Math.floor((w-40)/(s.length*.54)));
  let s=`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="5" fill="#f6d2df" stroke="#111" stroke-width="4"/>`;
  if(stereo)s+=txt(x+w/2,y+32,'«'+stereo+'»',21,'middle');
  s+=txt(x+w/2,y+(stereo?76:50),name,fit(name,31),'middle','font-weight="bold"');
  s+=`<path d="M${x} ${y+hh}H${x+w}${methods.length?'M'+x+' '+(y+hh+ah)+'H'+(x+w):''}" stroke="#111" stroke-width="3"/>`;
  attrs.forEach((a,i)=>s+=txt(x+20,y+hh+38+i*35,a,fit(a)));
  methods.forEach((m,i)=>s+=txt(x+20,y+hh+ah+38+i*38,m.text||m,fit(m.text||m), 'start',m.static?'text-decoration="underline"':''));
  const b={id,x,y,w,h};boxes[id]=b;nodes.push(s);return b;
 };
 const edge=(d,label='',lx=0,ly=0,dashed=false)=>{edges.push(`<path d="${d}" fill="none" stroke="#111" stroke-width="2.7" ${dashed?'stroke-dasharray="10 7"':''} marker-end="url(#open)"/>`+(label?txt(lx,ly,label,22):''));};
 const assoc=(d,labels=[])=>{edges.push(`<path d="${d}" fill="none" stroke="#111" stroke-width="2.7"/>`+labels.map(a=>txt(...a)).join(''));};
 const render=(notes=[])=>`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs><marker id="open" markerWidth="12" markerHeight="11" refX="11" refY="5.5" orient="auto"><path d="M0 0L11 5.5L0 11" fill="none" stroke="#111" stroke-width="1.6"/></marker></defs><rect width="100%" height="100%" fill="white"/><g font-family="Arial, sans-serif" fill="#111">${txt(W/2,65,title,43,'middle','font-weight="bold"')}${txt(W/2,112,sub,25,'middle')}${edges.join('')}${nodes.join('')}${notes.map((n,i)=>txt(100,H-50-(notes.length-1-i)*40,n,23)).join('')}</g></svg>`;
 return {box,edge,assoc,render,boxes};
}
(async()=>{
 const T=create(5500,3210,'EquiTransit — Design Class Diagram','F10 / Studio / F11 · Selected members · One line per operation');const {box:B,edge:L}=T;
 const pc=B('pc',1450,210,1250,'OperationalPlanController',[
  '− service : OperationalPlanService','− graphs : JourneyGraphService'],[
  '+ createDraft(orderId, req) : PlanDetailResponse',
  '+ compileGraph(orderId, req) : JourneyGraphView',
  '+ confirm(orderId, planId, req) : PlanDetailResponse']);
 const dc=B('dc',3100,210,1250,'DepartureGateController',['− service : DepartureGateService'],[
  '+ getReadiness(orderId) : DepartureReadinessResponse',
  '+ evaluateGates(orderId, req) : DepartureReadinessResponse',
  '+ authorize(orderId, req) : DepartureReadinessResponse',
  '+ depart(orderId, req) : DepartureReadinessResponse']);
 const gs=B('gs',150,210,1150,'JourneyGraphService',['− graphs : JourneyGraphRepository','− planning : OperationalPlanService'],[
  '+ get(orderId, planId) : JourneyGraphView',
  '+ compile(orderId, req) : JourneyGraphView',
  '+ layout(orderId, planId, req) : JourneyGraphView']);
 const gr=B('gr',150,660,1150,'JourneyGraphRepository',['− jdbc : NamedParameterJdbcTemplate'],[
  '+ nodes(planId) : List<GraphNodeInput>',
  '+ insert(planId, nodes, wires, layout) : void']);
 const ps=B('ps',1450,1050,1250,'OperationalPlanService',['− plans : PlanRepository','− quotes : QuotationCommitmentFacade'],[
  '+ createDraft(orderId, req) : PlanDetailResponse',
  '+ createGraphDraft(orderId, req, payload) : PlanDetailResponse',
  '+ confirm(orderId, planId, req) : PlanDetailResponse']);
 const ds=B('ds',3100,1050,1250,'DepartureGateService',[
  '− departures : DepartureRepository','− plans : PlanRepository',
  '− quotes : QuotationCommitmentFacade','− compliance : ComplianceReadinessFacade'],[
  '+ getReadiness(orderId) : DepartureReadinessResponse',
  '+ evaluateGates(orderId, req) : DepartureReadinessResponse',
  '+ authorize(orderId, req) : DepartureReadinessResponse',
  '+ depart(orderId, req) : DepartureReadinessResponse']);
 const pp=B('pp',150,1100,1150,'OperationalPlanPolicy',[],[
  {text:'+ validateLegs(legs) : void',static:true},
  {text:'+ confirmationBlockers(...) : List<String>',static:true}]);
 const dp=B('dp',4500,1100,900,'DepartureGatePolicy',[],[
  {text:'+ gateView(blockers) : GateDetailView',static:true},
  {text:'+ departureBlockers(...) : List<String>',static:true}]);
 const fin=B('fin',4500,1690,900,'QuotationCommitmentFacade',[
  '− quotes : QuoteRepository','− ledger : PaymentRepository'],[
  '+ readiness(...) : CommercialReadiness']);
 const legal=B('legal',4500,2160,900,'ComplianceReadinessFacade',['− data : ComplianceRepository'],[
  '+ readiness(...) : ComplianceReadiness']);
 const pr=B('pr',1450,2110,1250,'PlanRepository',['− jdbc : NamedParameterJdbcTemplate'],[
  '+ legs(planId) : List<PlanLegView>',
  '+ quarantineReservations(revId) : List<PlanQuarantineReservationView>',
  '+ insurancePolicies(revId) : List<PlanInsurancePolicyView>']);
 const dr=B('dr',3100,2110,1250,'DepartureRepository',['− jdbc : NamedParameterJdbcTemplate'],[
  '+ plan(orderId, planId, lock) : Optional<PlanGateRow>',
  '+ authorizeDeparture(...) : void',
  '+ departFirstLeg(orderId, planId, legId, now) : void']);
 const order=B('order',2600,2670,1350,'TransportOrder',[
  '− orderId : long','− orderStatus : String',
  '− currentRevisionId : Long','− currentPlanId : Long'],[],'domain entity');
 // Vertical chains: no Policy box is inserted between Service and Repository.
 L(`M2075 ${pc.y+pc.h}V1050`,'service',2095,850);
 L(`M3725 ${dc.y+dc.h}V1050`,'service',3745,850);
 L(`M2075 ${ps.y+ps.h}V2110`,'plans',2095,1860);
 L(`M3725 ${ds.y+ds.h}V2110`,'departures',3745,1860);
 L(`M725 ${gs.y+gs.h}V660`,'graphs',745,620);
 L('M1450 300H1300','graphs',1325,280);
 L('M1300 415H1360V875H1600V1050','planning.createGraphDraft()',1400,850);
 L('M1450 1210H1300','«use»',1320,1185,true);
 L('M4350 1210H4500','«use»',4370,1185,true);
 // Three independent gate sources; all side branches remain inside the layout.
 L('M4350 1340H4440V1770H4500','FINANCIAL',4515,1660);
 L('M4350 1450H4400V2240H4500','LEGAL (PRE_EXPORT)',4515,2130);
 L('M3100 1320H2825V2235H2700','OPERATIONAL',2735,2055);
 L(`M2075 ${pr.y+pr.h}V2800H2600`,'«maps SQL»',2160,2775,true);
 L(`M3725 ${dr.y+dr.h}V2670`,'«maps SQL»',3745,2590,true);
 const tech=T.render([
  'Solid open arrow = stored collaborator. Dashed open arrow = dependency. Underlined operation = static. No inheritance or composition implied.',
  'Studio save path in code: compileGraph() → JourneyGraphService.compile() → OperationalPlanService.createGraphDraft(); no saveGraphDraft() method exists.',
  'Financial = accepted quote + payment ledger; Legal = PRE_EXPORT readiness / holds; Operational = plan, reservations and resource checks.',
  'TransportOrder is the shared logical domain entity mapped by JDBC repositories. Constructors, helper methods and secondary collaborators are omitted.'
 ]);
 await sharp(Buffer.from(tech)).png().toFile(path.join(out,'EquiTransit-Design-Class-Revised.png'));

 const D=create(3500,3510,'EquiTransit — Domain Class Diagram','Main shipment lifecycle · Shared order revision · Multiplicities and derived readiness');const C=D.box,A=D.assoc;
 const ox=1250,cw=850,lx=150,rx=2450;
 const o=C('o',ox,190,cw,'TransportOrder',['− orderId : long','− orderStatus : OrderStatus','− currentRevisionId : Long','− currentPlanId : Long']);
 const r=C('r',ox,690,cw,'OrderRevision',['− revisionId : long','− revisionNo : int','− submittedAt : OffsetDateTime']);
 const q=C('q',lx,690,cw,'Quote',['− quoteId : long','− totalQuotedAmount : BigDecimal','− quoteStatus : QuoteStatus']);
 const pay=C('pay',lx,1180,cw,'PaymentRequest',['− paymentRequestId : long','− amountDue : BigDecimal','− dueAt : OffsetDateTime']);
 const alloc=C('alloc',lx,1670,cw,'PaymentAllocation',['− allocationId : long','− allocatedAmount : BigDecimal']);
 const tr=C('tr',lx,2160,cw,'PaymentTransaction',['− transactionId : long','− amountReceived : BigDecimal','− status : String']);
 const p=C('p',ox,1480,cw,'TransportPlan',['− planId : long','− planStatus : PlanState','− acceptedQuoteId : Long','− departureAuthorizedAt : OffsetDateTime']);
 const n=C('n',lx,2740,cw,'PlanCheckpointNode',['− nodeId : long','− nodeKey : String','− countryCode : String','− nodeType : String']);
 const l=C('l',ox,2230,cw,'TripLeg',['− legId : long','− transportMode : PlanLegMode','− legStatus : LegState','− actualDeparture : OffsetDateTime']);
 const h=C('h',ox,2880,cw,'TripHandover',['− handoverId : long','− handoverType : String','− handoverTime : OffsetDateTime','− customerDecision : String']);
 const comp=C('comp',rx,690,cw,'ComplianceDossier',[
  '− revisionId : long','− checklists : List<ComplianceChecklist>',
  '− submissions : List<DocumentSubmission>','− clearances : List<ComplianceClearance>'],[],'logical aggregate');
 const ready=C('ready',rx,1480,cw,'DepartureReadiness',[
  '− financialGate : GateDetailView','− legalGate : GateDetailView',
  '− operationalGate : GateDetailView','− readyForDeparture : boolean'],[],'derived value object');
 const receipt=C('receipt',rx,2230,cw,'HorseReceipt',[
  '− receiptId : long','− identityCheckResult : IdentityResult',
  '− receiptStatus : ReceiptState','− receivedAt : OffsetDateTime']);
 const vertical=(a,b,m1='1',m2='0..*')=>A(`M${a.x+a.w/2} ${a.y+a.h}V${b.y}`,[[a.x+a.w/2+20,a.y+a.h+35,m1,23],[b.x+b.w/2+20,b.y-20,m2,23]]);
 vertical(o,r);vertical(r,p);vertical(p,l);vertical(l,h);
 A(`M1000 790H1250`,[[1020,770,'0..*',23],[1205,770,'1',23]]);
 vertical(q,pay);vertical(pay,alloc);vertical(alloc,tr,'0..*','1');
 A('M2100 790H2450',[[2130,765,'1',23],[2355,765,'0..1',23],[2160,835,'legal dossier',21]]);
 // Accepted quote reference uses a separate internal corridor; no edge crossing.
 A(`M900 ${q.y+q.h}V990H1110V1580H1250`,[[920,q.y+q.h+35,'0..1',23],[1170,1555,'0..*',23],[950,965,'accepted quote',21]]);
 A('M1250 1660H1160V2850H1000',[[1185,1640,'1',23],[1030,2825,'0..*',23],[1045,2680,'graph nodes',21]]);
 D.edge('M2100 1590H2450','«derive»',2190,1560,true);
 A('M2100 2340H2450',[[2130,2310,'0..1',23],[2350,2310,'0..*',23]]);
 const dom=D.render([
  'Solid line = domain association; multiplicities are shown at the ends. No lifecycle ownership is inferred from a foreign key.',
  'ComplianceDossier groups the real checklist / requirement / document / review / clearance model; it is not an extra database table.',
  'DepartureReadiness is computed from financial, legal and operational evidence; departure authorization is stored on TransportPlan.',
  'PlanCheckpointNode and TripLeg represent graph checkpoints and route legs. FromNodeKey / ToNodeKey details are collapsed in this view.',
  'The order closes DELIVERED → CLOSED through OrderClosureService. State transitions are behavior, not a new Settlement entity.'
 ]);
 await sharp(Buffer.from(dom)).png().toFile(path.join(out,'EquiTransit-Domain-Class-Main-Flow.png'));
 console.log('Generated revised Design Class Diagram and Domain Class Diagram PNGs.');
})();

