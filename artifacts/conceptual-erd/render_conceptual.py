from PIL import Image, ImageDraw, ImageFont
import math,json
from pathlib import Path
out=Path(__file__).parent
W,H=5100,2920
S=2
im=Image.new('RGB',(W*S,H*S),'white');d=ImageDraw.Draw(im)
fontpath='C:/Windows/Fonts/'
def font(size,bold=False): return ImageFont.truetype(fontpath+('arialbd.ttf' if bold else 'arial.ttf'),round(size*S))
ink='#334155';entity='#53565C';purple='#7773A7';blue='#174F7D';line='#657080'
shapes=[]
def text(x,y,label,size=26,fill=ink,bold=False,underline=False):
 f=font(size,bold);box=d.multiline_textbbox((0,0),label,font=f,spacing=6*S,align='center');tw=box[2]-box[0];th=box[3]-box[1]
 d.multiline_text((x*S-tw/2,y*S-th/2-box[1]),label,font=f,fill=fill,spacing=6*S,align='center')
 if underline:d.line(((x*S-tw/2,y*S+th/2+4*S),(x*S+tw/2,y*S+th/2+4*S)),fill=fill,width=2*S)
def shape(name,x,y,w,h,kind,label,color):
 obj=dict(name=name,x=x,y=y,w=w,h=h,kind=kind,label=label,color=color);shapes.append(obj);return obj
E={}
rows=[
 ('Customer','CUSTOMER',350,1750,'CustomerID','Full name','below'),
 ('Horse','HORSE',1150,1000,'HorseID','Horse name','above'),
 ('TransportOrder','TRANSPORT ORDER',1150,1750,'OrderID','Order number','above'),
 ('OrderRevision','ORDER REVISION',2050,1750,'RevisionID','Revision number','above'),
 ('Quote','QUOTATION',2050,1000,'QuoteID','Total amount','above'),
 ('QuotePaymentSchedule','PAYMENT SCHEDULE',2950,1000,'ScheduleID','Payment stage','above'),
 ('PaymentRequest','PAYMENT REQUEST',3850,1000,'PaymentRequestID','Amount due','above'),
 ('PaymentTransaction','BANK PAYMENT',3850,350,'TransactionID','Amount received','above'),
 ('TransportPlan','TRANSPORT PLAN',2950,1750,'PlanID','Plan status','above'),
 ('TripLeg','TRIP LEG',3850,1750,'LegID','Transport mode','left'),
 ('TripHandover','HANDOVER',4650,1750,'HandoverID','Handover time','above'),
 ('FleetVehicle','VEHICLE',4650,1000,'VehicleID','Plate number','above'),
 ('Supplier','SUPPLIER',4650,350,'SupplierID','Supplier name','above'),
 ('StaffProfile','STAFF',4650,2500,'StaffID','Full name','below'),
 ('DocumentRequirement','DOCUMENT\nREQUIREMENT',2050,2500,'RequirementID','Document type','below'),
 ('DocumentSubmission','DOCUMENT\nSUBMISSION',2950,2500,'SubmissionID','Submission status','below'),
 ('DocumentVersion','DOCUMENT VERSION',3850,2500,'DocumentVersionID','File reference','below')]
attrs=[]
for key,label,x,y,pk,attr,side in rows:
 E[key]=shape(key,x,y,430,110,'rect',label,entity)
 positions=[(x-160,y+(-200 if side=='above' else 200)),(x+160,y+(-200 if side=='above' else 200))]
 if side=='left': positions=[(x-290,y-240),(x-290,y+240)]
 for i,(an,(ax,ay)) in enumerate(zip([pk,attr],positions)):
  a=shape(key+'.'+an,ax,ay,280,76,'ellipse',an,blue);a['key']=i==0;attrs.append((E[key],a))
R=[]
def rel(a,b,x,y,label,ca,cb,mandatoryA=False,mandatoryB=False):
 node=shape('R'+str(len(R)+1),x,y,220,140,'diamond',label,purple)
 R.append((E[a],node,E[b],ca,cb,mandatoryA,mandatoryB))
rel('Customer','Horse',350,1000,'OWNS','1','N',False,True)
rel('Customer','TransportOrder',750,1750,'PLACES','1','N',False,True)
rel('Horse','TransportOrder',1150,1375,'TRAVELS\nIN','1','N')
rel('TransportOrder','OrderRevision',1600,1750,'HAS\nVERSION','1','N',False,True)
rel('OrderRevision','Quote',2050,1375,'HAS\nQUOTE','1','N',False,True)
rel('Quote','QuotePaymentSchedule',2500,1000,'SETS','1','N',False,True)
rel('QuotePaymentSchedule','PaymentRequest',3400,1000,'OPENS','1','1')
rel('PaymentTransaction','PaymentRequest',3850,675,'ALLOCATED\nTO','M','N')
rel('OrderRevision','TransportPlan',2500,1750,'HAS\nPLAN','1','N',False,True)
rel('TransportPlan','TripLeg',3400,1750,'CONTAINS','1','N',False,True)
rel('TripLeg','TripHandover',4250,1750,'RECORDS','1','N',False,True)
rel('FleetVehicle','TripLeg',4250,1375,'USED\nFOR','1','N')
rel('Supplier','FleetVehicle',4650,675,'OWNS','1','N')
rel('StaffProfile','TripLeg',4250,2125,'ASSIGNED\nTO','M','N')
rel('OrderRevision','DocumentRequirement',2050,2125,'REQUIRES','1','N',False,True)
rel('DocumentRequirement','DocumentSubmission',2500,2500,'RECEIVES','1','N',False,True)
rel('DocumentVersion','DocumentSubmission',3400,2500,'USED\nIN','1','N',False,True)
def boundary(a,b):
 dx=b['x']-a['x'];dy=b['y']-a['y'];w=a['w']/2;h=a['h']/2
 if a['kind']=='rect':t=min(w/abs(dx) if dx else 1e10,h/abs(dy) if dy else 1e10)
 elif a['kind']=='diamond':t=1/(abs(dx)/w+abs(dy)/h)
 else:t=1/math.sqrt((dx/w)**2+(dy/h)**2)
 return (a['x']+t*dx,a['y']+t*dy)
def edge(a,b,double=False,attr=False):
 p=boundary(a,b);q=boundary(b,a);dx=q[0]-p[0];dy=q[1]-p[1];L=math.hypot(dx,dy)
 nx=-dy/L;ny=dx/L
 for offset in [-3,3] if double else [0]:
  d.line(((int((p[0]+nx*offset)*S),int((p[1]+ny*offset)*S)),(int((q[0]+nx*offset)*S),int((q[1]+ny*offset)*S))),fill='#A6B2C0' if attr else line,width=(2 if attr else 3)*S)
 return p,q
labels=[]
def cardinal(a,r,value):
 p=boundary(a,r);dx=r['x']-a['x'];dy=r['y']-a['y'];L=math.hypot(dx,dy);ux=dx/L;uy=dy/L
 x=p[0]+ux*60-uy*26;y=p[1]+uy*60+ux*26
 labels.append((x,y,value))
for a,r,b,ca,cb,ma,mb in R:
 edge(a,r,ma);edge(b,r,mb);cardinal(a,r,ca);cardinal(b,r,cb)
for a,b in attrs: edge(a,b,attr=True)
# Render opaque nodes after edges, guaranteeing clean boundaries.
for o in shapes:
 x,y,w,h=o['x'],o['y'],o['w'],o['h'];bbox=((x-w/2)*S,(y-h/2)*S,(x+w/2)*S,(y+h/2)*S)
 if o['kind']=='rect':d.rectangle(bbox,fill=o['color'],outline='#34383F',width=3*S)
 elif o['kind']=='ellipse':d.ellipse(bbox,fill=o['color'],outline='#103B5E',width=2*S)
 else:d.polygon([(x*S,(y-h/2)*S),((x+w/2)*S,y*S),(x*S,(y+h/2)*S),((x-w/2)*S,y*S)],fill=o['color'],outline='#57547C',width=3*S)
 text(x,y,o['label'],29 if o['kind']=='rect' else 23 if o['kind']=='ellipse' else 22,'white',o['kind']=='rect',o.get('key',False))
for x,y,t in labels:
 d.rounded_rectangle(((x-17)*S,(y-18)*S,(x+17)*S,(y+18)*S),radius=4*S,fill='white')
 text(x,y,t,27,ink,True)
text(2550,55,'EQUITRANSIT — CONCEPTUAL ERD',43,'#182E3A',True)
# Structural QA: no node boxes may overlap.
collisions=[]
for i,a in enumerate(shapes):
 for b in shapes[i+1:]:
  if abs(a['x']-b['x'])<(a['w']+b['w'])/2 and abs(a['y']-b['y'])<(a['h']+b['h'])/2:collisions.append([a['name'],b['name']])
assert not collisions,collisions
im.resize((W,H),Image.Resampling.LANCZOS).save(out/'EquiTransit-Conceptual-ERD.png',dpi=(300,300))
(out/'diagram-qa.json').write_text(json.dumps(dict(entities=len(E),relationships=len(R),attributes=len(attrs),overlappingShapes=collisions,scope='dbo core TransportOrder workflow; separate journey consolidated-booking schema excluded',source='EquiTransit_Demo FK and unique-index metadata, 2026-10-27'),indent=2),encoding='utf-8')
print('PNG ready:',W,H,'entities',len(E),'relationships',len(R),'overlaps',len(collisions))
