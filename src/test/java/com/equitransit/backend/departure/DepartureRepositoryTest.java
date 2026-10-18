package com.equitransit.backend.departure;

import com.equitransit.backend.repository.departure.DepartureAuditRepository;
import com.equitransit.backend.repository.departure.DepartureRepository;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real JDBC and transaction rollback on H2, without native lock claims. */
public class DepartureRepositoryTest {

    private JdbcTemplate jdbc;
    private DepartureRepository departures;
    private DepartureAuditRepository audit;
    private TransactionTemplate transaction;
    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-18T00:00:00Z");

    /** Minimal physical fixture for the actual five-update departure method. */
    @BeforeEach
    public void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:depart_"
                + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(
                new DataSourceTransactionManager(source));
        departures = new DepartureRepository(source);
        audit = new DepartureAuditRepository(
                new NamedParameterJdbcTemplate(source));
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE ALIAS SYSUTCDATETIME FOR "
                + "'com.equitransit.backend.departure."
                + "DepartureRepositoryTest.utcNow'");
        jdbc.execute("CREATE TABLE dbo.TransportPlan (PlanID bigint,OrderID "
                + "bigint,PlanStatus varchar(30),FinancialGateStatus "
                + "varchar(30),DocGateStatus varchar(30),OperationalGateStatus "
                + "varchar(30),GatesCheckedAt datetime2)");
        jdbc.execute("CREATE TABLE dbo.TripLeg (LegID bigint,PlanID bigint,"
                + "LegStatus varchar(30),ActualDeparture datetime2)");
        jdbc.execute("CREATE TABLE dbo.TripAssignment (LegID bigint,"
                + "StaffID bigint,RoleInTrip varchar(20),Status varchar(20),"
                + "AssignedFrom datetime2,AssignedTo datetime2)");
        jdbc.execute("CREATE TABLE dbo.TransportOrder (OrderID bigint,"
                + "OrderStatus varchar(30),UpdatedAt datetime2)");
        jdbc.execute("CREATE TABLE dbo.StaffProfile (StaffID bigint,"
                + "UserID bigint)");
        jdbc.execute("CREATE TABLE dbo.[User] (UserID bigint,RoleID bigint,"
                + "Status varchar(20))");
        jdbc.execute("CREATE TABLE dbo.[Role] (RoleID bigint,"
                + "RoleCode varchar(20))");
        jdbc.update("INSERT INTO dbo.TransportPlan VALUES "
                + "(311,101,'CONFIRMED','PENDING','PENDING','PENDING',NULL)");
        jdbc.update("INSERT INTO dbo.TripLeg VALUES "
                + "(401,311,'CONFIRMED',NULL)");
        jdbc.update("INSERT INTO dbo.TripAssignment VALUES "
                + "(401,31,'DRIVER','CONFIRMED',"
                + "'2026-10-17','2026-10-19')");
        jdbc.update("INSERT INTO dbo.TransportOrder VALUES "
                + "(101,'CONFIRMED',NULL)");
        jdbc.update("INSERT INTO dbo.StaffProfile VALUES (31,41)");
        jdbc.update("INSERT INTO dbo.[User] VALUES (41,1,'ACTIVE')");
        jdbc.update("INSERT INTO dbo.[Role] VALUES (1,'DRIVER')");
    }

    /** @return deterministic H2 substitute for the SQL Server UTC function */
    public static Timestamp utcNow() {
        return Timestamp.valueOf("2026-10-18 00:00:00");
    }

    /** All persisted states transition together under one transaction. */
    @Test
    public void departureUpdatesAllOperationalStates() {
        transaction.executeWithoutResult(status ->
                departures.departFirstLeg(101, 311, 401, now));
        assertThat(value("TransportOrder", "OrderStatus"))
                .isEqualTo("IN_TRANSIT");
        assertThat(value("TransportPlan", "PlanStatus"))
                .isEqualTo("IN_TRANSIT");
        assertThat(value("TripLeg", "LegStatus")).isEqualTo("IN_TRANSIT");
        assertThat(value("TripAssignment", "Status")).isEqualTo("ACTIVE");
    }

    /** Failure of the last update rolls back earlier writes. */
    @Test
    public void lastUpdateFailureRollsBackTheWholeDeparture() {
        jdbc.execute("ALTER TABLE dbo.TransportOrder ADD CONSTRAINT "
                + "CK_Test_Order CHECK(OrderStatus='CONFIRMED')");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                departures.departFirstLeg(101, 311, 401, now)))
                .isInstanceOf(DataAccessException.class);
        assertRolledBack();
    }

    /** Audit failure cannot leave a departed trip without its audit fact. */
    @Test
    public void auditFailureRollsBackOperationalWrites() {
        jdbc.execute("CREATE TABLE dbo.AuditEvent (UserID bigint,"
                + "EntityType varchar(30),EntityID bigint,"
                + "ActionCode varchar(80)"
                + " CHECK(ActionCode='NEVER'),ChangeSnapshot varchar(2000),"
                + "OccurredAt datetime2)");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            departures.departFirstLeg(101, 311, 401, now);
            audit.append(101, 41, 311, "DEPART_FIRST_LEG",
                    UUID.randomUUID(), now);
        })).isInstanceOf(DataAccessException.class);
        assertRolledBack();
    }

    /** Pickup uses a half-open assignment interval and active role. */
    @Test
    public void actualDriverAssignmentMustStillBeActive() {
        assertThat(departures.driverAssignedAt(401, 31, now)).isTrue();
        assertThat(departures.driverAssignedAt(401, 0, now)).isTrue();
        assertThat(departures.driverAssignedAt(401, 32, now)).isFalse();
        assertThat(departures.driverAssignedAt(401, 31, now.plusDays(1)))
                .isFalse();
        jdbc.update("UPDATE dbo.[User] SET Status='INACTIVE'");
        assertThat(departures.driverAssignedAt(401, 0, now)).isFalse();
    }

    private String value(String table, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM dbo." + table,
                String.class);
    }

    private void assertRolledBack() {
        assertThat(value("TransportOrder", "OrderStatus"))
                .isEqualTo("CONFIRMED");
        assertThat(value("TransportPlan", "PlanStatus"))
                .isEqualTo("CONFIRMED");
        assertThat(value("TripLeg", "LegStatus")).isEqualTo("CONFIRMED");
        assertThat(value("TripAssignment", "Status")).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT ActualDeparture FROM dbo.TripLeg",
                Timestamp.class)).isNull();
    }
}
