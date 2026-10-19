/*
 * ActualCostRepository
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Parameterized JDBC queries for ActualCost & margin reporting.
 */
package com.equitransit.backend.repository.handover;

import com.equitransit.backend.entity.handover.ActualCost;
import java.math.BigDecimal;
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
 * Data access operations for internal actual supplier costs on transport plans.
 */
@Repository
public class ActualCostRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates actual cost repository with datasource.
     * @param source shared datasource
     */
    public ActualCostRepository(DataSource source) {
        this.jdbc = new NamedParameterJdbcTemplate(source);
    }

    /**
     * Inserts new actual cost record and returns generated ID.
     * @param c actual cost entity
     * @return generated cost ID
     */
    public long insert(ActualCost c) {
        String sql = "INSERT INTO dbo.ActualCost "
                + "(PlanID, LegID, CostCode, Amount, Currency, IncurredAt, "
                + "Description, RecordedBy, EvidenceFileKey) "
                + "OUTPUT INSERTED.CostID VALUES "
                + "(:planId, :legId, :costCode, :amount, :currency, "
                + ":incurredAt, :description, :recordedBy, :evidenceFileKey)";

        var params = new MapSqlParameterSource()
                .addValue("planId", c.getPlanId())
                .addValue("legId", c.getLegId())
                .addValue("costCode", c.getCostCode())
                .addValue("amount", c.getAmount())
                .addValue("currency", c.getCurrency())
                .addValue("incurredAt", utc(c.getIncurredAt()))
                .addValue("description", c.getDescription())
                .addValue("recordedBy", c.getRecordedBy())
                .addValue("evidenceFileKey", c.getEvidenceFileKey());

        Long id = jdbc.queryForObject(sql, params, Long.class);
        if (id == null) {
            throw new IllegalStateException("Cost ID not generated");
        }
        c.setCostId(id);
        return id;
    }

    /**
     * Finds cost by ID.
     * @param costId PK
     * @return cost entity if found
     */
    public Optional<ActualCost> findById(long costId) {
        String sql = "SELECT * FROM dbo.ActualCost WHERE CostID=:id";
        var list = jdbc.query(sql, Map.of("id", costId), this::mapCost);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Finds all actual costs incurred on a plan.
     * @param planId plan FK
     * @return list of costs descending by incurred time
     */
    public List<ActualCost> findByPlanId(long planId) {
        String sql = "SELECT * FROM dbo.ActualCost WHERE PlanID=:planId "
                + "ORDER BY IncurredAt DESC";
        return jdbc.query(sql, Map.of("planId", planId), this::mapCost);
    }

    /**
     * Finds all actual costs allocated to a leg.
     * @param legId leg FK
     * @return list of costs descending by incurred time
     */
    public List<ActualCost> findByLegId(long legId) {
        String sql = "SELECT * FROM dbo.ActualCost WHERE LegID=:legId "
                + "ORDER BY IncurredAt DESC";
        return jdbc.query(sql, Map.of("legId", legId), this::mapCost);
    }

    /**
     * Calculates the sum of all actual costs on a plan.
     * @param planId plan FK
     * @return sum of amounts or 0.00
     */
    public BigDecimal findTotalActualCost(long planId) {
        String sql = "SELECT COALESCE(SUM(Amount), 0.00) FROM dbo.ActualCost "
                + "WHERE PlanID=:planId";
        BigDecimal total = jdbc.queryForObject(sql, Map.of("planId", planId),
                BigDecimal.class);

        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Counts distinct cost line-items on a plan.
     * @param planId plan FK
     * @return count of cost records
     */
    public int countByPlanId(long planId) {
        String sql = "SELECT COUNT(*) FROM dbo.ActualCost WHERE PlanID=:planId";
        Integer count = jdbc.queryForObject(sql, Map.of("planId", planId),
                Integer.class);

        return count != null ? count : 0;
    }

    /**
     * Finds total commercial quoted amount agreed for the order.
     * @param orderId parent order FK
     * @return total quoted amount or 0.00
     */
    public BigDecimal findOrderTotalQuotedAmount(long orderId) {
        String sql = "SELECT TOP 1 q.TotalQuotedAmount "
                + "FROM dbo.TransportOrder o "
                + "JOIN dbo.Quote q ON q.RevisionID=o.CurrentRevisionID "
                + "WHERE o.OrderID=:orderId ORDER BY q.VersionNo DESC";
        var list = jdbc.query(sql, Map.of("orderId", orderId), (rs, rowNum) ->
                rs.getBigDecimal("TotalQuotedAmount"));

        if (!list.isEmpty() && list.getFirst() != null) {
            return list.getFirst();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Verifies composite integrity: whether leg belongs to this plan.
     * Enforces FK_Figma_ActualCost_LegID: ([LegID], [PlanID]).
     * @param legId leg FK
     * @param planId plan FK
     * @return true if leg is part of plan
     */
    public boolean legBelongsToPlan(long legId, long planId) {
        String sql = "SELECT COUNT(*) FROM dbo.TripLeg "
                + "WHERE LegID=:legId AND PlanID=:planId";
        Integer count = jdbc.queryForObject(sql,
                Map.of("legId", legId, "planId", planId), Integer.class);

        return count != null && count > 0;
    }

    private ActualCost mapCost(ResultSet rs, int rowNum) throws SQLException {
        var c = new ActualCost();

        c.setCostId(rs.getLong("CostID"));
        c.setPlanId(rs.getLong("PlanID"));
        long legId = rs.getLong("LegID");
        c.setLegId(rs.wasNull() ? null : legId);
        c.setCostCode(rs.getString("CostCode"));
        c.setAmount(rs.getBigDecimal("Amount"));
        c.setCurrency(rs.getString("Currency"));
        c.setIncurredAt(time(rs.getTimestamp("IncurredAt")));
        c.setDescription(rs.getString("Description"));
        long recordedBy = rs.getLong("RecordedBy");
        c.setRecordedBy(rs.wasNull() ? null : recordedBy);
        c.setEvidenceFileKey(rs.getString("EvidenceFileKey"));
        return c;
    }

    private Timestamp utc(OffsetDateTime odt) {
        return odt == null ? null : Timestamp.valueOf(odt.toLocalDateTime());
    }

    private OffsetDateTime time(Timestamp ts) {
        return ts == null ? null
                : ts.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }
}
