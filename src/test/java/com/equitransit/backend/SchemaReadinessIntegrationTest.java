/*
 * SchemaReadinessIntegrationTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify every runtime schema gate on native SQL Server.
 */
package com.equitransit.backend;

import com.equitransit.backend.repository.ActivityRepository;
import com.equitransit.backend.repository.ComplianceRepository;
import com.equitransit.backend.repository.DepartureRepository;
import com.equitransit.backend.repository.PaymentRepository;
import com.equitransit.backend.repository.PlanRepository;
import com.equitransit.backend.repository.ResourceRepository;
import com.equitransit.backend.repository.StaffAdministrationRepository;
import com.equitransit.backend.repository.TariffRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

/** Native metadata smoke; it performs no business writes or mock readiness. */
@SpringBootTest(properties = {
        "spring.datasource.url=${EQ_SCHEMA_TEST_DB_URL}",
        "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles({"test", "horse-test"})
@EnabledIfEnvironmentVariable(named = "EQ_SCHEMA_TEST_ENABLED",
        matches = "true")
public class SchemaReadinessIntegrationTest {
    @Autowired
    private DataSource source;
    @Autowired
    private ActivityRepository activity;
    @Autowired
    private ComplianceRepository compliance;
    @Autowired
    private DepartureRepository departure;
    @Autowired
    private PaymentRepository payment;
    @Autowired
    private PlanRepository plan;
    @Autowired
    private ResourceRepository resource;
    @Autowired
    private StaffAdministrationRepository staff;
    @Autowired
    private TariffRepository tariff;
    @Autowired
    private com.equitransit.backend.repository.LegExecutionRepository execution;

    /** @throws Exception if a runtime probe or physical seam is incomplete */
    @Test
    public void allRuntimeProbesAndOperationalSeamsAreReady() throws Exception {
        try (var connection = source.getConnection()) {
            assertThat(connection.getCatalog()).startsWith(
                    "EquiTransit_FigmaMigrationTest_");
            var sql = Files.readString(Path.of(
                    "database/verify-f12-f14-schema.sql"));
            try (var command = connection.createStatement()) {
                command.setQueryTimeout(30);
                try (var results = command.executeQuery(sql)) {
                    int tables = 0;
                    while (results.next()) {
                        assertThat(results.getString("Status"))
                                .as(results.getString("TableName"))
                                .isEqualTo("PASS");
                        tables++;
                    }
                    assertThat(tables).isEqualTo(7);
                }
            }
        }
        assertThat(execution.ready()).as("LegExecutionRepository").isTrue();
        assertThat(activity.ready()).as("ActivityRepository").isTrue();
        assertThat(compliance.ready()).as("ComplianceRepository").isTrue();
        assertThat(departure.ready()).as("DepartureRepository").isTrue();
        assertThat(payment.ready()).as("PaymentRepository").isTrue();
        assertThat(plan.ready()).as("PlanRepository").isTrue();
        assertThat(resource.ready()).as("ResourceRepository").isTrue();
        assertThat(staff.ready()).as("StaffAdministrationRepository").isTrue();
        assertThat(tariff.ready()).as("TariffRepository").isTrue();
    }
}
