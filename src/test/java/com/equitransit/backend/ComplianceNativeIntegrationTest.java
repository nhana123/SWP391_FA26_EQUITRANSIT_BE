package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.equitransit.backend.dto.request.QuarantineAssessmentRequest;
import com.equitransit.backend.dto.request.ComplianceClearanceRequest;
import com.equitransit.backend.dto.request.DocumentSubmissionRequest;
import com.equitransit.backend.dto.request.DocumentReviewRequest;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.service.CaseAssignmentAccess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs inherited F08 workflows against actual migrations on SQL Server.
 * Each test owns a newly created name-guarded database; no application DB is
 * restored, updated, truncated or dropped by this suite.
 */
@EnabledIfEnvironmentVariable(named = "EQ_F08_NATIVE_ENABLED", matches = "true")
class ComplianceNativeIntegrationTest extends ComplianceCompletionTest {
    private String database;
    private boolean created;
    private String masterUrl;
    private String user;
    private String password;
    private String be1Before;
    private DriverManagerDataSource nativeSource;

    @Override
    protected DriverManagerDataSource database() {
        database = "EquiTransit_F08Test_"
                + UUID.randomUUID().toString().replace("-", "");
        masterUrl = System.getenv("EQ_F08_NATIVE_MASTER_URL");
        user = System.getenv("EQ_F08_TEST_DB_USERNAME");
        password = System.getenv("EQ_F08_TEST_DB_PASSWORD");
        if (masterUrl == null || !masterUrl.startsWith("jdbc:sqlserver://")
                || !masterUrl.toLowerCase(java.util.Locale.ROOT)
                        .contains("databasename=master")) {
            throw new IllegalStateException("Explicit master test URL "
                    + "required");
        }
        try (Connection master = connect(masterUrl)) {
            execute(master, "CREATE DATABASE [" + checkedName() + "]");
            created = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot create isolated "
                    + "SQL test DB",
                    ex);
        }
        String url = masterUrl.replaceAll("(?i)databaseName=master",
                "databaseName=" + checkedName());
        nativeSource = new DriverManagerDataSource(url, user, password);
        if (user == null) {
            nativeSource.setUsername(null);
        }
        try (Connection connection = nativeSource.getConnection()) {
            String baseline = new ClassPathResource(
                    "compliance-native-baseline.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            batches(connection, baseline);
            for (int n = 1; n <= 5; n++) {
                batches(connection, migration(n));
            }
            be1Before = fingerprint(connection);
            batches(connection, migration(6));
            assertThat(fingerprint(connection)).isEqualTo(be1Before);
            batches(connection, migration(6));
            assertThat(fingerprint(connection)).isEqualTo(be1Before);
            return nativeSource;
        } catch (Exception ex) {
            closeDatabase();
            throw new IllegalStateException("Native migration setup "
                    + "failed", ex);
        }
    }

    @Override
    protected CaseAssignmentAccess caseAccess(
            NamedParameterJdbcTemplate named) {
        return new CaseAssignmentAccess(new AssessmentRepository(named),
                actors);
    }

    @Override
    protected void seedData() {
        sql.update("INSERT dbo.Role(RoleCode) VALUES('CUSTOMER'),"
                + "('SPECIALIST')");
        sql.update("INSERT dbo.[User](RoleID,Email,PasswordHash) VALUES"
                + "(1,'customer@fixture.invalid','test-only'),"
                + "(2,'specialist@fixture.invalid','test-only')");
        sql.update("INSERT dbo.Customer(UserID,FullName) VALUES(1,'Fixture')");
        sql.update("INSERT dbo.StaffProfile(UserID,FullName) VALUES(2,"
                + "'Fixture')");
        sql.update("INSERT dbo.Horse(CustomerID,Name) VALUES(1,"
                + "'Fixture Horse')");
        sql.update("INSERT dbo.TransportOrder(CustomerID,OrderNumber)"
                + " VALUES(1,'NATIVE-F08-FIXTURE')");
        sql.update("INSERT dbo.TransportOrderDetail(OrderID,HorseID) "
                + "VALUES(1,1)");
        sql.update("INSERT dbo.DocumentTypeCatalog(TypeCode,"
                + "DisplayName,IsActive)"
                + " VALUES('PASSPORT','Passport',1)");
        sql.update("INSERT dbo.OrderAssignment(OrderID,StaffID,AssignmentRole,"
                + "AssignedAt) VALUES(1,1,'SPECIALIST',?)", now().minusDays(1));
        sql.update("UPDATE dbo.TransportOrder SET OrderStatus='APPROVED'");
    }

    @Override
    protected void addRevision(long id, String country) {
        String snapshot = "{\"request\":{\"originCountryCode\":\"VN\","
                + "\"destinationCountryCode\":\"" + country + "\","
                        + "\"horseId\":1,"
                + "\"preferredDepartureAt\":\""
                + now().plusDays(7).atOffset(java.time.ZoneOffset.UTC) + "\","
                + "\"deliveryDeadline\":\""
                + now().plusDays(12).atOffset(java.time.ZoneOffset.UTC) + "\","
                + "\"transitAirports\":[]}}";
        sql.update("INSERT dbo.OrderRevision(OrderID,RevisionNo,OrderSnapshot,"
                + "SubmittedAt) VALUES(1,?,?,?)", id, snapshot, now());
        if (id == 1) {
            sql.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=1");
        }
    }

    @Test
    void canonicalContractRejectsPendingWhileBe2CanRecordPending() {
        specialist();
        tx.executeWithoutResult(s -> service.recordQuarantineAssessment(1,
                new QuarantineAssessmentRequest("PENDING", null, null,
                        "Awaiting sourced decision", null, null)));
        assertThat(sql.queryForObject("SELECT COUNT(*)"
                + " FROM "
                        + "dbo.RevisionQuarantineAssessment",
                                Long.class)).isZero();
        assertThat(sql.queryForObject("SELECT Applicability"
                + " FROM dbo.QuarantineAssessment", String.class))
                .isEqualTo("PENDING");
        assertThatThrownBy(() -> sql.update("INSERT"
                + " dbo.RevisionQuarantineAssessment"
                + " (RevisionID,Applicability,SpecialistID,AssessedAt,"
                + "ValidUntil,SourceReference) VALUES"
                + " (1,'PENDING',1,?,?, 'fixture')", now(), now().plusDays(1)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void approvedEvidenceAndConfirmedQuarantineOpenOnlyCurrentDepartureGate() {
        policy();
        long requirement = requirement();
        long version = upload(java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .plusDays(40));
        long submission = tx.execute(s -> service.submitDocument(1, requirement,
                new DocumentSubmissionRequest(version)).submissionId());
        specialist();
        tx.executeWithoutResult(s -> service.reviewSubmission(1, submission,
                new DocumentReviewRequest("CHANGES_REQUESTED", "Unreadable")));
        customer();
        long replacement = tx.execute(s -> service.submitDocument(1,
                requirement,
                new DocumentSubmissionRequest(version)).submissionId());
        specialist();
        tx.executeWithoutResult(s -> service.reviewSubmission(1, replacement,
                new DocumentReviewRequest("APPROVED", "Verified "
                        + "official copy")));
        tx.executeWithoutResult(s -> service.recordQuarantineAssessment(1,
                new QuarantineAssessmentRequest("NOT_REQUIRED", null, 1L,
                        "Synthetic native regulatory source",
                        now().plusDays(20).atOffset(java.time.ZoneOffset.UTC),
                        "Explicit fixture determination")));
        tx.executeWithoutResult(s -> service.issueClearance(1,
                new ComplianceClearanceRequest("PRE_EXPORT", "PASS",
                        now().plusDays(5).atOffset(java.time.ZoneOffset.UTC),
                        "Synthetic native clearance")));
        customer();
        assertThat(tx.<Boolean>execute(
                s -> service.readiness(1).legalGatePass()))
                .isTrue();
        assertThat(tx.<Boolean>execute(
                s -> service.quarantineReleasePassed(1)))
                .isFalse();
        specialist();
        tx.executeWithoutResult(s -> service.issueClearance(1,
                new ComplianceClearanceRequest("QUARANTINE_RELEASE", "PASS",
                        now().plusDays(5).atOffset(java.time.ZoneOffset.UTC),
                        "Explicit sourced quarantine exemption")));
        customer();
        assertThat(tx.<Boolean>execute(
                s -> service.quarantineReleasePassed(1))).isTrue();
        addRevision(2, "FR");
        sql.update("UPDATE dbo.TransportOrder SET CurrentRevisionID=2");
        assertThat(tx.<Boolean>execute(
                s -> service.readiness(1).legalGatePass()))
                .isFalse();
    }

    @Test
    void persistedAssignmentRevocationImmediatelyDeniesSpecialist() {
        specialist();
        tx.executeWithoutResult(s -> service.listRequirements(1, 0, 20));
        sql.update("UPDATE dbo.OrderAssignment SET EndedAt=?", now());
        assertThatThrownBy(() -> tx.execute(s ->
                service.listRequirements(1, 0, 20)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void concurrentUploadsAllocateDifferentImmutableVersionNumbers()
            throws Exception {
        customer();
        var first = service.storeDocumentFile(1, "one.pdf", "application/pdf",
                "%PDF-1.7 fixture one".getBytes(StandardCharsets.UTF_8));
        var second = service.storeDocumentFile(1, "two.pdf", "application/pdf",
                "%PDF-1.7 fixture two".getBytes(StandardCharsets.UTF_8));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(first, second).stream().map(file -> pool.submit(
                    () -> {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Start timeout");
                        }
                        return tx.execute(s -> service.uploadDocumentVersion(1,
                                "PASSPORT", file.fileKey(),
                                        file.originalFileName(),
                                file.contentType(), file.fileSizeBytes(), null,
                                null, null, file.fileHash()).versionNo());
                    })).toList();
            start.countDown();
            assertThat(tasks.get(0).get(30, TimeUnit.SECONDS))
                    .isNotEqualTo(tasks.get(1).get(30, TimeUnit.SECONDS));
        }
        assertThat(sql.queryForList("SELECT VersionNo FROM dbo.DocumentVersion"
                + " ORDER BY VersionNo", Integer.class)).containsExactly(1, 2);
    }

    @Override
    protected void closeDatabase() {
        if (!created || database == null || masterUrl == null) {
            return;
        }
        try (Connection master = connect(masterUrl)) {
            execute(master, "ALTER DATABASE [" + checkedName()
                    + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            execute(master, "DROP DATABASE [" + checkedName() + "]");
            database = null;
            created = false;
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot remove owned test database",
                    ex);
        }
    }

    private Connection connect(String url) throws SQLException {
        return user == null ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, user, password);
    }

    private String checkedName() {
        if (database == null
                || !database.matches("EquiTransit_F08Test_[a-f0-9]{32}")) {
            throw new IllegalStateException("Unsafe test database name");
        }
        return database;
    }

    private static void execute(Connection connection, String text)
            throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            statement.execute(text);
        }
    }

    private static void batches(Connection connection, String text)
            throws SQLException {
        for (String batch : text.replace("\r", "").split("(?im)^GO\\s*$")) {
            if (!batch.isBlank()) {
                execute(connection, batch);
            }
        }
    }

    private static String migration(int number) throws java.io.IOException {
        try (var paths = Files.list(Path.of("database/migrations"))) {
            Path path = paths.filter(p -> p.getFileName().toString()
                    .startsWith(String.format("%03d_", number)))
                    .findFirst().orElseThrow();
            return Files.readString(path);
        }
    }

    private static String fingerprint(Connection connection)
            throws SQLException {
        // BE1 column types, nullability, computed definitions and constraints.
        String sql = "SELECT name,type,definition FROM ("
                + " SELECT OBJECT_NAME(c.object_id)+'.'+c.name AS name,"
                + " 'COLUMN' AS type,CONCAT(TYPE_NAME(c.user_type_id),':',"
                + " c.max_length,':',c.scale,':',c.is_nullable,':',"
                        + "c.is_computed,"
                + " ':',cc.definition) AS definition FROM sys.columns c"
                + " LEFT JOIN sys.computed_columns cc"
                + " ON cc.object_id=c.object_id AND cc.column_id=c.column_id"
                + " WHERE c.object_id IN (OBJECT_ID('dbo.OrderRevision'),"
                + " OBJECT_ID('dbo.RevisionQuarantineAssessment'))"
                + " UNION ALL SELECT name,'CHECK',definition"
                + " FROM sys.check_constraints WHERE parent_object_id IN"
                + " (OBJECT_ID('dbo.OrderRevision'),"
                + " OBJECT_ID('dbo.RevisionQuarantineAssessment'))) q"
                + " ORDER BY name,type";
        var result = new StringBuilder();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.append(rows.getString(1)).append('|')
                        .append(rows.getString(2)).append('|')
                        .append(rows.getString(3)).append('\n');
            }
        }
        return result.toString();
    }
}
