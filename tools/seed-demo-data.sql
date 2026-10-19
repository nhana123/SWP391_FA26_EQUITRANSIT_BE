-- Synthetic fixtures ONLY. No genuine rates, permits or bank evidence.
-- @passwordHash is generated/verified with the project's BCrypt encoder.
-- Staged business transitions use normal APIs in seed-demo-orders.ps1.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME()<>N'EquiTransit_Demo'
    THROW 51000,'Demo seed refuses any other database.',1;
IF @passwordHash NOT LIKE '{bcrypt}$2%'
    THROW 51000,'A verified BCrypt password hash is required.',1;
BEGIN TRANSACTION;
DECLARE @now datetime2(0)=SYSUTCDATETIME();
DECLARE @from datetime2(0)=DATEADD(day,-1,@now);
DECLARE @until datetime2(0)=DATEADD(day,90,@now);
DECLARE @users TABLE(role varchar(20),email varchar(255),name nvarchar(150));
INSERT @users VALUES
 ('CUSTOMER','customer.demo@equitransit.com',N'DEMO Customer'),
 ('MANAGER','manager.demo@equitransit.com',N'DEMO Manager'),
 ('SPECIALIST','specialist.demo@equitransit.com',N'DEMO Specialist'),
 ('COORDINATOR','coordinator.demo@equitransit.com',N'DEMO Coordinator'),
 ('DRIVER','driver.demo@equitransit.com',N'DEMO Driver');
INSERT dbo.[User](RoleID,Email,PasswordHash,Status,CreatedAt)
SELECT r.RoleID,d.email,@passwordHash,'ACTIVE',@now FROM @users d
JOIN dbo.Role r ON r.RoleCode=d.role
WHERE NOT EXISTS(SELECT 1 FROM dbo.[User] u WHERE u.Email=d.email);
INSERT dbo.Customer(UserID,FullName,Phone,Address,EmailNotificationsEnabled)
SELECT u.UserID,d.name,'+84901234567',N'DEMO Stable, Ho Chi Minh City',0
FROM @users d JOIN dbo.[User] u ON u.Email=d.email
WHERE d.role='CUSTOMER' AND NOT EXISTS(
 SELECT 1 FROM dbo.Customer c WHERE c.UserID=u.UserID);
INSERT dbo.StaffProfile(UserID,FullName,Phone,StaffStatus,EmployeeCode,
 Department,Specialization)
SELECT u.UserID,d.name,'+84901234567','ACTIVE','DEMO-'+d.role,
 N'DEMO Operations',d.role FROM @users d JOIN dbo.[User] u ON u.Email=d.email
WHERE d.role<>'CUSTOMER' AND NOT EXISTS(
 SELECT 1 FROM dbo.StaffProfile s WHERE s.UserID=u.UserID);
DECLARE @manager bigint=(SELECT s.StaffID FROM dbo.StaffProfile s
 JOIN dbo.[User] u ON u.UserID=s.UserID
 WHERE u.Email='manager.demo@equitransit.com');
DECLARE @customer bigint=(SELECT c.CustomerID FROM dbo.Customer c
 JOIN dbo.[User] u ON u.UserID=c.UserID
 WHERE u.Email='customer.demo@equitransit.com');
INSERT dbo.Horse(CustomerID,Name,MicrochipID,PassportNumber,Breed,
 DateOfBirth,SexCode,WeightKg,HeightCm,BodyWidthCm,MeasuredAt,SpecialCareNotes)
SELECT @customer,h.name,h.chip,h.passport,N'Thoroughbred','2019-04-15',
 h.sex,h.weight,h.height,h.width,@now,N'DEMO synthetic horse; not for transport'
FROM (VALUES
 (N'DEMO Standard','985112000000001','DEMO-PASS-01','GELDING',430,155,65),
 (N'DEMO Wide','985112000000002','DEMO-PASS-02','MARE',620,175,85),
 (N'DEMO Stallion','985112000000003','DEMO-PASS-03','STALLION',470,160,70)
) h(name,chip,passport,sex,weight,height,width)
WHERE NOT EXISTS(SELECT 1 FROM dbo.Horse x WHERE x.MicrochipID=h.chip);
INSERT dbo.Supplier(Name,ContactName,Email,Phone,ServicesOfferedJSON,IsActive,
 CreatedAt)
SELECT x.name,N'DEMO Desk','demo@example.invalid','+84901234567',
 x.services,1,@now FROM (VALUES
 (N'DEMO Air Cargo',N'["AIR","AIRFARE"]'),
 (N'DEMO Quarantine Facility',N'["QUARANTINE"]')) x(name,services)
WHERE NOT EXISTS(SELECT 1 FROM dbo.Supplier s WHERE s.Name=x.name);
DECLARE @carrier bigint=(SELECT SupplierID FROM dbo.Supplier
 WHERE Name=N'DEMO Air Cargo');
INSERT dbo.FleetVehicle(VehicleCode,PlateNumber,HorseCapacity,
 ClimateSystemType,OperationalStatus,DocumentExpiresAt,AssetNotes,BufferMinutes)
SELECT 'DEMO-TRUCK-01','DEMO-001',3,'CONTROLLED','ACTIVE',@until,
 N'DEMO synthetic vehicle',30 WHERE NOT EXISTS(
 SELECT 1 FROM dbo.FleetVehicle WHERE VehicleCode='DEMO-TRUCK-01');
INSERT dbo.StallEquipment(StallCode,CapacitySlots,InternalHeightCm,
 InternalWidthCm,EquipmentStatus,OwnerSupplierID,WeightLimitKg,
 DocumentExpiresAt,AssetNotes,BufferMinutes)
SELECT x.code,3,240,240,'ACTIVE',@carrier,2100,@until,
 N'DEMO synthetic equipment',30 FROM (VALUES
 ('DEMO-STALL-SHARED'),('DEMO-STALL-PRIVATE')) x(code)
WHERE NOT EXISTS(SELECT 1 FROM dbo.StallEquipment s WHERE s.StallCode=x.code);
INSERT dbo.StallFitPolicy(PolicyCode,VersionNo,SlotQuantity,MaxWeightKg,
 MaxHeightCm,MaxWidthCm,EffectiveFrom,EffectiveTo,SourceURL,ReviewedBy,
 ReviewedAt,Status)
SELECT x.code,1,x.slots,x.weight,x.height,x.width,@from,@until,
 N'https://example.invalid/DEMO-fit-policy',@manager,@now,'APPROVED'
FROM (VALUES ('DEMO-FIT-1',1.0,500,165,75),
 ('DEMO-FIT-15',1.5,750,190,100),('DEMO-FIT-3',3.0,1000,220,150)
) x(code,slots,weight,height,width) WHERE NOT EXISTS(
 SELECT 1 FROM dbo.StallFitPolicy p WHERE p.PolicyCode=x.code);
DECLARE @prices TABLE(service varchar(50),optionCode varchar(50),
 costGroup varchar(50),unit varchar(50),price decimal(18,2));
INSERT @prices VALUES
 ('ROAD_TRANSPORT','EQUITRANSIT','TRANSPORTATION','KM',2),
 ('AIRFARE','DEFAULT','TRANSPORTATION','SHIPMENT',1500),
 ('AIR_STALL','DEFAULT','HORSE_ACCOMMODATION','SLOT_KM',0.25),
 ('BASIC_CARE','DEFAULT','HORSE_CARE','INCLUDED',0),
 ('HORSE_CARE','ENHANCED','HORSE_CARE','SHIPMENT',80),
 ('GROOMING','EXTRA','HORSE_CARE','PERSON_DAY',25),
 ('BOARDING','EXTRA','HORSE_ACCOMMODATION','HORSE_NIGHT',40),
 ('VETERINARY','EXAM','VETERINARY','HORSE_VISIT',60),
 ('VETERINARY','TEST','VETERINARY','TEST_SAMPLE',30),
 ('VETERINARY','VACCINE','VETERINARY','DOSE',20),
 ('VETERINARY','CERTIFICATE','VETERINARY','CERTIFICATE',45),
 ('QUARANTINE','STANDARD','DOCUMENTATION_QUARANTINE','HORSE_DAY',50),
 ('QUARANTINE','ENHANCED','DOCUMENTATION_QUARANTINE','HORSE_DAY',75),
 ('INSURANCE','MORTALITY','INSURANCE','POLICY',100),
 ('INSURANCE','COMBINED','INSURANCE','POLICY',150),
 ('DOCUMENT_COORDINATION','DEFAULT','DOCUMENTATION_QUARANTINE','SHIPMENT',90),
 ('DOCUMENT_REVIEW','DEFAULT','DOCUMENTATION_QUARANTINE','INCLUDED',0),
 ('AIRPORT_HANDLING','DEFAULT','AIRPORT_CUSTOMS','HORSE_AIRPORT',35),
 ('TERMINAL_HANDLING','DEFAULT','AIRPORT_CUSTOMS','HORSE_AIRPORT',25),
 ('CUSTOMS_DECLARATION','DEFAULT','AIRPORT_CUSTOMS','DECLARATION',40),
 ('GOVERNMENT_DUTY','DEFAULT','AIRPORT_CUSTOMS','ASSESSMENT',100),
 ('WAITING','DEFAULT','ADDITIONAL_CHARGES','HOUR',20),
 ('ESCORT','REQUESTED','HORSE_CARE','PERSON_LEG',45);
INSERT dbo.ServiceTariff(ServiceCode,OptionCode,CostGroup,UnitCode,UnitPrice,
 Currency,VersionNo,EffectiveFrom,EffectiveTo,Status,SourceReference,
 PublishedBy,PublishedAt)
SELECT p.service,p.optionCode,p.costGroup,p.unit,p.price,'USD',1,@from,@until,
 'ACTIVE',N'DEMO fictional retail prices; not a commercial offer',@manager,@now
FROM @prices p WHERE NOT EXISTS(SELECT 1 FROM dbo.ServiceTariff t
 WHERE t.ServiceCode=p.service AND t.OptionCode=p.optionCode);
COMMIT TRANSACTION;
