/* F14 close-order persistence; parent lock is owned by the service. */
package com.equitransit.backend.repository.handover;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Atomic lifecycle update and immutable audit-based retry lookup. */
@Repository
public class OrderClosureRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc shared transactional template */
    public OrderClosureRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param key trusted audit deduplication key @return original snapshot */
    public Optional<Map<String, Object>> replay(String key) {
        var rows = jdbc.queryForList("SELECT ChangeSnapshot FROM"
                + " dbo.AuditEvent WHERE EventKey=:key AND"
                + " EntityType='ORDER_ACTIVITY' AND ActionCode='CLOSED'",
                Map.of("key", key));
        return rows.stream().findFirst();
    }

    /** @param orderId locked delivered order @return whether changed */
    public boolean close(long orderId) {
        return jdbc.update("UPDATE dbo.TransportOrder SET OrderStatus="
                + "'CLOSED',UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:order"
                + " AND OrderStatus='DELIVERED'", Map.of("order", orderId))
                == 1;
    }

    /** @param orderId locked order @return persisted update timestamp */
    public OffsetDateTime updatedAt(long orderId) {
        var time = jdbc.queryForObject("SELECT UpdatedAt FROM"
                + " dbo.TransportOrder WHERE OrderID=:order",
                Map.of("order", orderId), Timestamp.class);
        return time.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }
}
