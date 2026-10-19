/*
 * TripHandoverRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC queries for TripHandover custody records.
 */
package com.equitransit.backend.repository.handover;

import com.equitransit.backend.entity.handover.TripHandover;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Data access operations for TripHandover table and order delivery gates.
 */
@Repository
public class TripHandoverRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Receiver contact & authority snapshot from parent order.
     *
     * @param receiverName authorized receiver full name
     * @param receiverPhone contact phone number
     * @param receiverEmail contact email address
     * @param receiverAuthority authority role or stable delegation
     */
    public record OrderReceiverRow(
            String receiverName, String receiverPhone,
            String receiverEmail, String receiverAuthority) { }

    /**
     * Operational plan projection.
     *
     * @param planId plan PK
     * @param orderId parent order FK
     * @param planStatus lifecycle status
     * @param versionNo version sequence number
     */
    public record PlanRow(
            long planId, long orderId, String planStatus, int versionNo) { }

    /**
     * Creates handover repository with datasource.
     * @param source shared datasource
     */
    public TripHandoverRepository(DataSource source) {
        this.jdbc = new NamedParameterJdbcTemplate(source);
    }

    /**
     * Inserts new handover record and returns generated ID.
     * @param h handover entity
     * @return generated handover ID
     */
    public long insert(TripHandover h) {
        String sql = "INSERT INTO dbo.TripHandover "
                + "(LegID, RecordedBy, HandoverType, HandoverTime, "
                + "ReceivedByName, ReceiverAuthority, "
                + "ReceiverSignatureFileKey, HorseConditionStatus, Outcome, "
                + "ExceptionNotes, PhotoFileKey, CustomerConfirmedAt, "
                + "LocationDescription, CustomerDecision, CustomerFeedback, "
                + "CustomerRespondedAt, RespondedByCustomerID, "
                + "EvidenceFilesJSON) OUTPUT INSERTED.HandoverID VALUES "
                + "(:legId, :recordedBy, :handoverType, :handoverTime, "
                + ":receivedByName, :receiverAuthority, "
                + ":receiverSignatureFileKey, :horseConditionStatus, :outcome, "
                + ":exceptionNotes, :photoFileKey, :customerConfirmedAt, "
                + ":locationDescription, :customerDecision, :customerFeedback, "
                + ":customerRespondedAt, :respondedByCustomerId, "
                + ":evidenceFilesJson)";

        var params = new MapSqlParameterSource()
                .addValue("legId", h.getLegId())
                .addValue("recordedBy", h.getRecordedBy())
                .addValue("handoverType", h.getHandoverType())
                .addValue("handoverTime", utc(h.getHandoverTime()))
                .addValue("receivedByName", h.getReceivedByName())
                .addValue("receiverAuthority", h.getReceiverAuthority())
                .addValue("receiverSignatureFileKey",
                        h.getReceiverSignatureFileKey())
                .addValue("horseConditionStatus", h.getHorseConditionStatus())
                .addValue("outcome", h.getOutcome())
                .addValue("exceptionNotes", h.getExceptionNotes())
                .addValue("photoFileKey", h.getPhotoFileKey())
                .addValue("customerConfirmedAt",
                        utc(h.getCustomerConfirmedAt()))
                .addValue("locationDescription", h.getLocationDescription())
                .addValue("customerDecision", h.getCustomerDecision())
                .addValue("customerFeedback", h.getCustomerFeedback())
                .addValue("customerRespondedAt",
                        utc(h.getCustomerRespondedAt()))
                .addValue("respondedByCustomerId",
                        h.getRespondedByCustomerId())
                .addValue("evidenceFilesJson", h.getEvidenceFilesJson());

        Long id = jdbc.queryForObject(sql, params, Long.class);
        if (id == null) {
            throw new IllegalStateException("Handover ID not generated");
        }
        h.setHandoverId(id);
        return id;
    }

    /**
     * Finds handover by ID.
     * @param handoverId PK
     * @return handover entity if found
     */
    public Optional<TripHandover> findById(long handoverId) {
        String sql = "SELECT * FROM dbo.TripHandover WHERE HandoverID=:id";
        var list = jdbc.query(sql, Map.of("id", handoverId), this::mapHandover);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Finds handovers by leg ID.
     * @param legId transport leg FK
     * @return list of handovers descending by time
     */
    public List<TripHandover> findByLegId(long legId) {
        String sql = "SELECT * FROM dbo.TripHandover WHERE LegID=:legId "
                + "ORDER BY HandoverTime DESC";
        return jdbc.query(sql, Map.of("legId", legId), this::mapHandover);
    }

    /**
     * Finds handovers for an order across all plan legs.
     * @param orderId parent order FK
     * @return list of handovers descending by time
     */
    public List<TripHandover> findByOrderId(long orderId) {
        String sql = "SELECT h.* FROM dbo.TripHandover h "
                + "JOIN dbo.TripLeg l ON l.LegID=h.LegID "
                + "JOIN dbo.TransportPlan p ON p.PlanID=l.PlanID "
                + "WHERE p.OrderID=:orderId ORDER BY h.HandoverTime DESC";
        return jdbc.query(sql, Map.of("orderId", orderId), this::mapHandover);
    }

    /**
     * Updates customer delivery response & decision.
     * @param handoverId handover PK
     * @param decision customer decision
     * @param feedback feedback notes
     * @param respondedAt response UTC timestamp
     * @param customerId customer FK
     */
    public void updateCustomerDecision(long handoverId, String decision,
            String feedback, OffsetDateTime respondedAt, long customerId) {
        String sql = "UPDATE dbo.TripHandover SET CustomerDecision=:decision, "
                + "CustomerFeedback=:feedback, "
                + "CustomerRespondedAt=:respondedAt, "
                + "RespondedByCustomerID=:customerId, "
                + "CustomerConfirmedAt=:respondedAt WHERE HandoverID=:id";

        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", handoverId)
                .addValue("decision", decision)
                .addValue("feedback", feedback)
                .addValue("respondedAt", utc(respondedAt))
                .addValue("customerId", customerId));
    }

    /**
     * Finds receiver details from the transport order.
     * @param orderId order PK
     * @return receiver details if found
     */
    public Optional<OrderReceiverRow> findOrderReceiver(long orderId) {
        String sql = "SELECT ReceiverName, ReceiverPhone, ReceiverEmail, "
                + "ReceiverAuthority FROM dbo.TransportOrder "
                + "WHERE OrderID=:orderId";
        var list = jdbc.query(sql, Map.of("orderId", orderId), (rs, rowNum) ->
                new OrderReceiverRow(
                        rs.getString("ReceiverName"),
                        rs.getString("ReceiverPhone"),
                        rs.getString("ReceiverEmail"),
                        rs.getString("ReceiverAuthority")));

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Finds operational plan by ID.
     * @param planId plan PK
     * @return plan row if found
     */
    public Optional<PlanRow> findPlan(long planId) {
        String sql = "SELECT PlanID, OrderID, PlanStatus, VersionNo "
                + "FROM dbo.TransportPlan WHERE PlanID=:planId";
        var list = jdbc.query(sql, Map.of("planId", planId), (rs, rowNum) ->
                new PlanRow(
                        rs.getLong("PlanID"),
                        rs.getLong("OrderID"),
                        rs.getString("PlanStatus"),
                        rs.getInt("VersionNo")));

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Checks if this leg is the final leg of the plan by sequence number.
     * @param legId leg PK
     * @param planId plan PK
     * @return true if leg has maximum sequence number in plan
     */
    public boolean isLastLegOfPlan(long legId, long planId) {
        String sql = "SELECT CASE WHEN (SELECT SequenceNo FROM dbo.TripLeg "
                + "WHERE LegID=:legId) = MAX(SequenceNo) THEN 1 ELSE 0 END "
                + "FROM dbo.TripLeg WHERE PlanID=:planId";
        Integer result = jdbc.queryForObject(sql,
                Map.of("legId", legId, "planId", planId), Integer.class);

        return result != null && result == 1;
    }

    /**
     * Marks leg completed and records actual arrival.
     * @param legId leg PK
     * @param actualArrival actual arrival timestamp
     */
    public void completeLeg(long legId, OffsetDateTime actualArrival) {
        String sql = "UPDATE dbo.TripLeg SET LegStatus='COMPLETED', "
                + "ActualArrival=COALESCE(ActualArrival, :arrival) "
                + "WHERE LegID=:legId";
        jdbc.update(sql, Map.of("legId", legId, "arrival", utc(actualArrival)));
    }

    /**
     * Prepares next leg in sequence if in planned or scheduled state.
     * @param planId plan PK
     * @param nextSequenceNo next sequence number
     */
    public void activateNextLeg(long planId, int nextSequenceNo) {
        String sql = "UPDATE dbo.TripLeg SET LegStatus='READY' "
                + "WHERE PlanID=:planId AND SequenceNo=:seq "
                + "AND LegStatus IN ('PLANNED', 'SCHEDULED')";
        jdbc.update(sql, Map.of("planId", planId, "seq", nextSequenceNo));
    }

    /**
     * Marks plan completed.
     * @param planId plan PK
     */
    public void completePlan(long planId) {
        String sql = "UPDATE dbo.TransportPlan SET PlanStatus='COMPLETED' "
                + "WHERE PlanID=:planId";
        jdbc.update(sql, Map.of("planId", planId));
    }

    /**
     * Transitions transport order to DELIVERED status.
     * @param orderId order PK
     * @param deliveredAt delivery timestamp
     */
    public void deliverOrder(long orderId, OffsetDateTime deliveredAt) {
        String sql = "UPDATE dbo.TransportOrder SET OrderStatus='DELIVERED', "
                + "UpdatedAt=:deliveredAt WHERE OrderID=:orderId";
        jdbc.update(sql, Map.of("orderId", orderId,
                "deliveredAt", utc(deliveredAt)));
    }

    private TripHandover mapHandover(ResultSet rs, int rowNum)
            throws SQLException {
        var h = new TripHandover();

        h.setHandoverId(rs.getLong("HandoverID"));
        h.setLegId(rs.getLong("LegID"));
        h.setRecordedBy(rs.getLong("RecordedBy"));
        h.setHandoverType(rs.getString("HandoverType"));
        h.setHandoverTime(time(rs.getTimestamp("HandoverTime")));
        h.setReceivedByName(rs.getString("ReceivedByName"));
        h.setReceiverAuthority(rs.getString("ReceiverAuthority"));
        h.setReceiverSignatureFileKey(
                rs.getString("ReceiverSignatureFileKey"));
        h.setHorseConditionStatus(rs.getString("HorseConditionStatus"));
        h.setOutcome(rs.getString("Outcome"));
        h.setExceptionNotes(rs.getString("ExceptionNotes"));
        h.setPhotoFileKey(rs.getString("PhotoFileKey"));
        h.setCustomerConfirmedAt(
                time(rs.getTimestamp("CustomerConfirmedAt")));
        h.setLocationDescription(rs.getString("LocationDescription"));
        h.setCustomerDecision(rs.getString("CustomerDecision"));
        h.setCustomerFeedback(rs.getString("CustomerFeedback"));
        h.setCustomerRespondedAt(
                time(rs.getTimestamp("CustomerRespondedAt")));
        long custId = rs.getLong("RespondedByCustomerID");
        h.setRespondedByCustomerId(rs.wasNull() ? null : custId);
        h.setEvidenceFilesJson(rs.getString("EvidenceFilesJSON"));
        return h;
    }

    private Timestamp utc(OffsetDateTime odt) {
        return odt == null ? null : Timestamp.valueOf(odt.toLocalDateTime());
    }

    private OffsetDateTime time(Timestamp ts) {
        return ts == null ? null
                : ts.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }
}
