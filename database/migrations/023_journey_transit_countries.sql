SET XACT_ABORT ON;
SET NOCOUNT ON;
IF DB_NAME() NOT IN ('EquiTransit_Demo','EquiTransit_Test')
    THROW 51023, 'Journey transit is sandbox only.', 1;
BEGIN TRANSACTION;
IF OBJECT_ID('journey.DepartureCheck') IS NULL
    THROW 51023, 'Apply migrations 021-022 first.', 1;
IF COL_LENGTH('journey.Booking','TransitCountriesJSON') IS NULL
    ALTER TABLE journey.Booking ADD TransitCountriesJSON nvarchar(max)
        NOT NULL CONSTRAINT DF_JourneyBooking_Transit DEFAULT '[]'
        CONSTRAINT CK_JourneyBooking_Transit
            CHECK (ISJSON(TransitCountriesJSON)=1);
IF COL_LENGTH('journey.Trip','TransitCountriesJSON') IS NULL
    ALTER TABLE journey.Trip ADD TransitCountriesJSON nvarchar(max)
        NOT NULL CONSTRAINT DF_JourneyTrip_Transit DEFAULT '[]'
        CONSTRAINT CK_JourneyTrip_Transit
            CHECK (ISJSON(TransitCountriesJSON)=1);
IF COL_LENGTH('journey.Booking','RouteReviewed') IS NULL
    ALTER TABLE journey.Booking ADD RouteReviewed bit NOT NULL
        CONSTRAINT DF_JourneyBooking_RouteReviewed DEFAULT 0;
IF NOT EXISTS (SELECT 1 FROM dbo.SchemaMigrationHistory
    WHERE MigrationID='023_journey_transit_countries')
    INSERT dbo.SchemaMigrationHistory(MigrationID,AppliedAt,AppliedBy)
        VALUES ('023_journey_transit_countries',SYSUTCDATETIME(),SUSER_SNAME());
COMMIT;
