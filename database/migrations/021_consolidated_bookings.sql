-- Additive reference flow. Never run against the production catalog.
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo', 'EquiTransit_Test')
    THROW 51021, 'Consolidated flow is sandbox only.', 1;
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock = sys.sp_getapplock
    @Resource='EquiTransitSchemaMigration', @LockMode='Exclusive',
    @LockOwner='Transaction', @LockTimeout=10000;
IF @lock < 0 THROW 51021, 'Schema migration is locked.', 1;
IF SCHEMA_ID('journey') IS NULL EXEC('CREATE SCHEMA journey');
IF OBJECT_ID('journey.Booking') IS NULL
BEGIN
    CREATE TABLE journey.Booking (
        BookingID bigint IDENTITY PRIMARY KEY,
        CustomerID bigint NOT NULL REFERENCES dbo.Customer(CustomerID),
        OriginCountry char(2) NOT NULL,
        DestinationCountry char(2) NOT NULL,
        OriginAddress nvarchar(500) NOT NULL,
        DestinationAddress nvarchar(500) NOT NULL,
        PickupFrom datetime2(0) NOT NULL,
        PickupTo datetime2(0) NOT NULL,
        DeliveryBy datetime2(0) NOT NULL,
        DistanceKm decimal(12,2) NOT NULL CHECK (DistanceKm > 0),
        TransportMode varchar(8) NOT NULL
            CHECK (TransportMode IN ('ROAD','AIR')),
        Currency char(3) NOT NULL CHECK (Currency='USD'),
        TotalAmount decimal(18,2) NOT NULL CHECK (TotalAmount > 0),
        DepositAmount decimal(18,2) NOT NULL,
        PriceSnapshot nvarchar(max) NOT NULL CHECK (ISJSON(PriceSnapshot)=1),
        Status varchar(30) NOT NULL DEFAULT 'SUBMITTED' CHECK (Status IN
            ('SUBMITTED','AWAITING_DEPOSIT','APPROVED',
             'AWAITING_FINAL_PAYMENT','COMPLETED','REJECTED','CANCELLED')),
        SpecialistID bigint NULL REFERENCES dbo.StaffProfile(StaffID),
        CoordinatorID bigint NULL REFERENCES dbo.StaffProfile(StaffID),
        CreatedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        Version bigint NOT NULL DEFAULT 1,
        CHECK (PickupTo>=PickupFrom AND DeliveryBy>PickupTo),
        CHECK (DepositAmount>0 AND DepositAmount<=TotalAmount)
    );
    CREATE INDEX IX_Booking_Owner ON journey.Booking(CustomerID,BookingID);
    CREATE TABLE journey.BookingHorse (
        BookingHorseID bigint IDENTITY PRIMARY KEY,
        BookingID bigint NOT NULL REFERENCES journey.Booking(BookingID),
        HorseID bigint NOT NULL REFERENCES dbo.Horse(HorseID),
        HorseSnapshot nvarchar(max) NOT NULL
            CHECK (ISJSON(HorseSnapshot)=1),
        Status varchar(16) NOT NULL DEFAULT 'PLANNED'
            CHECK (Status IN ('PLANNED','IN_TRANSIT','DELIVERED')),
        UNIQUE (BookingID,HorseID)
    );
    CREATE TABLE journey.Trip (
        TripID bigint IDENTITY PRIMARY KEY,
        VehicleID bigint NULL REFERENCES dbo.FleetVehicle(VehicleID),
        SupplierID bigint NULL REFERENCES dbo.Supplier(SupplierID),
        Capacity int NOT NULL CHECK (Capacity BETWEEN 1 AND 100),
        TransportMode varchar(8) NOT NULL
            CHECK (TransportMode IN ('ROAD','AIR')),
        DriverID bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CoordinatorID bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        OriginCountry char(2) NOT NULL,
        DestinationCountry char(2) NOT NULL,
        StartsAt datetime2(0) NOT NULL,
        EndsAt datetime2(0) NOT NULL,
        ActualDeparture datetime2(0) NULL,
        Status varchar(24) NOT NULL DEFAULT 'DRAFT' CHECK (Status IN
            ('DRAFT','PENDING_APPROVAL','SCHEDULED','IN_TRANSIT',
             'ARRIVED','COMPLETED','CANCELLED')),
        Version bigint NOT NULL DEFAULT 1,
        CHECK (EndsAt>StartsAt),
        CHECK ((TransportMode='ROAD' AND VehicleID IS NOT NULL)
            OR (TransportMode='AIR' AND SupplierID IS NOT NULL))
    );
    CREATE INDEX IX_Trip_Resource ON journey.Trip(VehicleID,StartsAt,EndsAt);
    CREATE TABLE journey.Manifest (
        ManifestID bigint IDENTITY PRIMARY KEY,
        TripID bigint NOT NULL REFERENCES journey.Trip(TripID),
        BookingHorseID bigint NOT NULL
            REFERENCES journey.BookingHorse(BookingHorseID),
        SlotNo int NOT NULL CHECK (SlotNo>0),
        Active bit NOT NULL DEFAULT 1,
        DeliveredAt datetime2(0) NULL,
        ReceiverName nvarchar(150) NULL,
        EvidenceKey uniqueidentifier NULL,
        UNIQUE (TripID,BookingHorseID),
        UNIQUE (TripID,SlotNo)
    );
    CREATE UNIQUE INDEX UX_Manifest_ActiveHorse
        ON journey.Manifest(BookingHorseID) WHERE Active=1;
    CREATE TABLE journey.Requirement (
        RequirementID bigint IDENTITY PRIMARY KEY,
        BookingHorseID bigint NOT NULL
            REFERENCES journey.BookingHorse(BookingHorseID),
        Phase varchar(24) NOT NULL CHECK (Phase IN
            ('PRE_EXPORT','ARRIVAL','QUARANTINE_RELEASE')),
        TypeCode varchar(64) NOT NULL,
        Status varchar(16) NOT NULL DEFAULT 'REQUIRED' CHECK (Status IN
            ('REQUIRED','SUBMITTED','APPROVED','REJECTED')),
        Version bigint NOT NULL DEFAULT 1,
        UNIQUE (BookingHorseID,Phase,TypeCode)
    );
    CREATE TABLE journey.FileObject (
        FileKey uniqueidentifier PRIMARY KEY,
        BookingID bigint NOT NULL REFERENCES journey.Booking(BookingID),
        UploadedBy bigint NOT NULL REFERENCES dbo.[User](UserID),
        FileName nvarchar(255) NOT NULL,
        ContentType varchar(100) NOT NULL,
        ContentBytes varbinary(max) NOT NULL
            CHECK (DATALENGTH(ContentBytes) BETWEEN 1 AND 5242880),
        CreatedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
    ALTER TABLE journey.Manifest ADD CONSTRAINT FK_Manifest_Evidence
        FOREIGN KEY (EvidenceKey) REFERENCES journey.FileObject(FileKey);
    CREATE TABLE journey.Submission (
        SubmissionID bigint IDENTITY PRIMARY KEY,
        RequirementID bigint NOT NULL
            REFERENCES journey.Requirement(RequirementID),
        FileKey uniqueidentifier NOT NULL
            REFERENCES journey.FileObject(FileKey),
        ExpiresAt datetime2(0) NOT NULL,
        Decision varchar(16) NOT NULL DEFAULT 'SUBMITTED' CHECK (Decision IN
            ('SUBMITTED','APPROVED','REJECTED')),
        ReviewNotes nvarchar(2000) NULL,
        ReviewedBy bigint NULL REFERENCES dbo.StaffProfile(StaffID),
        SubmittedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE TABLE journey.Clearance (
        BookingHorseID bigint NOT NULL
            REFERENCES journey.BookingHorse(BookingHorseID),
        Phase varchar(24) NOT NULL CHECK (Phase IN
            ('PRE_EXPORT','ARRIVAL','QUARANTINE_RELEASE')),
        SpecialistID bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        ClearedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        ValidUntil datetime2(0) NOT NULL,
        PRIMARY KEY (BookingHorseID,Phase)
    );
    CREATE TABLE journey.Payment (
        PaymentID bigint IDENTITY PRIMARY KEY,
        BookingID bigint NOT NULL REFERENCES journey.Booking(BookingID),
        Amount decimal(18,2) NOT NULL CHECK (Amount>0),
        BankReference nvarchar(100) NOT NULL UNIQUE,
        ReconciledBy bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        ReceivedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX IX_Payment_Booking ON journey.Payment(BookingID);
    CREATE TABLE journey.ExtraCharge (
        ChargeID bigint IDENTITY PRIMARY KEY,
        TripID bigint NOT NULL REFERENCES journey.Trip(TripID),
        BookingID bigint NOT NULL REFERENCES journey.Booking(BookingID),
        Amount decimal(18,2) NOT NULL CHECK (Amount>0),
        Reason nvarchar(1000) NOT NULL,
        ApprovedBy bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        CreatedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE TABLE journey.Operation (
        UserID bigint NOT NULL REFERENCES dbo.[User](UserID),
        OperationID uniqueidentifier NOT NULL,
        ActionCode varchar(64) NOT NULL,
        RequestHash varchar(64) NOT NULL,
        Receipt nvarchar(max) NOT NULL CHECK (ISJSON(Receipt)=1),
        CreatedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        PRIMARY KEY (UserID,OperationID)
    );
END;
IF NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
    WHERE MigrationID='021_consolidated_bookings')
    INSERT dbo.SchemaMigrationHistory(MigrationID,AppliedAt,AppliedBy)
        VALUES ('021_consolidated_bookings',SYSUTCDATETIME(),SUSER_SNAME());
COMMIT;
