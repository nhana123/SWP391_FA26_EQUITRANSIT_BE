SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo','EquiTransit_Test')
    THROW 51022, 'Journey readiness is sandbox only.', 1;
BEGIN TRANSACTION;
IF OBJECT_ID('journey.Trip') IS NULL
    THROW 51022, 'Apply migration 021 first.', 1;
IF OBJECT_ID('journey.DepartureCheck') IS NULL
    CREATE TABLE journey.DepartureCheck (
        TripID bigint PRIMARY KEY REFERENCES journey.Trip(TripID),
        DriverID bigint NOT NULL REFERENCES dbo.StaffProfile(StaffID),
        VehicleSafe bit NOT NULL CHECK (VehicleSafe=1),
        WaterReady bit NOT NULL CHECK (WaterReady=1),
        BeddingReady bit NOT NULL CHECK (BeddingReady=1),
        HorsesReceived bit NOT NULL CHECK (HorsesReceived=1),
        CheckedAt datetime2(0) NOT NULL DEFAULT SYSUTCDATETIME()
    );
IF NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
    WHERE MigrationID='022_journey_departure_check')
    INSERT dbo.SchemaMigrationHistory(MigrationID,AppliedAt,AppliedBy)
        VALUES ('022_journey_departure_check',SYSUTCDATETIME(),SUSER_SNAME());
COMMIT;
