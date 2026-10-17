/*
 * TariffRepository
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Versioned service retail prices and read-only estimates.
 */
package com.equitransit.backend.repository;


import com.equitransit.backend.dto.pricing.TariffResponse;
import com.equitransit.backend.dto.pricing.TariffWriteRequest;
import com.equitransit.backend.entity.pricing.ServiceProduct;
import com.equitransit.backend.entity.pricing.TariffStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;


/**
 * Serialized tariff mutations and immutable audit receipts, in one transaction.
 */
@Repository
public class TariffRepository {

    private static final String COLUMNS = "TariffID,ServiceCode,OptionCode,"
            + "UnitCode,UnitPrice,Currency,VersionNo,EffectiveFrom,EffectiveTo,"
            + "Status,SourceReference,RecordVersion";

    private final NamedParameterJdbcTemplate jdbc;

    /** @param source shared transaction-aware datasource */
    public TariffRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return whether all additive management columns exist */
    public boolean ready() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE"
                + " object_id=OBJECT_ID('dbo.ServiceTariff') AND name IN"
                + " ('SourceReference','PublishedBy','PublishedAt',"
                + "'RecordVersion')", new MapSqlParameterSource(),
                Integer.class) == 4;
    }

    /** @return application lock result; negative means timeout/failure */
    public int lock() {
        return jdbc.queryForObject("DECLARE @result INT; EXEC @result="
                + "sys.sp_getapplock @Resource='EquiTransit:service-tariffs',"
                + "@LockMode='Exclusive',@LockOwner='Transaction',"
                + "@LockTimeout=5000; SELECT @result",
                new MapSqlParameterSource(), Integer.class);
    }

    /**
     * @param page zero-based page
     * @param size bounded size
     * @return latest versions first, without per-row queries
     */
    public List<TariffResponse> list(int page, int size) {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.ServiceTariff"
                + " ORDER BY TariffID DESC OFFSET :offset ROWS"
                + " FETCH NEXT :size ROWS ONLY",
                    new MapSqlParameterSource("offset", (long) page * size)
                        .addValue("size", size), this::map);
    }

    /** @return total tariff version count */
    public long count() {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.ServiceTariff",
                new MapSqlParameterSource(), Long.class);
    }

    /**
     * @param id exact version
     * @return persisted record without supplier cost
     */
    public Optional<TariffResponse> find(long id) {
        return jdbc.query("SELECT " + COLUMNS
                + " FROM dbo.ServiceTariff WHERE TariffID=:id",
                new MapSqlParameterSource("id", id), this::map)
                .stream().findFirst();
    }

    /**
     * @param product immutable service/option identity
     * @return most recent version, including retired records
     */
    public Optional<TariffResponse> latest(ServiceProduct product) {
        return jdbc.query("SELECT TOP(1) " + COLUMNS
                + " FROM dbo.ServiceTariff WHERE ServiceCode=:code"
                + " AND OptionCode=:option AND Currency='USD'"
                + " ORDER BY VersionNo DESC,TariffID DESC", args(product),
                this::map).stream().findFirst();
    }

    /**
     * Checks legacy overlaps instead of publishing into ambiguous rates.
     * @param product exact pricing family
     * @param previous sole predecessor or null
     * @param now publication time
     * @return whether another usable rate conflicts
     */
    public boolean overlaps(ServiceProduct product, Long previous,
            OffsetDateTime now) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ServiceTariff"
                + " WHERE ServiceCode=:code AND OptionCode=:option"
                + " AND Currency='USD' AND Status='ACTIVE'"
                + " AND (EffectiveTo IS NULL OR EffectiveTo>:now)"
                + " AND TariffID<>:previous", args(product)
                        .addValue("now", utc(now))
                        .addValue("previous", previous == null ? -1 : previous),
                Integer.class) > 0;
    }

    /**
     * @param previous existing predecessor
     * @param at replacement activation
     */
    public void end(TariffResponse previous, OffsetDateTime at) {
        jdbc.update("UPDATE dbo.ServiceTariff SET EffectiveTo=:at"
                + " WHERE TariffID=:id AND (EffectiveTo IS NULL"
                + " OR EffectiveTo>:at)",
                new MapSqlParameterSource("at", utc(at))
                        .addValue("id", previous.tariffId()));
    }

    /**
     * @param request validated price/evidence
     * @param version next family version allocated under the catalog lock
     * @param staff trusted Manager StaffID
     * @param from activation time
     * @return new immutable retail price version
     */
    public long insert(TariffWriteRequest request, int version, long staff,
            OffsetDateTime from) {
        var args = args(request.product()).addValue("group",
                request.product().group()).addValue("unit",
                request.product().unit()).addValue("rate",
                    new BigDecimal(request.unitPrice()))
                .addValue("source", request.sourceReference().trim())
                .addValue("staff", staff).addValue("version", version)
                .addValue("from", utc(from))
                .addValue("until", request.validUntil() == null ? null
                        : utc(request.validUntil()));

        return jdbc.queryForObject("INSERT dbo.ServiceTariff (ServiceCode,"
                + "OptionCode,CostGroup,UnitCode,UnitPrice,Currency,VersionNo,"
                + "EffectiveFrom,EffectiveTo,Status,SourceReference,"
                + "PublishedBy,"
                + "PublishedAt) OUTPUT INSERTED.TariffID VALUES (:code,:option,"
                + ":group,:unit,:rate,'USD',:version,:from,:until,'ACTIVE',"
                + ":source,:staff,SYSUTCDATETIME())", args, Long.class);
    }

    /**
     * @param user trusted UserID, distinct from StaffID
     * @param operation UUID string
     * @return saved receipt envelope for replay
     */
    public Optional<String> operation(long user, String operation) {
        return jdbc.query("SELECT TOP(1) ChangeSnapshot FROM dbo.AuditEvent"
                + " WHERE EntityType='SERVICE_TARIFF' AND UserID=:user"
                + " AND JSON_VALUE(ChangeSnapshot,'$.operationId')=:operation"
                + " ORDER BY EventID DESC", new MapSqlParameterSource("user",
                        user).addValue("operation", operation),
                (row, index) -> row.getString(1)).stream().findFirst();
    }

    /**
     * @param user trusted UserID
     * @param id resulting tariff version
     * @param envelope typed completion receipt encoded as private JSON
     */
    public void audit(long user, long id, String envelope) {
        jdbc.update("INSERT dbo.AuditEvent (UserID,EntityType,EntityID,"
                + "ActionCode,ChangeSnapshot) VALUES (:user,'SERVICE_TARIFF',"
                + ":id,'PUBLISH',:envelope)", new MapSqlParameterSource("user",
                        user).addValue("id", id)
                        .addValue("envelope", envelope));
    }

    private MapSqlParameterSource args(ServiceProduct product) {
        return new MapSqlParameterSource("code", product.code())
                .addValue("option", product.option());
    }

    private java.time.LocalDateTime utc(OffsetDateTime time) {
        return time.withOffsetSameInstant(ZoneOffset.UTC).withNano(0)
                .toLocalDateTime();
    }

    private OffsetDateTime time(Timestamp time) {
        return time == null ? null : time.toLocalDateTime()
                .atOffset(ZoneOffset.UTC);
    }

    private TariffResponse map(java.sql.ResultSet row, int index)
            throws java.sql.SQLException {
        BigDecimal rate = row.getBigDecimal("UnitPrice");
        String exact = rate.stripTrailingZeros().scale() <= 2
                ? rate.setScale(2, RoundingMode.HALF_UP).toPlainString() : null;

        return new TariffResponse(row.getLong("TariffID"),
                ServiceProduct.find(row.getString("ServiceCode"),
                        row.getString("OptionCode")), row.getString("UnitCode"),
                exact,
                row.getString("Currency"), row.getInt("VersionNo"),
                time(row.getTimestamp("EffectiveFrom")),
                time(row.getTimestamp("EffectiveTo")),
                TariffStatus.valueOf(row.getString("Status")),
                row.getString("SourceReference"), exact == null
                        || row.getString("SourceReference") == null,
                HexFormat.of().formatHex(
                        row.getBytes("RecordVersion")));
    }
}
