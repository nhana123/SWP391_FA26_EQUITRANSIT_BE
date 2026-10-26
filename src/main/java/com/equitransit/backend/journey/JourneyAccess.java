package com.equitransit.backend.journey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.exception.order.OrderException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import static com.equitransit.backend.journey.JourneyPolicy.require;

/** Live ownership, assigned-staff authorization and atomic retry receipts. */
@Component
public class JourneyAccess {
    private final JourneyStore db;
    private final CurrentActorService actors;
    private final ObjectMapper json;

    /** @param db store @param actors live identities @param json JSON codec */
    public JourneyAccess(JourneyStore db, CurrentActorService actors,
            ObjectMapper json) {
        this.db = db;
        this.actors = actors;
        this.json = json;
    }

    /** @return live actor */
    public CurrentActor actor() {
        return actors.current();
    }

    /** @param roles allowed roles @return authorized actor */
    public CurrentActor role(String... roles) {
        var actor = actor();
        if (!Set.of(roles).contains(actor.roleCode())) {
            throw new AccessDeniedException("Journey role required.");
        }
        return actor;
    }

    /** @param sql internal SELECT @param args parameters @return single row */
    public Map<String, Object> one(String sql, Map<String, ?> args) {
        var rows = db.rows(sql, args);
        if (rows.size() != 1) {
            throw new OrderException(HttpStatus.NOT_FOUND, "JOURNEY_NOT_FOUND",
                    "Journey record not available.");
        }
        return rows.getFirst();
    }

    /** @param id booking key @return owner/assigned-staff scoped booking */
    public Map<String, Object> booking(long id) {
        var row = one("SELECT * FROM journey.Booking WHERE BookingID=:id",
                Map.of("id", id));
        var a = actor();
        boolean valid = switch (a.roleCode()) {
            case "MANAGER" -> true;
            case "CUSTOMER" -> equal(row.get("CustomerID"), a.customerId());
            case "SPECIALIST" -> equal(row.get("SpecialistID"), a.staffId());
            case "COORDINATOR" -> equal(row.get("CoordinatorID"), a.staffId());
            case "DRIVER" -> !db.rows("SELECT m.ManifestID FROM journey.Trip t"
                    + " JOIN journey.Manifest m ON m.TripID=t.TripID"
                    + " JOIN journey.BookingHorse h"
                    + " ON h.BookingHorseID=m.BookingHorseID"
                    + " WHERE h.BookingID=:id AND t.DriverID=:staff",
                    Map.of("id", id, "staff", a.staffId())).isEmpty();
            default -> false;
        };
        if (!valid) {
            throw new AccessDeniedException("Booking access required.");
        }
        return row;
    }

    /** @param id trip key @return manager/assigned crew scoped trip */
    public Map<String, Object> trip(long id) {
        var a = role("MANAGER", "COORDINATOR", "DRIVER");
        var row = one("SELECT * FROM journey.Trip WHERE TripID=:id",
                Map.of("id", id));
        boolean valid = "MANAGER".equals(a.roleCode())
                || ("COORDINATOR".equals(a.roleCode())
                    && equal(row.get("CoordinatorID"), a.staffId()))
                || ("DRIVER".equals(a.roleCode())
                    && equal(row.get("DriverID"), a.staffId()));
        if (!valid) {
            throw new AccessDeniedException("Trip assignment required.");
        }
        return row;
    }

    /** @param row aggregate @param version expected version */
    public void version(Map<String, Object> row, long version) {
        require(number(row, "Version") == version, "JOURNEY_VERSION_CONFLICT");
    }

    /** @param value any serializable value @return canonical saved JSON */
    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Journey JSON unavailable.", e);
        }
    }

    /** @param action scoped action @param op retry ID @param request payload
     * @param work mutation under caller transaction @return exact receipt */
    public Map<String, Object> operation(String action, UUID op, Object request,
            Supplier<Map<String, Object>> work) {
        db.lock();
        long user = actor().userId();
        String hash = TokenTools.hash(action + ":" + encode(request));
        var args = new HashMap<String, Object>();
        args.put("user", user);
        args.put("op", op.toString());
        args.put("hash", hash);
        args.put("action", action);
        var rows = db.rows("SELECT * FROM journey.Operation WHERE UserID=:user"
                + " AND OperationID=:op", args);
        if (!rows.isEmpty()) {
            var prior = rows.getFirst();
            require(action.equals(prior.get("ActionCode"))
                    && hash.equals(prior.get("RequestHash")),
                    "JOURNEY_RETRY_CONFLICT");
            try {
                var receipt = json.readValue(prior.get("Receipt").toString(),
                        new TypeReference<Map<String, Object>>() { });
                if (receipt.containsKey("TripID")) {
                    trip(number(receipt, "TripID"));
                } else if (receipt.containsKey("BookingID")) {
                    booking(number(receipt, "BookingID"));
                }
                return receipt;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Invalid journey receipt.", e);
            }
        }
        var receipt = work.get();
        args.put("receipt", encode(receipt));
        db.write("INSERT journey.Operation(UserID,OperationID,ActionCode,"
                + "RequestHash,Receipt) VALUES (:user,:op,:action,:hash,"
                + ":receipt)", args);
        return receipt;
    }

    /** @param row result row @param key numeric column @return long value */
    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private boolean equal(Object value, Long expected) {
        return value instanceof Number n && expected != null
                && n.longValue() == expected;
    }
}
