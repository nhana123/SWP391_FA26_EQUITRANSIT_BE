-- Additional synthetic H2 view contracts, not production migrations.
CREATE TABLE dbo.OrderRevision (RevisionID BIGINT PRIMARY KEY,OrderID BIGINT);
CREATE TABLE dbo.RevisionRouteSource (
    RevisionID BIGINT PRIMARY KEY,OrderID BIGINT,
    OriginCountryCode VARCHAR(2),DestinationCountryCode VARCHAR(2),
    HorseID BIGINT,PreferredDepartureAt TIMESTAMP,DeliveryDeadline TIMESTAMP
);
CREATE TABLE dbo.TransitAirport (RevisionID BIGINT,AirportCode VARCHAR(3));
CREATE TABLE dbo.Airport (AirportCode VARCHAR(3) PRIMARY KEY,
    CountryCode VARCHAR(2),SourceReference VARCHAR(1000),ReviewedAt TIMESTAMP);
CREATE TABLE dbo.RuleRequirementPolicy (
    RuleID BIGINT PRIMARY KEY,ScopeCode VARCHAR(20),IsMandatory BIT,
    ValidityBasis VARCHAR(20),Instructions VARCHAR(2000),
    ReviewedBy BIGINT,ReviewedAt TIMESTAMP
);
CREATE TABLE dbo.RevisionChecklistState (
    RevisionID BIGINT PRIMARY KEY,AssessedAt TIMESTAMP,PendingRuleAssessment BIT
);
