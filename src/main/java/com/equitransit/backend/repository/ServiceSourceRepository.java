/*
 * ServiceSourceRepository
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Read published rates and version-bound sourced assessments.
 */
package com.equitransit.backend.repository;


import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.equitransit.backend.entity.FitPolicyRecord;
import com.equitransit.backend.entity.BundleSourceRecord;
import com.equitransit.backend.entity.ItineraryPricingContext;
import com.equitransit.backend.entity.QuarantineAssessmentResult;
import com.equitransit.backend.entity.TariffRecord;
import com.equitransit.backend.entity.PublishedTariffRecord;
import com.equitransit.backend.entity.VeterinaryAssessmentResult;


/** Read-only adapters behind typed facades; no fake producer READY results. */
@Repository
public class ServiceSourceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc shared parameterized SQL template */
    public ServiceSourceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reads approved global measurement policies covering the requested
     * horizon.
     * Scoped equipment/carrier policies cannot be applied without their
     * context.
     * @param now server time
     * @param horizon delivery horizon or now when unknown
     * @return effective policies, never automatic highest-version selection
     */
    public List<FitPolicyRecord> fitPolicies(OffsetDateTime now,
            OffsetDateTime horizon) {
        return jdbc.query("SELECT PolicyID,PolicyCode,VersionNo,SlotQuantity,"
                + "MaxWeightKg,MaxHeightCm,MaxWidthCm FROM dbo.StallFitPolicy"
                + " WITH (HOLDLOCK)"
                + " WHERE Status='APPROVED' AND StallID IS NULL"
                + " AND CarrierSupplierID IS NULL AND EffectiveFrom<=:now"
                + " AND (EffectiveTo IS NULL OR EffectiveTo>:horizon)"
                + " AND ReviewedBy IS NOT NULL AND ReviewedAt<=:now"
                + " AND ((SourceURL LIKE 'https://%' OR SourceURL LIKE"
                + " 'http://%') OR LEN(LTRIM(RTRIM(SourceFileKey)))>0)",
                times(now, horizon), (row, index) -> new FitPolicyRecord(
                        row.getLong("PolicyID"), row.getString("PolicyCode"),
                        row.getInt("VersionNo"),
                        row.getBigDecimal("SlotQuantity"),
                        row.getBigDecimal("MaxWeightKg"),
                        row.getBigDecimal("MaxHeightCm"),
                        row.getBigDecimal("MaxWidthCm")));
    }

    /**
     * Reads the bounded booking catalogue once, retaining rate ambiguity.
     * @param codes server-owned catalogue codes
     * @param now server calculation time
     * @return effective published USD versions for all requested services
     */
    public List<PublishedTariffRecord> tariffs(List<String> codes,
            OffsetDateTime now) {
        return jdbc.query("WITH rates AS (SELECT ServiceCode,OptionCode,"
                + "TariffID,VersionNo,UnitCode,CostGroup,UnitPrice,"
                + "ROW_NUMBER() OVER (PARTITION BY ServiceCode,OptionCode"
                + " ORDER BY TariffID) AS MatchingVersion"
                + " FROM dbo.ServiceTariff WHERE ServiceCode IN (:codes)"
                + " AND OptionCode IN (:options) AND Currency='USD'"
                + " AND Status='ACTIVE' AND EffectiveFrom<=:now"
                + " AND (EffectiveTo IS NULL OR EffectiveTo>:now))"
                + " SELECT ServiceCode,OptionCode,TariffID,VersionNo,"
                + "UnitCode,CostGroup,UnitPrice FROM rates"
                + " WHERE MatchingVersion<=2",
                new MapSqlParameterSource("codes", codes)
                        .addValue("options", List.of("EQUITRANSIT", "DEFAULT",
                                "ENHANCED", "EXTRA", "EXAM", "TEST", "VACCINE",
                                "CERTIFICATE", "STANDARD", "NONE", "EXISTING",
                                "MORTALITY", "COMBINED", "REQUESTED", ""))
                        .addValue("now", now.toLocalDateTime()),
                (row, index) -> new PublishedTariffRecord(
                        row.getString("ServiceCode"),
                        row.getString("OptionCode"),
                        new TariffRecord(row.getLong("TariffID"),
                                row.getInt("VersionNo"),
                                row.getString("UnitCode"),
                                row.getString("CostGroup"),
                                row.getBigDecimal("UnitPrice"))));
    }

    /**
     * Reads only the exact current confirmed plan's individually sourced legs.
     * @param order owned parent
     * @param revision exact current submitted revision
     * @param now server time
     * @return sourced kilometres or explicit pending quantities
     */
    public ItineraryPricingContext itinerary(long order, long revision,
            OffsetDateTime now) {
        return itinerary(order, revision, now, false);
    }

    /**
     * Reads sourced draft distances without claiming resource confirmation.
     * @param order authorized parent
     * @param revision exact submitted revision
     * @param now source evaluation time
     * @param quotation whether draft plans may supply quotation quantities
     * @return sourced quantities; never an operational readiness result
     */
    public ItineraryPricingContext itinerary(long order, long revision,
            OffsetDateTime now, boolean quotation) {
        String sql = "SELECT p.PlanID,"
                + "SUM(CASE WHEN l.TransportMode='ROAD' THEN l.DistanceKm"
                + " ELSE 0 END) AS RoadKm,"
                + "SUM(CASE WHEN l.TransportMode='AIR' THEN l.DistanceKm"
                + " ELSE 0 END) AS AirKm,"
                + "COUNT(l.LegID) AS Legs,"
                + "SUM(CASE WHEN l.LegID IS NULL OR l.DistanceKm<=0"
                + " OR l.DistanceSource IS NULL OR l.DistanceConfirmedAt>:now"
                + " OR l.DistanceConfirmedAt IS NULL OR proof.ValidStaff=0"
                + " THEN 1 ELSE 0 END) AS MissingSources"
                + " FROM dbo.TransportOrder o JOIN dbo.TransportPlan p"
                + " ON p.PlanID=o.CurrentPlanID AND p.OrderID=o.OrderID"
                + " AND p.RevisionID=:revision AND p.PlanStatus IN ("
                + (quotation ? "'DRAFT','PROPOSED','PLANNED','CONFIRMED'"
                        : "'CONFIRMED'") + ")"
                + " LEFT JOIN dbo.TripLeg l ON l.PlanID=p.PlanID"
                + " AND l.TransportMode IN ('ROAD','AIR')"
                + " OUTER APPLY (SELECT COUNT(*) AS ValidStaff"
                + " FROM dbo.OrderAssignment a JOIN dbo.StaffProfile s"
                + " ON s.StaffID=a.StaffID JOIN dbo.[User] u"
                + " ON u.UserID=s.UserID JOIN dbo.[Role] r ON r.RoleID=u.RoleID"
                + " WHERE a.OrderID=o.OrderID AND a.EndedAt IS NULL"
                + " AND a.AssignmentRole='COORDINATOR'"
                + " AND a.StaffID=l.DistanceConfirmedBy"
                + " AND r.RoleCode='COORDINATOR' AND u.Status='ACTIVE') proof"
                + " WHERE o.OrderID=:order GROUP BY p.PlanID";

        if (!available("dbo.TripLeg", List.of("DistanceSource",
                "DistanceConfirmedBy", "DistanceConfirmedAt"))) {
            return new ItineraryPricingContext(null, null, null,
                    "SOURCE_SCHEMA_PENDING");
        }

        return jdbc.query(sql, new MapSqlParameterSource("order", order)
                .addValue("revision", revision)
                .addValue("now", now.toLocalDateTime()), (row, index) ->
                row.getLong("Legs") > 0 && row.getInt("MissingSources") == 0
                        ? new ItineraryPricingContext(row.getLong("PlanID"),
                                row.getBigDecimal("RoadKm"),
                                row.getBigDecimal("AirKm"), "SOURCED")
                        : new ItineraryPricingContext(row.getLong("PlanID"),
                                null, null, "PENDING"))
                .stream().findFirst().orElse(new ItineraryPricingContext(
                        null, null, null, "PENDING"));
    }

    /**
     * Reads a valid source-backed assessment from the assigned Specialist.
     * @param order owned order
     * @param revision exact revision
     * @param now server time
     * @param horizon required delivery horizon
     * @return applicability, distinct from an absent/expired UNKNOWN assessment
     */
    public QuarantineAssessmentResult quarantine(long order, long revision,
            OffsetDateTime now, OffsetDateTime horizon) {
        if (!available("dbo.RevisionQuarantineAssessment", List.of(
                "RevisionID", "Applicability", "RequiredDays", "SupersededAt",
                "SpecialistID", "AssessedAt", "ValidUntil",
                "AssessmentID", "SourceReference"))) {
            return new QuarantineAssessmentResult("UNKNOWN", null, null);
        }
        return jdbc.query("SELECT q.AssessmentID,q.Applicability,q.RequiredDays"
                + " FROM dbo.RevisionQuarantineAssessment q"
                + " WHERE q.RevisionID=:revision AND q.SupersededAt IS NULL"
                + " AND q.AssessedAt<=:now"
                + " AND q.ValidUntil>:horizon" + specialistProof("q"),
                times(now, horizon).addValue("revision", revision)
                        .addValue("order", order), (row, index) ->
                new QuarantineAssessmentResult(row.getString("Applicability"),
                        row.getObject("RequiredDays", Integer.class),
                        row.getLong("AssessmentID")))
                .stream().findFirst().orElse(new QuarantineAssessmentResult(
                        "UNKNOWN", null, null));
    }

    /**
     * Reads clinically assessed quantities, never customer requested
     * reductions.
     * @param order owned order
     * @param revision exact current revision
     * @param now server time
     * @param horizon delivery horizon
     * @return real unit counts, or null quantities pending F08
     */
    public VeterinaryAssessmentResult veterinary(long order, long revision,
            OffsetDateTime now, OffsetDateTime horizon) {
        if (!available("dbo.RevisionVeterinaryAssessment", List.of(
                "RevisionID", "VisitCount", "TestCount", "DoseCount",
                "CertificateCount", "SupersededAt", "SpecialistID",
                "AssessedAt",
                "ValidUntil", "SourceReference"))) {
            return new VeterinaryAssessmentResult(null, null, null, null);
        }
        return jdbc.query("SELECT q.VisitCount,q.TestCount,q.DoseCount,"
                + "q.CertificateCount FROM dbo.RevisionVeterinaryAssessment q"
                + " WHERE q.RevisionID=:revision AND q.SupersededAt IS NULL"
                + " AND q.AssessedAt<=:now"
                + " AND q.ValidUntil>:horizon" + specialistProof("q"),
                times(now, horizon).addValue("revision", revision)
                        .addValue("order", order), (row, index) ->
                new VeterinaryAssessmentResult(row.getInt("VisitCount"),
                        row.getInt("TestCount"), row.getInt("DoseCount"),
                        row.getInt("CertificateCount")))
                .stream().findFirst().orElse(new VeterinaryAssessmentResult(
                        null, null, null, null));
    }

    /**
     * Returns bundle metadata only; supplier purchase prices are never
     * selected.
     * @param revision exact revision
     * @param now server time
     * @return selected airfare bundle envelopes, including ambiguous duplicates
     */
    public List<BundleSourceRecord> bundles(long revision, OffsetDateTime now) {
        return jdbc.query("SELECT IncludesStall,IncludedCharges"
                + " FROM dbo.SupplierOffer WHERE RevisionID=:revision"
                + " AND ServiceCode='AIRFARE' AND Status='SELECTED'"
                + " AND ValidUntil>:now"
                + " AND LEN(LTRIM(RTRIM(EvidenceFileKey)))>0",
                new MapSqlParameterSource("revision", revision)
                        .addValue("now", now.toLocalDateTime()),
                (row, index) -> new BundleSourceRecord(
                        row.getBoolean("IncludesStall"),
                        row.getString("IncludedCharges")));
    }

    /**
     * Reads the chosen commercial version, never an arbitrary supplier offer.
     * Retail prices and commercial assessments are frozen in this snapshot.
     * @param order authorized parent
     * @param revision exact current submitted revision
     * @param now server UTC time
     * @return latest usable snapshot, without exposing purchase costs
     */
    public java.util.Optional<String> commercialSnapshot(long order,
            long revision, OffsetDateTime now) {
        if (!available("dbo.Quote", List.of("PricingSnapshotJSON"))) {
            return java.util.Optional.empty();
        }
        return jdbc.query("SELECT TOP (1) q.PricingSnapshotJSON"
                + " FROM dbo.Quote q JOIN dbo.OrderRevision r"
                + " ON r.RevisionID=q.RevisionID JOIN dbo.TransportOrder o"
                + " ON o.OrderID=r.OrderID AND o.CurrentRevisionID=r.RevisionID"
                + " WHERE o.OrderID=:order AND r.RevisionID=:revision"
                + " AND q.PricingSnapshotJSON IS NOT NULL"
                + " AND (q.QuoteStatus='ACCEPTED' OR (q.QuoteStatus IN"
                + " ('DRAFT','APPROVED','ISSUED') AND q.ValidUntil>:now))"
                + " ORDER BY q.VersionNo DESC,q.QuoteID DESC",
                new MapSqlParameterSource("order", order)
                        .addValue("revision", revision)
                        .addValue("now", now.toLocalDateTime()),
                (row, index) -> row.getString("PricingSnapshotJSON"))
                .stream().findFirst();
    }

    private String specialistProof(String alias) {
        return " AND EXISTS (SELECT 1 FROM dbo.OrderAssignment a"
                + " JOIN dbo.StaffProfile s ON s.StaffID=a.StaffID"
                + " JOIN dbo.[User] u ON u.UserID=s.UserID"
                + " JOIN dbo.[Role] r ON r.RoleID=u.RoleID"
                + " WHERE a.OrderID=:order AND a.EndedAt IS NULL"
                + " AND a.AssignmentRole='SPECIALIST'"
                + " AND a.StaffID=" + alias + ".SpecialistID"
                + " AND r.RoleCode='SPECIALIST' AND u.Status='ACTIVE')";
    }

    private MapSqlParameterSource times(OffsetDateTime now,
            OffsetDateTime horizon) {
        return new MapSqlParameterSource("now", now.toLocalDateTime())
                .addValue("horizon", horizon.toLocalDateTime());
    }

    private boolean available(String table, List<String> columns) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns"
                + " WHERE object_id=OBJECT_ID(:table,'U')"
                + " AND name IN (:columns)",
                new MapSqlParameterSource("table", table)
                        .addValue("columns", columns), Integer.class);

        return count != null && count == columns.size();
    }
}
