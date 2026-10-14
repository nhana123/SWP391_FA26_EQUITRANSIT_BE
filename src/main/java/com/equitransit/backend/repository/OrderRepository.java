/*
 * OrderRepository
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Persist owner-scoped drafts and immutable revision records.
 */
package com.equitransit.backend.repository;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.equitransit.backend.entity.OrderHorseData;
import com.equitransit.backend.entity.OrderChangeRecord;
import com.equitransit.backend.entity.OrderRevisionRecord;
import com.equitransit.backend.entity.OrderServiceIntent;
import com.equitransit.backend.entity.OrderWriteData;
import com.equitransit.backend.entity.TransportOrderRecord;
import com.equitransit.backend.json.OrderJsonCodec;


/** Bound parameters and an order-row lock serialize all revision mutations. */
@Repository
public class OrderRepository {

    private static final String COLUMNS = "OrderID, CustomerID, OrderNumber,"
            + " OrderStatus, Currency, RecordVersion, CurrentRevisionID,"
            + " CurrentPlanID, CreatedAt, UpdatedAt, DraftPayloadJSON";
    private static final String REVISION_COLUMNS = "RevisionID, OrderID,"
            + " RevisionNo, OrderSnapshot, ChangeReason, SubmittedAt";
    private static final String CHANGE_COLUMNS = "ChangeRequestID, OrderID,"
            + " BaseRevisionID, Status, CreatedAt";

    private final NamedParameterJdbcTemplate jdbc;
    private final OrderJsonCodec json;

    /**
     * Creates the SQL persistence adapter.
     * @param jdbc the transaction-aware SQL template
     * @param json the shared versioned codec
     */
    public OrderRepository(NamedParameterJdbcTemplate jdbc,
            OrderJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Reads only the authenticated owner's order.
     * @param id the order key
     * @param owner the trusted customer key
     * @param lock whether the service is about to mutate this order
     * @return the owned row, otherwise empty
     */
    public Optional<TransportOrderRecord> findOwned(long id, long owner,
            boolean lock) {
        String hint = lock ? " WITH (UPDLOCK,HOLDLOCK)" : "";

        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.TransportOrder"
                + hint + " WHERE OrderID=:id AND CustomerID=:owner",
                keys(id, owner), (row, index) -> order(row))
                .stream().findFirst();
    }

    /**
     * Lists current request metadata for the trusted owner.
     * @param owner the customer key
     * @param page the zero-based page
     * @param size the bounded page size
     * @return an owner-filtered page
     */
    public List<TransportOrderRecord> list(long owner, int page, int size) {
        return jdbc.query("SELECT " + COLUMNS
                + " FROM dbo.TransportOrder WHERE CustomerID=:owner"
                + " ORDER BY OrderID DESC OFFSET :offset ROWS"
                + " FETCH NEXT :size ROWS ONLY",
                new MapSqlParameterSource().addValue("owner", owner)
                        .addValue("offset", (long) page * size)
                        .addValue("size", size),
                (row, index) -> order(row));
    }

    /**
     * Counts only the owner's current requests.
     * @param owner the trusted customer key
     * @return the matching count
     */
    public long count(long owner) {
        Long total = jdbc.queryForObject("SELECT COUNT_BIG(*)"
                + " FROM dbo.TransportOrder WHERE CustomerID=:owner",
                new MapSqlParameterSource("owner", owner), Long.class);

        return total == null ? 0 : total;
    }

    /**
     * Creates a draft without a fake submitted revision or horse snapshot.
     * @param owner the trusted customer key
     * @param number the server-generated unique request number
     * @param data the normalized typed write data
     * @return the generated key
     */
    public long create(long owner, String number, OrderWriteData data) {
        String sql = "INSERT dbo.TransportOrder (CustomerID,OrderNumber,"
                + "OrderStatus,Currency,"
                + "OriginCountryCode,DestinationCountryCode,OriginAddress,"
                + "DestinationAddress,OriginTimezone,DestinationTimezone,"
                + "PreferredDepartureAt,DeliveryDeadline,DateFlexibilityNotes,"
                + "ImportType,ReceiverName,ReceiverPhone,ReceiverEmail,"
                + "ReceiverAuthority,EventStartAt,EventName,EventVenue,"
                + "EventTimezone,TransitAirportsJSON,"
                + "DraftPayloadJSON,UpdatedAt)"
                + " OUTPUT INSERTED.OrderID VALUES (:owner,:number,'DRAFT',"
                + "'USD',:originCountryCode,:destinationCountryCode,"
                + ":originAddress,:destinationAddress,:originTimezone,"
                + ":destinationTimezone,:preferredDepartureAt,"
                + ":deliveryDeadline,:dateFlexibilityNotes,:importType,"
                + ":receiverName,:receiverPhone,:receiverEmail,"
                + ":receiverAuthority,:eventStartAt,:eventName,:eventVenue,"
                + ":eventTimezone,:transitAirportsJson,:draftPayloadJson,"
                + "SYSUTCDATETIME())";
        Long id = jdbc.queryForObject(sql, values(data)
                .addValue("owner", owner).addValue("number", number),
                Long.class);

        if (id == null) {
            throw new IllegalStateException("Order identity unavailable.");
        }
        return id;
    }

    /**
     * Replaces current request data using the observed rowversion.
     * @param order the locked owner-scoped row
     * @param data the normalized write data
     * @param status the service-owned state
     * @param revision the exact current submitted revision, or null for draft
     * @return whether the atomic version comparison succeeded
     */
    public boolean replace(TransportOrderRecord order, OrderWriteData data,
            String status, Long revision) {
        String sql = "UPDATE dbo.TransportOrder SET "
                + "OriginCountryCode=:originCountryCode,"
                + "DestinationCountryCode=:destinationCountryCode,"
                + "OriginAddress=:originAddress,"
                + "DestinationAddress=:destinationAddress,"
                + "OriginTimezone=:originTimezone,"
                + "DestinationTimezone=:destinationTimezone,"
                + "PreferredDepartureAt=:preferredDepartureAt,"
                + "DeliveryDeadline=:deliveryDeadline,"
                + "DateFlexibilityNotes=:dateFlexibilityNotes,"
                + "ImportType=:importType,ReceiverName=:receiverName,"
                + "ReceiverPhone=:receiverPhone,ReceiverEmail=:receiverEmail,"
                + "ReceiverAuthority=:receiverAuthority,"
                + "EventStartAt=:eventStartAt,EventName=:eventName,"
                + "EventVenue=:eventVenue,EventTimezone=:eventTimezone,"
                + "TransitAirportsJSON=:transitAirportsJson,"
                + "DraftPayloadJSON=:draftPayloadJson,OrderStatus=:status,"
                + "CurrentRevisionID=:revision,UpdatedAt=SYSUTCDATETIME()"
                + " WHERE OrderID=:id AND CustomerID=:owner"
                + " AND RecordVersion=:version";

        return jdbc.update(sql, values(data)
                .addValue("id", order.orderId())
                .addValue("owner", order.customerId())
                .addValue("version", HexFormat.of().parseHex(
                        order.recordVersion()))
                .addValue("status", status).addValue("revision", revision))
                == 1;
    }

    /**
     * Allocates a sequence under the parent lock, never an operating pointer.
     * @param id the locked order key
     * @return the next history sequence
     */
    public int nextRevision(long id) {
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(RevisionNo),"
                + "0)+1 FROM dbo.OrderRevision WHERE OrderID=:id",
                new MapSqlParameterSource("id", id), Integer.class);

        if (next == null) {
            throw new IllegalStateException("Revision number unavailable.");
        }
        return next;
    }

    /**
     * Inserts immutable submitted content under the parent order lock.
     * @param id the locked order key
     * @param number the allocated revision sequence
     * @param snapshot the complete typed snapshot JSON
     * @param reason the optional first-submit or required revise reason
     * @param submittedAt the UTC submission time
     * @return the generated revision key
     */
    public long insertRevision(long id, int number, String snapshot,
            String reason, OffsetDateTime submittedAt) {
        Long revision = jdbc.queryForObject("INSERT dbo.OrderRevision"
                + " (OrderID,RevisionNo,OrderSnapshot,ChangeReason,SubmittedAt)"
                + " OUTPUT INSERTED.RevisionID VALUES"
                + " (:id,:number,:snapshot,:reason,:at)",
                new MapSqlParameterSource("id", id)
                        .addValue("number", number)
                        .addValue("snapshot", snapshot)
                        .addValue("reason", reason)
                        .addValue("at", submittedAt.toLocalDateTime()),
                Long.class);

        if (revision == null) {
            throw new IllegalStateException("Revision identity unavailable.");
        }
        return revision;
    }

    /**
     * Reads a revision only through its owned parent request.
     * @param id the already authorized order key
     * @param revision the revision key
     * @return the matching immutable row
     */
    public Optional<OrderRevisionRecord> revision(long id, long revision) {
        return jdbc.query("SELECT " + REVISION_COLUMNS
                + " FROM dbo.OrderRevision WHERE OrderID=:id"
                + " AND RevisionID=:revision",
                new MapSqlParameterSource("id", id)
                        .addValue("revision", revision),
                (row, index) -> revision(row)).stream().findFirst();
    }

    /**
     * Reads bounded history without confusing sequence with current plan.
     * @param id the already authorized order key
     * @param page the zero-based page
     * @param size the page size
     * @return the immutable revision page
     */
    public List<OrderRevisionRecord> revisions(long id, int page, int size) {
        return jdbc.query("SELECT " + REVISION_COLUMNS
                + " FROM dbo.OrderRevision WHERE OrderID=:id"
                + " ORDER BY RevisionNo DESC OFFSET :offset ROWS"
                + " FETCH NEXT :size ROWS ONLY",
                new MapSqlParameterSource("id", id)
                        .addValue("offset", (long) page * size)
                        .addValue("size", size),
                (row, index) -> revision(row));
    }

    /**
     * Finds a successful retry under the parent lock.
     * @param id the locked authorized order
     * @param operation the client-generated retry identifier
     * @return the existing revision for this operation
     */
    public Optional<OrderRevisionRecord> operation(long id, UUID operation) {
        return jdbc.query("SELECT " + REVISION_COLUMNS
                + " FROM dbo.OrderRevision WHERE OrderID=:id"
                + " AND JSON_VALUE(OrderSnapshot,'$.operationId')=:operation",
                new MapSqlParameterSource("id", id)
                        .addValue("operation", operation.toString()),
                (row, index) -> revision(row)).stream().findFirst();
    }

    /**
     * Maintains one current horse row; history lives in immutable snapshots.
     * @param id the locked order key
     * @param horse the captured owned horse profile
     */
    public void saveHorse(long id, OrderHorseData horse) {
        MapSqlParameterSource args = new MapSqlParameterSource("id", id)
                .addValue("horse", horse.horseId())
                .addValue("chip", horse.microchipId())
                .addValue("sex", horse.sexCode())
                .addValue("weight", horse.weightKg())
                .addValue("height", horse.heightCm())
                .addValue("width", horse.bodyWidthCm())
                .addValue("at", horse.measuredAt() == null ? null
                        : horse.measuredAt())
                .addValue("needs", horse.specialNeeds());
        String columns = "HorseID=:horse,SnapshotMicrochip=:chip,"
                + "SnapshotSexCode=:sex,SnapshotWeightKg=:weight,"
                + "SnapshotHeightCm=:height,SnapshotBodyWidthCm=:width,"
                + "SnapshotMeasuredAt=:at,SpecialNeeds=:needs";
        int updated = jdbc.update("UPDATE dbo.TransportOrderDetail SET "
                + columns + " WHERE OrderID=:id", args);

        if (updated == 0) {
            jdbc.update("INSERT dbo.TransportOrderDetail (OrderID,HorseID,"
                    + "SnapshotMicrochip,SnapshotSexCode,SnapshotWeightKg,"
                    + "SnapshotHeightCm,SnapshotBodyWidthCm,SnapshotMeasuredAt,"
                    + "SpecialNeeds) VALUES"
                    + " (:id,:horse,:chip,:sex,:weight,:height,"
                    + ":width,:at,:needs)",
                    args);
        }
    }

    /**
     * Writes requested service intent with no rate or approval claim.
     * @param revision the new revision key
     * @param intents the server-normalized requested options
     */
    public void saveIntents(long revision, List<OrderServiceIntent> intents) {
        for (OrderServiceIntent intent : intents) {
            jdbc.update("INSERT dbo.OrderServiceChoice (RevisionID,ServiceCode,"
                    + "OptionCode,ArrangedBy,SelectionInputsJSON,ProviderName,"
                    + "ProviderContact) VALUES"
                    + " (:revision,:code,:option,:by,:inputs,:name,:contact)",
                    new MapSqlParameterSource("revision", revision)
                            .addValue("code", intent.serviceCode())
                            .addValue("option", intent.optionCode())
                            .addValue("by", intent.arrangedBy())
                            .addValue("inputs", json.write(
                                    intent.requestedInputs()))
                            .addValue("name", intent.providerName())
                            .addValue("contact", intent.providerContact()));
        }
    }

    /**
     * Records a controlled, unapplied proposal under the order lock.
     * @param order the locked owned order
     * @param user the trusted authenticated account key
     * @param reason the validated reason
     * @param proposed the versioned proposed JSON
     * @return the generated request key
     */
    public long insertChange(TransportOrderRecord order, long user,
            String reason, String proposed) {
        Long id = jdbc.queryForObject("INSERT dbo.OrderChangeRequest"
                + " (OrderID,BaseRevisionID,RequestedBy,ChangeType,Reason,"
                + "RequestedChangesJSON,Status) OUTPUT INSERTED.ChangeRequestID"
                + " VALUES (:id,:revision,:user,'MODIFY',"
                + ":reason,:json,'PENDING')",
                new MapSqlParameterSource("id", order.orderId())
                        .addValue("revision", order.currentRevisionId())
                        .addValue("user", user).addValue("reason", reason)
                        .addValue("json", proposed), Long.class);

        if (id == null) {
            throw new IllegalStateException("Change identity unavailable.");
        }
        return id;
    }

    /**
     * Finds a change request by operation or generated key.
     * @param order the authorized parent key
     * @param operation the retry key, null when reading by ID
     * @param id the generated key, null when reading a retry
     * @return its safe owner-visible receipt
     */
    public Optional<OrderChangeRecord> change(long order, UUID operation,
            Long id) {
        String filter = operation == null ? " AND ChangeRequestID=:change"
                : " AND JSON_VALUE(RequestedChangesJSON,'$.operationId')=:op";

        return jdbc.query("SELECT " + CHANGE_COLUMNS
                + " FROM dbo.OrderChangeRequest WHERE OrderID=:id" + filter,
                new MapSqlParameterSource("id", order)
                        .addValue("change", id)
                        .addValue("op", operation == null ? null
                                : operation.toString()),
                (row, index) -> new OrderChangeRecord(
                        row.getLong("ChangeRequestID"), row.getLong("OrderID"),
                        nullableLong(row, "BaseRevisionID"),
                        row.getString("Status"), time(row, "CreatedAt")))
                .stream().findFirst();
    }

    /**
     * Returns only a hash for safe idempotency checking of a proposal.
     * @param change the already authorized change request key
     * @return the stored canonical command fingerprint
     */
    public String changeHash(long change) {
        return jdbc.queryForObject("SELECT JSON_VALUE(RequestedChangesJSON,"
                + "'$.requestHash') FROM dbo.OrderChangeRequest"
                + " WHERE ChangeRequestID=:id",
                new MapSqlParameterSource("id", change), String.class);
    }

    /**
     * Checks pending proposals under the parent lock.
     * @param order the locked order key
     * @return whether a proposal is already awaiting handling
     */
    public boolean hasPendingChange(long order) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*)"
                + " FROM dbo.OrderChangeRequest WHERE OrderID=:id"
                + " AND Status='PENDING'",
                new MapSqlParameterSource("id", order), Integer.class);

        return count != null && count > 0;
    }

    private MapSqlParameterSource keys(long id, long owner) {
        return new MapSqlParameterSource("id", id).addValue("owner", owner);
    }

    private MapSqlParameterSource values(OrderWriteData data) {
        return new MapSqlParameterSource()
                .addValue("originCountryCode", data.originCountryCode())
                .addValue("destinationCountryCode",
                        data.destinationCountryCode())
                .addValue("originAddress", data.originAddress())
                .addValue("destinationAddress", data.destinationAddress())
                .addValue("originTimezone", data.originTimezone())
                .addValue("destinationTimezone", data.destinationTimezone())
                .addValue("preferredDepartureAt", data.preferredDepartureAt())
                .addValue("deliveryDeadline", data.deliveryDeadline())
                .addValue("dateFlexibilityNotes", data.dateFlexibilityNotes())
                .addValue("importType", data.importType())
                .addValue("receiverName", data.receiverName())
                .addValue("receiverPhone", data.receiverPhone())
                .addValue("receiverEmail", data.receiverEmail())
                .addValue("receiverAuthority", data.receiverAuthority())
                .addValue("eventStartAt", data.eventStartAt())
                .addValue("eventName", data.eventName())
                .addValue("eventVenue", data.eventVenue())
                .addValue("eventTimezone", data.eventTimezone())
                .addValue("transitAirportsJson", data.transitAirportsJson())
                .addValue("draftPayloadJson", data.draftPayloadJson());
    }

    private TransportOrderRecord order(ResultSet row) throws SQLException {
        return new TransportOrderRecord(row.getLong("OrderID"),
                row.getLong("CustomerID"), row.getString("OrderNumber"),
                row.getString("OrderStatus"), row.getString("Currency"),
                HexFormat.of().formatHex(row.getBytes("RecordVersion")),
                nullableLong(row, "CurrentRevisionID"),
                nullableLong(row, "CurrentPlanID"), time(row, "CreatedAt"),
                time(row, "UpdatedAt"), row.getString("DraftPayloadJSON"));
    }

    private OrderRevisionRecord revision(ResultSet row) throws SQLException {
        return new OrderRevisionRecord(row.getLong("RevisionID"),
                row.getLong("OrderID"), row.getInt("RevisionNo"),
                row.getString("OrderSnapshot"), row.getString("ChangeReason"),
                time(row, "SubmittedAt"));
    }

    private Long nullableLong(ResultSet row, String column)
            throws SQLException {
        long value = row.getLong(column);

        return row.wasNull() ? null : value;
    }

    private OffsetDateTime time(ResultSet row, String column)
            throws SQLException {
        return row.getTimestamp(column) == null ? null
                : row.getTimestamp(column).toLocalDateTime()
                        .atOffset(ZoneOffset.UTC);
    }
}
