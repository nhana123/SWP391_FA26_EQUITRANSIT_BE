/*
 * ResourceRepositoryQueryTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend.resource;

import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs actual filter SQL on H2; native locks/triggers are not emulated. */
class ResourceRepositoryQueryTest {
    private JdbcTemplate sql;
    private ResourceRepository repository;
    private final OffsetDateTime now =
            OffsetDateTime.parse("2027-01-01T00:00:00Z");

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:f09query_"
                + UUID.randomUUID()
                + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", "");
        sql = new JdbcTemplate(source);
        repository = new ResourceRepository(source, new ObjectMapper());
        sql.execute("CREATE SCHEMA dbo");
        sql.execute("CREATE TABLE dbo.StallFitPolicy (PolicyID BIGINT,"
                + "PolicyCode VARCHAR(50),VersionNo INT,"
                + "SlotQuantity DECIMAL(5,1),"
                + "MaxWeightKg DECIMAL,MaxHeightCm DECIMAL,MaxWidthCm DECIMAL,"
                + "StallID BIGINT,CarrierSupplierID BIGINT,Status VARCHAR(20),"
                + "EffectiveFrom TIMESTAMP,EffectiveTo TIMESTAMP,"
                + "ReviewedBy BIGINT,ReviewedAt TIMESTAMP,"
                + "SourceURL VARCHAR(1000),SourceFileKey VARCHAR(1000))");
        sql.execute("CREATE TABLE dbo.TransportPlan (PlanID BIGINT,"
                + "OrderID BIGINT,RevisionID BIGINT,PlanStatus VARCHAR(20))");
        sql.execute("CREATE TABLE dbo.TripLeg (LegID BIGINT,PlanID BIGINT,"
                + "VehicleID BIGINT,LegStatus VARCHAR(20),"
                + "TransportMode VARCHAR(20),PlannedDeparture TIMESTAMP,"
                + "PlannedArrival TIMESTAMP)");
        sql.execute("CREATE TABLE dbo.StallAssignment (LegID BIGINT,"
                + "StallID BIGINT,Status VARCHAR(20),AssignedFrom TIMESTAMP,"
                + "AssignedTo TIMESTAMP)");
        sql.execute("CREATE TABLE dbo.TransportOrder (OrderID BIGINT,"
                + "CurrentPlanID BIGINT,CurrentRevisionID BIGINT,"
                + "OrderStatus VARCHAR(20))");
        sql.execute("CREATE TABLE dbo.TripAssignment (LegID BIGINT,"
                + "StaffID BIGINT,RoleInTrip VARCHAR(20),Status VARCHAR(20),"
                + "AssignedTo TIMESTAMP)");
        sql.execute("CREATE TABLE dbo.FleetVehicle (VehicleID BIGINT,"
                + "VehicleCode VARCHAR(50),PlateNumber VARCHAR(50),"
                + "HorseCapacity INT,ClimateSystemType VARCHAR(50),"
                + "OperationalStatus VARCHAR(20))");
    }

    @AfterEach
    void close() {
        sql.execute("SHUTDOWN");
    }

    @Test
    void policyMustBeApprovedReviewedAndCoverJourneyDates() {
        var start = now.plusDays(7);
        sql.update("INSERT INTO dbo.StallFitPolicy VALUES (1,'SYNTHETIC',1,1,"
                + "700,190,90,NULL,NULL,'APPROVED',?,NULL,3,?,"
                + "'https://example.invalid/source',NULL)",
                now.plusDays(2).toLocalDateTime(), now.toLocalDateTime());
        assertThat(repository.policies(11, null, BigDecimal.ONE, now,
                start, start.plusHours(2))).hasSize(1);
        for (String status : java.util.List.of("DRAFT", "INACTIVE")) {
            sql.update("UPDATE dbo.StallFitPolicy SET Status=?", status);
            assertThat(repository.policies(11, null, BigDecimal.ONE, now,
                    start, start.plusHours(2))).isEmpty();
        }
        sql.update("UPDATE dbo.StallFitPolicy SET Status='APPROVED',"
                + "EffectiveTo=?", start.plusHours(2).toLocalDateTime());
        assertThat(repository.policies(11, null, BigDecimal.ONE, now,
                start, start.plusHours(2))).isEmpty();
        sql.update("UPDATE dbo.StallFitPolicy SET EffectiveTo=NULL,"
                + "ReviewedAt=?", now.plusDays(1).toLocalDateTime());
        assertThat(repository.policies(11, null, BigDecimal.ONE, now,
                start, start.plusHours(2))).isEmpty();
    }

    @Test
    void cancelledAirLegReleasesItsStallAndReactivationBlocksAgain() {
        sql.update("INSERT INTO dbo.TransportPlan VALUES(1,10,20,'CONFIRMED')");
        sql.update("INSERT INTO dbo.TripLeg VALUES"
                + "(1,1,NULL,'CANCELLED','AIR',?,?)",
                now.toLocalDateTime(), now.plusHours(2).toLocalDateTime());
        sql.update("INSERT INTO dbo.StallAssignment VALUES"
                + "(1,11,'ASSIGNED',?,?)",
                now.toLocalDateTime(), now.plusHours(2).toLocalDateTime());
        assertThat(repository.scheduleBlockers(ResourceKind.STALL, 11, now,
                now.plusHours(2), 0, 0)).isEmpty();
        sql.update("UPDATE dbo.TripLeg SET LegStatus='PLANNED'");
        assertThat(repository.scheduleBlockers(ResourceKind.STALL, 11, now,
                now.plusHours(2), 0, 0)).contains(ResourceBlocker.OVERLAP);
    }

    @Test
    void reversedLegacyWindowsStayPendingInsteadOfBecomingFree() {
        sql.update("INSERT INTO dbo.TransportPlan VALUES(1,10,20,'CONFIRMED')");
        sql.update("INSERT INTO dbo.TripLeg VALUES"
                + "(1,1,11,'PLANNED','ROAD',?,?)",
                now.toLocalDateTime(), now.minusDays(1).toLocalDateTime());
        assertThat(repository.scheduleBlockers(ResourceKind.VEHICLE, 11,
                now.plusDays(7), now.plusDays(8), 0, 0))
                .contains(ResourceBlocker.SCHEDULE_PENDING);
    }

    @Test
    void persistedAirlineAndModeBindAnExistingLegsPolicyContext() {
        sql.execute("ALTER TABLE dbo.TripLeg ADD SupplierID BIGINT NULL");
        sql.update("INSERT INTO dbo.TripLeg VALUES(1,1,NULL,'PLANNED','AIR',"
                + "NULL,NULL,21)");
        assertThat(repository.legContext(1, "AIR", 21L)).isTrue();
        assertThat(repository.legContext(1, "AIR", 22L)).isFalse();
        assertThat(repository.legContext(1, "AIR", null)).isFalse();
        assertThat(repository.legContext(1, "ROAD", 21L)).isFalse();
    }

    @Test
    void driverReadRequiresOwnCurrentUnexpiredAssignment() {
        sql.update("INSERT INTO dbo.TransportOrder VALUES"
                + "(10,1,20,'CONFIRMED')");
        sql.update("INSERT INTO dbo.TransportPlan VALUES(1,10,20,'CONFIRMED')");
        sql.update("INSERT INTO dbo.FleetVehicle VALUES(11,'V-11','TEST',2,"
                + "'VENTILATED','ACTIVE')");
        sql.update("INSERT INTO dbo.TripLeg VALUES"
                + "(1,1,11,'PLANNED','ROAD',?,?)",
                now.toLocalDateTime(), now.plusHours(2).toLocalDateTime());
        sql.update("INSERT INTO dbo.TripAssignment VALUES"
                + "(1,3,'DRIVER','ASSIGNED',"
                + "NULL)");
        assertThat(repository.assignedVehicle(1, 3, now)).isPresent();
        assertThat(repository.assignedVehicle(1, 4, now)).isEmpty();
        sql.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=21");
        assertThat(repository.assignedVehicle(1, 3, now)).isEmpty();
        sql.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=20");
        sql.update("UPDATE dbo.TripAssignment SET AssignedTo=?",
                now.minusHours(1).toLocalDateTime());
        assertThat(repository.assignedVehicle(1, 3, now)).isEmpty();
    }
}
