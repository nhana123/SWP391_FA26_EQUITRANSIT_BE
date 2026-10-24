package com.equitransit.backend.journey;

import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Rejects accidental production activation or activation before migration. */
@Component
@ConditionalOnProperty(name = "app.journey.enabled", havingValue = "true")
public class JourneyActivation implements InitializingBean {
    private final JourneyStore db;

    /** @param db metadata reader */
    public JourneyActivation(JourneyStore db) {
        this.db = db;
    }

    @Override
    public void afterPropertiesSet() {
        var row = db.rows("SELECT DB_NAME() AS CatalogName,"
                + "OBJECT_ID('journey.Operation') AS OperationTable,"
                + "OBJECT_ID('journey.DepartureCheck') AS SafetyTable,"
                + "COL_LENGTH('journey.Booking','RouteReviewed')"
                + " AS RouteColumn",
                Map.of()).getFirst();
        if (!java.util.Set.of("EquiTransit_Demo", "EquiTransit_Test")
                .contains(row.get("CatalogName"))
                || row.get("OperationTable") == null
                || row.get("SafetyTable") == null
                || row.get("RouteColumn") == null) {
            throw new IllegalStateException(
                    "Journey v2 requires migrated Demo/Test database.");
        }
    }
}
