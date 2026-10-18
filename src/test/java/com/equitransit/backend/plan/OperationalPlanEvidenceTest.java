package com.equitransit.backend.plan;

import com.equitransit.backend.dto.plan.PlanModels.PlanCommand;
import com.equitransit.backend.dto.plan.PlanModels.PlanInsurancePolicyRequest;
import com.equitransit.backend.dto.plan.PlanModels.ReservationState;
import com.equitransit.backend.repository.plan.PlanEvidenceRepository;
import com.equitransit.backend.repository.plan.PlanRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real JDBC checks on disposable H2; not SQL Server lock verification. */
public class OperationalPlanEvidenceTest {

    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-18T00:00:00Z");
    private JdbcTemplate jdbc;
    private PlanEvidenceRepository evidence;
    private PlanRepository plans;
    private TransactionTemplate transactions;

    /** Build a private in-memory schema containing the actual used columns. */
    @BeforeEach
    public void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:plan_"
                + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
                "sa", "");

        jdbc = new JdbcTemplate(source);
        evidence = new PlanEvidenceRepository(
                new NamedParameterJdbcTemplate(source));
        plans = new PlanRepository(source);
        transactions = new TransactionTemplate(
                new DataSourceTransactionManager(source));
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.TransportOrder (OrderID bigint "
                + "PRIMARY KEY, CurrentRevisionID bigint)");
        jdbc.execute("CREATE TABLE dbo.OrderRevision (RevisionID bigint "
                + "PRIMARY KEY, OrderID bigint)");
        jdbc.execute("CREATE TABLE dbo.Quote (QuoteID bigint PRIMARY KEY, "
                + "RevisionID bigint, QuoteStatus varchar(20), "
                + "AcceptedAt datetime2, TotalQuotedAmount decimal(18,2))");
        jdbc.execute("CREATE TABLE dbo.AuditEvent (UserID bigint, "
                + "EntityType varchar(50), EntityID bigint, "
                + "ActionCode varchar(50), ChangeSnapshot nvarchar(max), "
                + "OccurredAt datetime2)");
        jdbc.execute("CREATE TABLE dbo.InsurancePolicy (PolicyID bigint "
                + "IDENTITY PRIMARY KEY, PlanID bigint, "
                + "InsuranceChoiceID bigint, InsurerName nvarchar(300), "
                + "PolicyNumber varchar(150), EvidenceFileKey nvarchar(1024),"
                + " CoverageFrom datetime2, CoverageTo datetime2, "
                + "Status varchar(50), VerifiedBy bigint, VerifiedAt "
                + "datetime2, PolicyTerms nvarchar(max))");
        jdbc.update("INSERT INTO dbo.TransportOrder VALUES "
                + "(101,201),(102,202)");
        jdbc.update("INSERT INTO dbo.OrderRevision VALUES (201,101),(202,102),"
                + "(203,101)");
        jdbc.update("INSERT INTO dbo.Quote VALUES "
                + "(501,201,'ACCEPTED','2026-10-17',1000),"
                + "(502,202,'ACCEPTED','2026-10-17',1000),"
                + "(503,203,'ACCEPTED','2026-10-17',1000)");
    }

    /** A real accepted row on the exact current revision is consumed. */
    @Test
    public void acceptedQuoteMatchesCurrentOrderAndRevision() {
        evidence.requireAccepted(101, 201, 501, now);
    }

    /** Another order's accepted quote never authorizes this plan. */
    @Test
    public void otherOrdersQuoteIsRejected() {
        assertThatThrownBy(() -> evidence.requireAccepted(101, 201, 502,
                now)).hasMessageContaining("accepted quote");
    }

    /** Even the same owner's superseded revision is insufficient. */
    @Test
    public void oldRevisionIsRejected() {
        assertThatThrownBy(() -> evidence.requireAccepted(101, 203, 503,
                now)).hasMessageContaining("accepted quote");
    }

    /** Status alone, with no acceptance timestamp, is insufficient proof. */
    @Test
    public void acceptanceTimestampCannotBeMissingOrFuture() {
        jdbc.update("UPDATE dbo.Quote SET AcceptedAt=NULL WHERE QuoteID=501");
        assertThatThrownBy(() -> evidence.requireAccepted(101, 201, 501,
                now)).hasMessageContaining("accepted quote");
        jdbc.update("UPDATE dbo.Quote SET AcceptedAt='2027-01-01' "
                + "WHERE QuoteID=501");
        assertThatThrownBy(() -> evidence.requireAccepted(101, 201, 501,
                now)).hasMessageContaining("accepted quote");
    }

    /** A rejected quote cannot be made acceptable by a historic timestamp. */
    @Test
    public void statusAndPriceAreVerified() {
        jdbc.update("UPDATE dbo.Quote SET QuoteStatus='REJECTED' "
                + "WHERE QuoteID=501");
        assertThatThrownBy(() -> evidence.requireAccepted(101, 201, 501,
                now)).hasMessageContaining("accepted quote");
        jdbc.update("UPDATE dbo.Quote SET QuoteStatus='ACCEPTED',"
                + "TotalQuotedAmount=NULL WHERE QuoteID=501");
        assertThatThrownBy(() -> evidence.requireAccepted(101, 201, 501,
                now)).hasMessageContaining("accepted quote");
    }

    /** Failure rolls back the audit fact in the caller's transaction. */
    @Test
    public void auditIsAtomicAndContainsNoFreeText() {
        assertThatThrownBy(() -> transactions.execute(status -> {
            evidence.append(101, 41, 311, "CONFIRM_PLAN",
                    UUID.randomUUID(), now);
            throw new IllegalStateException("later write failed");
        })).hasMessageContaining("later write failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditEvent",
                Integer.class)).isZero();
        evidence.append(101, 41, 311, "CONFIRM_PLAN", UUID.randomUUID(), now);
        assertThat(jdbc.queryForObject("SELECT ChangeSnapshot FROM "
                + "dbo.AuditEvent", String.class))
                .contains("\"planId\":311", "operationId")
                .doesNotContain("price", "contact", "notes");
    }

    /** Editing one draft does not cancel another version's policy. */
    @Test
    public void insuranceEvidenceIsScopedToItsPlan() {
        var req = new PlanInsurancePolicyRequest(new PlanCommand(
                UUID.randomUUID(), "0000000000000001"), 201, "Insurer",
                "POL-1", "signed.pdf", now, now.plusDays(40),
                ReservationState.CONFIRMED, "terms");

        plans.verifyInsurance(311, 601, req, 31, now);
        plans.verifyInsurance(312, 601, req, 31, now);
        plans.verifyInsurance(311, 601, req, 31, now);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.InsurancePolicy WHERE PlanID=312 AND "
                + "Status='CONFIRMED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.InsurancePolicy WHERE PlanID=311 AND "
                + "Status='CONFIRMED'", Integer.class)).isEqualTo(1);
    }
}
