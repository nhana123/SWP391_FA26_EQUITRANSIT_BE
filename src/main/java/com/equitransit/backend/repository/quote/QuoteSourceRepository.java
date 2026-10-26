/*
 * QuoteSourceRepository
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.repository.quote;


import com.equitransit.backend.entity.quote.QuotePriceSource;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** Bounded source batches with finite query timeout; no per-line queries. */
@Repository
@SuppressWarnings("null")
public class QuoteSourceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param dataSource shared transaction-aware datasource */
    public QuoteSourceRepository(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    /**
     * Reads requested sources and the current plan's retail airfare estimate.
     * Missing, expired and superseded sources stay absent.
     * @param revision exact order revision
     * @param tariffs bounded selected tariff keys
     * @param offers bounded selected supplier keys
     * @param now server time
     * @return retail-only source records, without supplier purchase cost
     */
    public List<QuotePriceSource> read(long revision, List<Long> tariffs,
            List<Long> offers, OffsetDateTime now) {
        var args = new MapSqlParameterSource("revision", revision)
                .addValue("tariffs", tariffs.isEmpty() ? List.of(-1L)
                        : tariffs)
                .addValue("offers", offers.isEmpty() ? List.of(-1L) : offers)
                .addValue("now", now.toLocalDateTime());
        String planned = planAirfare();
        String sql = "SELECT TariffID AS SourceID,0 AS IsOffer,ServiceCode,"
                + "UnitCode,CostGroup,UnitPrice AS RetailRate,"
                + "CAST(NULL AS DECIMAL(18,4)) AS Quantity,"
                + "EffectiveTo AS ValidUntil,CAST(NULL AS NVARCHAR(MAX))"
                + " AS IncludedCharges,0 AS IncludesStall,VersionNo"
                + " FROM dbo.ServiceTariff WITH (HOLDLOCK)"
                + " WHERE TariffID IN (:tariffs) AND Currency='USD'"
                + " AND Status='ACTIVE' AND EffectiveFrom<=:now"
                + " AND (EffectiveTo IS NULL OR EffectiveTo>:now)"
                + " UNION ALL SELECT OfferID,1,ServiceCode,UnitCode,"
                + "CAST(NULL AS VARCHAR(50)),RetailUnitPrice,Quantity,"
                + "ValidUntil,IncludedCharges,IncludesStall,CAST(NULL AS INT)"
                + " FROM dbo.SupplierOffer WITH (HOLDLOCK)"
                + " WHERE (OfferID IN (:offers) OR (" + planned + "))"
                + " AND RevisionID=:revision"
                + " AND Status IN ('RECEIVED','SELECTED')"
                + " AND Currency='USD' AND ValidUntil>:now"
                + " AND RetailUnitPrice IS NOT NULL"
                + " AND (LEN(LTRIM(RTRIM(EvidenceFileKey)))>0"
                + " OR (" + planned + "))"
                + " AND RecordedBy IS NOT NULL AND RecordedAt<=:now";

        return jdbc.query(sql, args, (row, index) -> {
            Timestamp expiry = row.getTimestamp("ValidUntil");

            return new QuotePriceSource(row.getLong("SourceID"),
                    row.getBoolean("IsOffer"), row.getString("ServiceCode"),
                    row.getString("UnitCode"), row.getString("CostGroup"),
                    row.getBigDecimal("RetailRate"),
                    row.getBigDecimal("Quantity"), expiry == null ? null
                            : expiry.toLocalDateTime()
                                    .atOffset(ZoneOffset.UTC),
                    row.getString("IncludedCharges"),
                    row.getBoolean("IncludesStall"),
                    row.getObject("VersionNo", Integer.class));
        });
    }

    /** @return exact current-plan provenance for server-recorded estimates */
    public static String planAirfare() {
        return "ServiceCode='AIRFARE' AND Status='SELECTED'"
                + " AND ProviderName='Plan airfare estimate'"
                + " AND EvidenceFileKey IS NULL AND EXISTS (SELECT 1"
                + " FROM dbo.TransportOrder o JOIN dbo.TransportPlan p"
                + " ON p.PlanID=o.CurrentPlanID AND p.OrderID=o.OrderID"
                + " WHERE p.RevisionID=:revision AND p.PlanStatus IN"
                + " ('DRAFT','PROPOSED','PLANNED','CONFIRMED')"
                + " AND SupplierOffer.RevisionID=p.RevisionID"
                + " AND SupplierOffer.RecordedBy=p.CreatedBy"
                + " AND OfferReference=CONCAT('PLAN-AIRFARE:',p.PlanID))";
    }
}
