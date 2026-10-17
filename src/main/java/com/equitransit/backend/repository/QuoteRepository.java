/*
 * QuoteRepository
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement versioned, source-backed quotation contracts.
 */
package com.equitransit.backend.repository;


import com.equitransit.backend.dto.quote.QuoteSchedule;
import com.equitransit.backend.dto.quote.QuoteSummary;
import com.equitransit.backend.dto.quote.SupplierOfferRequest;
import com.equitransit.backend.entity.quote.QuoteRecord;
import com.equitransit.backend.entity.quote.QuoteSnapshot;
import com.equitransit.backend.entity.quote.QuoteStatus;
import com.equitransit.backend.json.OrderJsonCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** All writes follow the parent TransportOrder lock owned by the service. */
@Repository
public class QuoteRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final OrderJsonCodec json;

    /**
     * @param dataSource shared transactional datasource
     * @param json snapshot encoder
     */
    public QuoteRepository(DataSource dataSource, OrderJsonCodec json) {
        JdbcTemplate template = new JdbcTemplate(dataSource);

        template.setQueryTimeout(10);
        this.jdbc = new NamedParameterJdbcTemplate(template);
        this.json = json;
    }

    /**
     * @param order parent key
     * @param id quote key
     * @return quote bound to its persisted parent
     */
    public Optional<QuoteRecord> find(long order, long id) {
        return jdbc.query("SELECT q.QuoteID,q.RevisionID,q.QuoteNumber,"
                + "q.VersionNo,q.QuoteStatus,q.TotalQuotedAmount,q.ValidUntil,"
                + "q.IssuedAt,q.AcceptedAt,q.PricingSnapshotJSON"
                + " FROM dbo.Quote q"
                + " JOIN dbo.OrderRevision r ON r.RevisionID=q.RevisionID"
                + " WHERE r.OrderID=:order AND q.QuoteID=:id",
                args(order).addValue("id", id), (row, index) ->
                new QuoteRecord(row.getLong("QuoteID"),
                        row.getLong("RevisionID"), row.getString("QuoteNumber"),
                        row.getInt("VersionNo"),
                        QuoteStatus.valueOf(row.getString("QuoteStatus")),
                        row.getBigDecimal("TotalQuotedAmount"),
                        time(row.getTimestamp("ValidUntil")),
                        time(row.getTimestamp("IssuedAt")),
                        time(row.getTimestamp("AcceptedAt")),
                        row.getString("PricingSnapshotJSON")))
                .stream().findFirst();
    }

    /**
     * @param order authorized parent
     * @param customer whether to omit unpublished internal quotes
     * @param page bounded page
     * @param size bounded size
     * @return stable page of headers without item N+1 queries
     */
    public List<QuoteSummary> list(long order, boolean customer,
            int page, int size) {
        String visible = customer ? " AND q.IssuedAt IS NOT NULL"
                + " AND q.QuoteStatus IN"
                + " ('ISSUED','ACCEPTED','REJECTED','EXPIRED')" : "";

        return jdbc.query("SELECT q.QuoteID,q.RevisionID,q.QuoteNumber,"
                + "q.VersionNo,q.QuoteStatus,q.TotalQuotedAmount,q.ValidUntil"
                + " FROM dbo.Quote q JOIN dbo.OrderRevision r"
                + " ON r.RevisionID=q.RevisionID WHERE r.OrderID=:order"
                + visible + " ORDER BY q.QuoteID DESC OFFSET :offset ROWS"
                + " FETCH NEXT :size ROWS ONLY", args(order)
                        .addValue("offset", (long) page * size)
                        .addValue("size", size), (row, index) ->
                new QuoteSummary(row.getLong("QuoteID"),
                        row.getLong("RevisionID"), row.getString("QuoteNumber"),
                        row.getInt("VersionNo"),
                        QuoteStatus.valueOf(row.getString("QuoteStatus")),
                        row.getBigDecimal("TotalQuotedAmount") == null ? null
                                : row.getBigDecimal("TotalQuotedAmount")
                                        .toPlainString(),
                        time(row.getTimestamp("ValidUntil"))));
    }

    /**
     * @param order authorized parent
     * @param customer whether unpublished headers are hidden
     * @return matching header count
     */
    public long count(long order, boolean customer) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Quote q"
                + " JOIN dbo.OrderRevision r ON r.RevisionID=q.RevisionID"
                + " WHERE r.OrderID=:order" + (customer
                        ? " AND q.IssuedAt IS NOT NULL AND q.QuoteStatus IN"
                                + " ('ISSUED','ACCEPTED','REJECTED','EXPIRED')"
                        : ""), args(order), Long.class);
    }

    /** @return whether the additive F06 schema is deployed in this catalog */
    public boolean schemaReady() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN"
                + " COL_LENGTH('dbo.Quote','PricingSnapshotJSON') IS NOT NULL"
                + " AND COL_LENGTH('dbo.SupplierOffer','RetailUnitPrice')"
                + " IS NOT NULL THEN 1 ELSE 0 END",
                new MapSqlParameterSource(), Boolean.class));
    }

    /**
     * Prevents deactivation/role changes during a customer quotation command.
     * @param user trusted authenticated account key
     * @param role expected live role
     * @return whether the account remains active under the retained lock
     */
    public boolean activeAccount(long user, String role) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.[User] u WITH (UPDLOCK,HOLDLOCK)"
                + " JOIN dbo.[Role] r ON r.RoleID=u.RoleID"
                + " WHERE u.UserID=:user AND u.Status='ACTIVE'"
                + " AND r.RoleCode=:role", new MapSqlParameterSource("user",
                        user).addValue("role", role), Integer.class);

        return count != null && count == 1;
    }

    /**
     * Creates a new immutable preparation version and its priced rows.
     * @param order locked parent
     * @param staff live preparer StaffID
     * @param snapshot typed, validated captured prices
     * @return generated quote identity
     */
    public long prepare(long order, long staff, QuoteSnapshot snapshot) {
        var request = snapshot.request();
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX("
                + "q.VersionNo),0)+1 FROM dbo.Quote q"
                + " JOIN dbo.OrderRevision r ON r.RevisionID=q.RevisionID"
                + " WHERE r.OrderID=:order", args(order), Integer.class);
        BigDecimal total = snapshot.blockers().isEmpty() ? total(snapshot)
                : null;
        var params = args(order).addValue("revision", request.revisionId())
                .addValue("number", "Q-" + order + "-" + version)
                .addValue("version", version).addValue("staff", staff)
                .addValue("total", total)
                .addValue("expiry", utc(request.validUntil()))
                .addValue("termsVersion", request.termsVersion())
                .addValue("terms", request.terms())
                .addValue("cancellation", request.cancellationTerms())
                .addValue("snapshot", json.write(snapshot));
        Long id = jdbc.queryForObject("INSERT dbo.Quote (RevisionID,"
                + "QuoteNumber,VersionNo,Currency,TotalQuotedAmount,"
                + "QuoteStatus,"
                + "PreparedBy,SubmittedAt,ValidUntil,TermsVersion,TermsText,"
                + "CancellationTerms,PricingSnapshotJSON)"
                + " OUTPUT INSERTED.QuoteID VALUES (:revision,:number,"
                + ":version,'USD',:total,'DRAFT',:staff,SYSUTCDATETIME(),"
                + ":expiry,:termsVersion,:terms,:cancellation,:snapshot)",
                params, Long.class);

        if (id == null) {
            throw new IllegalStateException("Quote identity unavailable.");
        }
        List<MapSqlParameterSource> values = new java.util.ArrayList<>();

        for (var line : snapshot.lines()) {
            if (!"PRICED".equals(line.pricingStatus())) {
                continue;
            }
            var evidence = request.assessments().stream()
                    .filter(e -> e.code().name().equals(line.code()))
                    .findFirst().orElse(null);
            var tariff = snapshot.sources().stream().filter(s -> !s.offer()
                    && (s.code().equals(line.code())
                            || line.code().startsWith(s.code() + "_"))
                    && s.unit().equals(line.unit())).findFirst().orElse(null);

            values.add(new MapSqlParameterSource("quote", id)
                    .addValue("code", line.code())
                    .addValue("group", line.costGroup())
                    .addValue("description", line.description())
                    .addValue("unit", line.unit())
                    .addValue("quantity", new BigDecimal(line.quantity()))
                    .addValue("rate", new BigDecimal(line.unitRate()))
                    .addValue("amount", new BigDecimal(line.amount()))
                    .addValue("tariff", evidence == null
                            ? tariff == null ? null : tariff.id()
                            : evidence.tariffId())
                    .addValue("offer", evidence == null ? null
                            : evidence.offerId())
                    .addValue("inputs", json.write(java.util.Map.of(
                            "schemaVersion", 1, "line", line,
                            "revisionId", request.revisionId(),
                            "formula", "quantity * retailRate; HALF_UP(2)",
                            "quantitySources",
                            snapshot.calculationContext()))));
        }
        jdbc.batchUpdate("INSERT dbo.QuoteItem (QuoteID,TariffID,OfferID,"
                + "ServiceCode,CostCategory,Description,UnitCode,Quantity,"
                + "UnitPrice,Amount,CalculationInputs) VALUES (:quote,"
                + ":tariff,:offer,:code,:group,:description,:unit,:quantity,"
                + ":rate,:amount,:inputs)",
                values.toArray(MapSqlParameterSource[]::new));
        List<MapSqlParameterSource> schedule = request.paymentSchedule()
                .stream().map(s -> new MapSqlParameterSource("quote", id)
                        .addValue("sequence", s.sequenceNo())
                        .addValue("stage", s.paymentStage())
                        .addValue("amount", new BigDecimal(s.amountDue()))
                        .addValue("due", utc(s.dueAt()))).toList();

        jdbc.batchUpdate("INSERT dbo.QuotePaymentSchedule (QuoteID,SequenceNo,"
                + "PaymentStage,AmountDue,Currency,DueAt) VALUES (:quote,"
                + ":sequence,:stage,:amount,'USD',:due)",
                schedule.toArray(MapSqlParameterSource[]::new));
        jdbc.update("UPDATE q SET QuoteStatus='SUPERSEDED' FROM dbo.Quote q"
                + " JOIN dbo.OrderRevision r ON r.RevisionID=q.RevisionID"
                + " WHERE r.OrderID=:order AND q.QuoteID<>:id"
                + " AND q.QuoteStatus IN ('DRAFT','APPROVED','ISSUED')",
                args(order).addValue("id", id));
        return id;
    }

    /**
     * @param quote locked quote key
     * @param target service-owned next state
     * @param staff trusted StaffID or null for customer
     * @param customer trusted CustomerID or null for staff
     */
    public void transition(long quote, QuoteStatus target, Long staff,
            Long customer) {
        String extra = switch (target) {
            case APPROVED -> ",ApprovedBy=:staff,"
                    + "ApprovedAt=SYSUTCDATETIME()";
            case ISSUED -> ",IssuedBy=:staff,IssuedAt=SYSUTCDATETIME()";
            case ACCEPTED -> ",AcceptedByCustomerID=:customer,"
                    + "AcceptedAt=SYSUTCDATETIME()";
            default -> "";
        };

        jdbc.update("UPDATE dbo.Quote SET QuoteStatus=:state" + extra
                + " WHERE QuoteID=:quote",
                new MapSqlParameterSource("quote", quote)
                        .addValue("state", target.name())
                        .addValue("staff", staff)
                        .addValue("customer", customer));
    }

    /**
     * @param order parent scope
     * @param user authenticated UserID
     * @param operation retry UUID
     * @return successful immutable receipt envelope
     */
    public Optional<String> replay(long order, long user, UUID operation) {
        return jdbc.query("SELECT ChangeSnapshot FROM dbo.AuditEvent"
                + " WHERE EntityType='ORDER_QUOTATION' AND EntityID=:order"
                + " AND UserID=:user AND JSON_VALUE(ChangeSnapshot,"
                + "'$.operationId')=:operation", args(order)
                        .addValue("user", user)
                        .addValue("operation", operation.toString()),
                (row, index) -> row.getString(1)).stream().findFirst();
    }

    /**
     * @param order parent key
     * @param user trusted UserID
     * @param action server-owned action
     * @param envelope typed server receipt, no passwords/tokens
     */
    public void audit(long order, long user, String action, String envelope) {
        jdbc.update("INSERT dbo.AuditEvent (UserID,EntityType,EntityID,"
                + "ActionCode,ChangeSnapshot) VALUES (:user,"
                + "'ORDER_QUOTATION',:order,:action,:envelope)", args(order)
                        .addValue("user", user).addValue("action", action)
                        .addValue("envelope", envelope));
    }

    /**
     * @param staff trusted recorder StaffID
     * @param request validated supplier cost and separate retail price
     * @param now server UTC recording time, truncated to SQL seconds
     * @return immutable offer key; no reservation is confirmed
     */
    public long offer(long staff, SupplierOfferRequest request,
            OffsetDateTime now) {
        var args = new MapSqlParameterSource("revision", request.revisionId())
                .addValue("staff", staff)
                .addValue("provider", request.providerName())
                .addValue("reference", request.offerReference())
                .addValue("code", request.serviceCode().name())
                .addValue("unit", request.unitCode())
                .addValue("quantity", new BigDecimal(request.quantity()))
                .addValue("cost", new BigDecimal(request.purchaseUnitPrice()))
                .addValue("retail", new BigDecimal(request.retailUnitPrice()))
                .addValue("recordedAt", utc(now))
                .addValue("expiry", utc(request.validUntil()))
                .addValue("evidence", request.evidenceFileKey())
                .addValue("stall", request.includesStall())
                .addValue("includes", json.write(java.util.Map.of(
                        "schemaVersion", 1, "services",
                        request.includedCharges())));

        return jdbc.queryForObject("INSERT dbo.SupplierOffer (RevisionID,"
                + "ProviderName,OfferReference,ServiceCode,UnitCode,Quantity,"
                + "UnitPrice,RetailUnitPrice,Currency,IncludesStall,"
                + "IncludedCharges,ValidUntil,Status,EvidenceFileKey,"
                + "RecordedBy,RecordedAt) OUTPUT INSERTED.OfferID"
                + " VALUES (:revision,:provider,:reference,:code,:unit,"
                + ":quantity,:cost,:retail,'USD',:stall,:includes,:expiry,"
                + "'RECEIVED',:evidence,:staff,:recordedAt)", args,
                Long.class);
    }

    /**
     * @param quote quote key
     * @return captured payment obligations, not PaymentRequests or receipts
     */
    public List<QuoteSchedule> schedule(long quote) {
        return jdbc.query("SELECT SequenceNo,PaymentStage,AmountDue,DueAt"
                + " FROM dbo.QuotePaymentSchedule WHERE QuoteID=:id"
                + " ORDER BY SequenceNo", new MapSqlParameterSource("id",
                        quote), (row, index) -> new QuoteSchedule(
                                row.getInt("SequenceNo"),
                                row.getString("PaymentStage"),
                                row.getBigDecimal("AmountDue").toPlainString(),
                                time(row.getTimestamp("DueAt"))));
    }

    /**
     * @param id locked quote
     * @return persisted item total, not a client calculation
     */
    public BigDecimal itemTotal(long id) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(Amount),0)"
                + " FROM dbo.QuoteItem WHERE QuoteID=:id",
                new MapSqlParameterSource("id", id), BigDecimal.class);
    }

    /**
     * @param snapshot captured lines
     * @return exact sum after each line was rounded once
     */
    public static BigDecimal total(QuoteSnapshot snapshot) {
        return snapshot.lines().stream().filter(l -> l.amount() != null)
                .map(l -> new BigDecimal(l.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private MapSqlParameterSource args(long order) {
        return new MapSqlParameterSource("order", order);
    }

    private OffsetDateTime time(Timestamp timestamp) {
        return timestamp == null ? null
                : timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }

    private java.time.LocalDateTime utc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).withNano(0)
                .toLocalDateTime();
    }
}
