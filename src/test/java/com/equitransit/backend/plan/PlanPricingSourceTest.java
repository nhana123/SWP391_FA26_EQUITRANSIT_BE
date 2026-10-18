package com.equitransit.backend.plan;

import com.equitransit.backend.repository.plan.PlanRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercise the candidate SQL on H2, without claiming native lock coverage. */
public class PlanPricingSourceTest {

    private JdbcTemplate jdbc;
    private PlanRepository plans;

    /** Minimal physical schema for the actual summary mapper. */
    @BeforeEach
    public void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:pricing_"
                + UUID.randomUUID() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(source);
        plans = new PlanRepository(source);
        jdbc.execute("CREATE SCHEMA dbo");
        jdbc.execute("CREATE TABLE dbo.TransportOrder (OrderID bigint,"
                + "CurrentPlanID bigint)");
        jdbc.execute("CREATE TABLE dbo.TransportPlan (PlanID bigint,"
                + "OrderID bigint,RevisionID bigint,VersionNo int,"
                + "PlanStatus varchar(20),AcceptedQuoteID bigint,"
                + "CreatedBy bigint,PlannedStartTime datetime2,"
                + "PlannedEndTime datetime2,FinancialGateStatus varchar(20),"
                + "OperationalGateStatus varchar(20),"
                + "DocGateStatus varchar(20))");
        jdbc.update("INSERT INTO dbo.TransportOrder VALUES (101,311)");
        insert(311, 101, 201, "CONFIRMED");
        insert(312, 101, 202, "DRAFT");
    }

    /** Ignore the older operating revision and other orders' drafts. */
    @Test
    public void queryScopesOrderRevisionAndEligibleState() {
        insert(313, 999, 202, "DRAFT");
        insert(314, 101, 201, "DRAFT");
        insert(315, 101, 202, "SUPERSEDED");
        insert(316, 101, 202, "CANCELLED");
        insert(317, 101, 202, "REJECTED");
        assertThat(plans.pricingDrafts(101, 202)).hasSize(1)
                .allSatisfy(p -> {
                    assertThat(p.planId()).isEqualTo(312);
                    assertThat(p.currentPlan()).isFalse();
                });
        assertThat(plans.currentPlanId(101)).isEqualTo(311);
    }

    /** Two rows suffice to signal ambiguity even with more candidates. */
    @Test
    public void queryRetainsAmbiguityWithoutLoadingAllVersions() {
        insert(313, 101, 202, "PROPOSED");
        insert(314, 101, 202, "PLANNED");
        assertThat(plans.pricingDrafts(101, 202)).hasSize(2)
                .extracting(p -> p.planId()).containsExactly(314L, 313L);
    }

    /** A current marker remains metadata and is not changed by this query. */
    @Test
    public void currentMarkerAndMissingRevisionArePreserved() {
        jdbc.update("UPDATE dbo.TransportOrder SET CurrentPlanID=312");
        assertThat(plans.pricingDrafts(101, 202).getFirst().currentPlan())
                .isTrue();
        assertThat(plans.pricingDrafts(101, 203)).isEmpty();
    }

    private void insert(long id, long order, long revision, String state) {
        jdbc.update("INSERT INTO dbo.TransportPlan VALUES "
                + "(?,?,?,?,?,NULL,31,'2026-11-17','2026-11-18',"
                + "'PENDING','PENDING','PENDING')",
                id, order, revision, (int) id, state);
    }
}
