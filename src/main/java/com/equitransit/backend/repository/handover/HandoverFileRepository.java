/*
 * HandoverFileRepository
 * Purpose: Parameterized JDBC storage for F14 private files.
 */
package com.equitransit.backend.repository.handover;

import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores files scoped to an order without exposing internal cost files. */
@Repository
public class HandoverFileRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param source transaction-aware application datasource */
    public HandoverFileRepository(DataSource source) {
        jdbc = new NamedParameterJdbcTemplate(source);
    }

    /** @return true when migration 019 is deployed */
    public boolean ready() {
        Integer count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM sys.tables WHERE name='HandoverFile'",
                Integer.class);
        return count != null && count == 1;
    }

    /**
     * Checks staff access to an order using domain staff identity.
     * @param orderId order key
     * @param staffId staff profile key
     * @param role current staff role
     * @return whether access is active
     */
    public boolean staffCanAccess(long orderId, long staffId, String role) {
        String sql = "SELECT COUNT(*) FROM dbo.[User] u "
                + "JOIN dbo.StaffProfile s ON s.UserID=u.UserID "
                + "WHERE s.StaffID=:staff AND u.Status='ACTIVE' AND "
                + "((:role='MANAGER') OR (:role='COORDINATOR' AND EXISTS "
                + "(SELECT 1 FROM "
                + "dbo.OrderAssignment a WHERE a.OrderID=:order "
                + "AND a.StaffID=:staff AND a.EndedAt IS NULL "
                + "AND a.AssignmentRole='COORDINATOR')) OR (:role='DRIVER' "
                + "AND EXISTS (SELECT 1 FROM dbo.TripAssignment ta JOIN "
                + "dbo.TripLeg l "
                + "ON l.LegID=ta.LegID JOIN dbo.TransportPlan p ON "
                + "p.PlanID=l.PlanID WHERE p.OrderID=:order "
                + "AND ta.StaffID=:staff AND ta.RoleInTrip='DRIVER' "
                + "AND ta.Status IN ('CONFIRMED','ASSIGNED','ACTIVE'))))";
        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("order", orderId)
                .addValue("staff", staffId)
                .addValue("role", role), Integer.class);
        return count != null && count > 0;
    }

    /** @param orderId order key @param customerId customer key
     * @return whether customer owns the order */
    public boolean customerOwns(long orderId, long customerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.TransportOrder "
                        + "WHERE OrderID=:order AND CustomerID=:customer",
                new MapSqlParameterSource().addValue("order", orderId)
                        .addValue("customer", customerId), Integer.class);
        return count != null && count == 1;
    }

    /**
     * Stores immutable file content.
     * @param key opaque file key
     * @param orderId order key
     * @param userId uploader account key
     * @param purpose file classification
     * @param contentType validated MIME type
     * @param fileName validated file name
     * @param bytes inspected content
     * @param hash SHA-256 content hash
     */
    public void insert(String key, long orderId, long userId, String purpose,
            String contentType, String fileName, byte[] bytes, String hash) {
        String sql = "INSERT dbo.HandoverFile(FileKey,OrderID,UploadedBy,"
                + "Purpose,ContentType,OriginalFileName,ContentBytes,FileHash)"
                + " VALUES(:key,:order,:user,:purpose,:type,:name,:bytes,"
                + ":hash)";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("order", orderId)
                .addValue("user", userId)
                .addValue("purpose", purpose)
                .addValue("type", contentType)
                .addValue("name", fileName)
                .addValue("bytes", bytes)
                .addValue("hash", hash));
    }

    /**
     * Reads a file scoped to the order.
     * @param orderId order key
     * @param key opaque file key
     * @param purpose optional purpose filter
     * @return file row
     */
    public Optional<Map<String, Object>> find(long orderId, String key,
            String purpose) {
        String sql = "SELECT Purpose,ContentType,OriginalFileName,"
                + "ContentBytes FROM dbo.HandoverFile WHERE OrderID=:order "
                + "AND FileKey=:key AND (:purpose IS NULL OR Purpose=:purpose)";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("order", orderId)
                .addValue("key", key)
                .addValue("purpose", purpose));
        return rows.isEmpty() ? Optional.empty()
                : Optional.of(rows.getFirst());
    }

    /**
     * Reads only customer-visible handover files.
     * @param orderId order key
     * @param key opaque file key
     * @return non-cost file row
     */
    public Optional<Map<String, Object>> findCustomer(long orderId,
            String key) {
        String sql = "SELECT Purpose,ContentType,OriginalFileName,"
                + "ContentBytes FROM dbo.HandoverFile WHERE OrderID=:order "
                + "AND FileKey=:key AND Purpose<>'ACTUAL_COST_EVIDENCE'";
        var rows = jdbc.queryForList(sql, Map.of("order", orderId,
                "key", key));
        return rows.isEmpty() ? Optional.empty()
                : Optional.of(rows.getFirst());
    }
}
