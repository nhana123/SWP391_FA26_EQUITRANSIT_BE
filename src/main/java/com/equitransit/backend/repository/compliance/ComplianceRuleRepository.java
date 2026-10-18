package com.equitransit.backend.repository.compliance;


import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.exception.compliance.ComplianceException;


/** Reads actual sourced rules; absence never becomes an exemption. */
@Repository
public class ComplianceRuleRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public ComplianceRuleRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param rule the persisted rule key
     * @param type the requested type
     * @param phase the requested phase
     */
    public void requireReviewedRule(long rule, String type, String phase) {
        long matches = count("SELECT COUNT(*) FROM dbo.ComplianceRuleVersion r"
                + " JOIN dbo.DocumentTypeCatalog c ON c.TypeCode=r.DocumentType"
                + " WHERE r.RuleID=:id AND r.DocumentType=:type"
                + " AND r.PhaseCode=:phase AND c.IsActive=1"
                + " AND r.VersionNo>0 AND r.ReviewedAt IS NOT NULL"
                + " AND LEN(LTRIM(RTRIM(r.SourceURL)))>0"
                + " AND r.EffectiveFrom<=:now"
                + " AND (r.EffectiveTo IS NULL OR r.EffectiveTo>=:now)",
                args("id", rule).addValue("type", type)
                        .addValue("phase", phase).addValue("now", now()));

        if (matches != 1) {
            throw new ComplianceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "RULE_PENDING_ASSESSMENT", "A reviewed rule is required.");
        }
    }

    /** @param order the authorized order
     * @param horse the requested horse
     */
    public void requireOrderHorse(long order, long horse) {
        require(count("SELECT COUNT(*) FROM dbo.TransportOrderDetail"
                + " WHERE OrderID=:order AND HorseID=:horse",
                args("order", order).addValue("horse", horse)) == 1);
    }

    /**
     * Validates the rule against the revision's route and departure date.
     * Rejects rules whose origin, destination or transit country differs
     * from the revision, or whose effective window does not cover the
     * revision's preferred departure date.
     * @param rule the persisted rule key
     * @param type the requested document type
     * @param phase the requested phase code
     * @param revision the authorized revision key
     */
    public void requireRouteMatchedRule(long rule, String type,
            String phase, long revision) {
        long matches = count(
                "SELECT COUNT(*) FROM dbo.ComplianceRuleVersion r"
                + " JOIN dbo.DocumentTypeCatalog c"
                + "   ON c.TypeCode = r.DocumentType AND c.IsActive = 1"
                + " JOIN dbo.RevisionRouteSource rev"
                + "   ON rev.RevisionID = :rev"
                + " WHERE r.RuleID = :id"
                + " AND r.DocumentType = :type"
                + " AND r.PhaseCode = :phase"
                + " AND r.VersionNo > 0"
                + " AND r.ReviewedAt IS NOT NULL"
                + " AND LEN(LTRIM(RTRIM(r.SourceURL))) > 0"
                + " AND r.OriginCountryCode = rev.OriginCountryCode"
                + " AND r.DestinationCountryCode ="
                + "     rev.DestinationCountryCode"
                + " AND (r.TransitCountryCode IS NULL"
                + "   OR EXISTS (SELECT 1 FROM dbo.TransitAirport ta"
                + "     JOIN dbo.Airport ap"
                + "       ON ap.AirportCode = ta.AirportCode"
                + "     WHERE ta.RevisionID = rev.RevisionID"
                + "       AND ap.CountryCode = r.TransitCountryCode"
                + "       AND ap.ReviewedAt IS NOT NULL"
                + "       AND LEN(LTRIM(RTRIM(ap.SourceReference)))>0))"
                + " AND rev.PreferredDepartureAt IS NOT NULL"
                + " AND r.EffectiveFrom <="
                + "     COALESCE(rev.PreferredDepartureAt, :now)"
                + " AND (r.EffectiveTo IS NULL"
                + "   OR r.EffectiveTo >="
                + "     COALESCE(rev.PreferredDepartureAt, :now))",
                args("id", rule).addValue("type", type)
                        .addValue("phase", phase)
                        .addValue("rev", revision)
                        .addValue("now", now()));
        if (matches != 1) {
            throw new ComplianceException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "RULE_ROUTE_MISMATCH",
                    "No reviewed rule matches this route and departure date.");
        }
    }
}
