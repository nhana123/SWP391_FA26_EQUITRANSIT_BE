package com.equitransit.backend.plan;

import com.equitransit.backend.repository.plan.PlanRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Actual repository queries on isolated H2; no native lock claims. */
public class OperationalPlanSourceTest {

    private JdbcTemplate jdbc;
    private PlanRepository plans;

    /** Create only the physical columns read by the provenance boundary. */
    @BeforeEach
    public void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:source_"
                + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(source);
        plans = new PlanRepository(source);
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.TransportPlan (PlanID bigint "
                + "PRIMARY KEY, RevisionID bigint)");
        jdbc.execute("CREATE TABLE dbo.TripLeg (LegID bigint PRIMARY KEY, "
                + "PlanID bigint,TransportMode varchar(10),SupplierID bigint)");
        jdbc.execute("CREATE TABLE dbo.FlightReservation (ReservationID "
                + "bigint PRIMARY KEY,LegID bigint,OfferID bigint,Status "
                + "varchar(20),ConfirmedAt datetime2)");
        jdbc.execute("CREATE TABLE dbo.SupplierOffer (OfferID bigint "
                + "PRIMARY KEY,RevisionID bigint,SupplierID bigint,ServiceCode"
                + " varchar(20),Status varchar(20),RecordedBy bigint,"
                + "RecordedAt datetime2,ValidUntil datetime2,"
                + "EvidenceFileKey nvarchar(1024))");
        jdbc.execute("CREATE TABLE dbo.Supplier (SupplierID bigint "
                + "PRIMARY KEY,IsActive bit)");
        jdbc.update("INSERT INTO dbo.TransportPlan VALUES (311,201),(312,202)");
        jdbc.update("INSERT INTO dbo.TripLeg VALUES (402,311,'AIR',21)");
        jdbc.update("INSERT INTO dbo.Supplier VALUES (21,1)");
        jdbc.update("INSERT INTO dbo.SupplierOffer VALUES "
                + "(601,201,21,'AIRFARE',"
                + "'SELECTED',31,'2026-10-16','2026-10-20','offer.pdf')");
        jdbc.update("INSERT INTO dbo.FlightReservation VALUES "
                + "(901,402,601,'CONFIRMED','2026-10-17')");
        jdbc.execute("CREATE TABLE dbo.OrderAssignment (AssignmentID bigint,"
                + "OrderID bigint,StaffID bigint,AssignmentRole varchar(30),"
                + "EndedAt datetime2)");
        jdbc.execute("CREATE TABLE dbo.StaffProfile (StaffID bigint,"
                + "UserID bigint,FullName nvarchar(150),Phone varchar(30))");
        jdbc.execute("CREATE TABLE dbo.[User] (UserID bigint,RoleID bigint,"
                + "Status varchar(20))");
        jdbc.execute("CREATE TABLE dbo.[Role] (RoleID bigint,"
                + "RoleCode varchar(30))");
        jdbc.update("INSERT INTO dbo.OrderAssignment VALUES "
                + "(1,101,31,'COORDINATOR',NULL)");
        jdbc.update("INSERT INTO dbo.StaffProfile VALUES "
                + "(31,41,'Coordinator','+84901234567')");
        jdbc.update("INSERT INTO dbo.[User] VALUES (41,1,'ACTIVE')");
        jdbc.update("INSERT INTO dbo.[Role] VALUES (1,'COORDINATOR')");
    }

    /** Exact plan, revision, carrier and booking match the actual source. */
    @Test
    public void actualBookingSourceIsAccepted() {
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isTrue();
        assertThat(plans.flightSourceValid(312, 201, 402, 901)).isFalse();
        assertThat(plans.flightSourceValid(311, 202, 402, 901)).isFalse();
        assertThat(plans.flightSourceValid(311, 201, 403, 901)).isFalse();
    }

    /** Booking labels cannot launder another revision's supplier offer. */
    @Test
    public void foreignRevisionOrCarrierIsRejected() {
        jdbc.update("UPDATE dbo.SupplierOffer SET RevisionID=202");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isFalse();
        jdbc.update("UPDATE dbo.SupplierOffer SET "
                + "RevisionID=201,SupplierID=22");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isFalse();
    }

    /** An expired price offer does not cancel an already purchased ticket. */
    @Test
    public void laterOfferExpiryDoesNotInvalidateAValidBooking() {
        jdbc.update("UPDATE dbo.SupplierOffer SET Status='EXPIRED',"
                + "ValidUntil='2026-10-18'");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isTrue();
    }

    /** The source must have been valid at the actual confirmation time. */
    @Test
    public void alreadyExpiredOrFutureRecordedOfferIsRejected() {
        jdbc.update("UPDATE dbo.SupplierOffer SET ValidUntil='2026-10-16'");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isFalse();
        jdbc.update("UPDATE dbo.SupplierOffer SET ValidUntil='2026-10-20',"
                + "RecordedAt='2026-10-18'");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isFalse();
    }

    /** Revoked source evidence is not substituted by a booking's file key. */
    @Test
    public void missingEvidenceAndRejectedSourceAreRejected() {
        jdbc.update("UPDATE dbo.SupplierOffer SET EvidenceFileKey=' '");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isFalse();
        jdbc.update("UPDATE dbo.SupplierOffer SET EvidenceFileKey='offer.pdf',"
                + "Status='REJECTED'");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isFalse();
    }

    /** A carrier withdrawal closes the live operational boundary. */
    @Test
    public void inactiveCarrierIsRejected() {
        jdbc.update("UPDATE dbo.Supplier SET IsActive=0");
        assertThat(plans.flightSourceValid(311, 201, 402, 901)).isFalse();
    }

    /** Infrastructure failure never turns into a successful gate result. */
    @Test
    public void databaseFailurePropagates() {
        jdbc.execute("DROP TABLE dbo.SupplierOffer");
        assertThatThrownBy(() -> plans.flightSourceValid(311, 201, 402, 901))
                .isInstanceOf(DataAccessException.class);
    }

    /** Only the active Coordinator assigned to this order is published. */
    @Test
    public void emergencyContactIsOrderScoped() {
        assertThat(plans.coordinatorContact(101)).hasValueSatisfying(contact ->
                assertThat(contact.phone()).isEqualTo("+84901234567"));
        assertThat(plans.coordinatorContact(102)).isEmpty();
    }

    /** Ended or deactivated case contacts cannot be used by drivers. */
    @Test
    public void endedOrInactiveCoordinatorIsNotPublished() {
        jdbc.update("UPDATE dbo.OrderAssignment SET EndedAt='2026-10-18'");
        assertThat(plans.coordinatorContact(101)).isEmpty();
        jdbc.update("UPDATE dbo.OrderAssignment SET EndedAt=NULL");
        jdbc.update("UPDATE dbo.[User] SET Status='INACTIVE'");
        assertThat(plans.coordinatorContact(101)).isEmpty();
    }
}
