/*
 * ResourceRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Maintain sourced resources and transactional availability.
 */
package com.equitransit.backend.repository;

import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.PolicyState;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceVehicleView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceStallView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceSupplierData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceSupplierView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyView;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceWindow;
import com.equitransit.backend.entity.FitPolicyRecord;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceBlocker;
import com.equitransit.backend.exception.OrderException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL identifiers are enum-selected; all caller values are bound. */
@Repository
public class ResourceRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** @param source transaction-aware pool @param mapper typed JSON codec */
    public ResourceRepository(DataSource source, ObjectMapper mapper) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        jdbc = new NamedParameterJdbcTemplate(template);
        this.mapper = mapper;
    }

    /** @return complete, enabled F09 deployment, not just table presence */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT CASE WHEN "
                + "OBJECT_ID('dbo.ResourceOperation','U') IS NOT NULL AND "
                + "(SELECT COUNT(*) FROM sys.columns WHERE name='RecordVersion'"
                + " AND system_type_id=189 AND object_id IN ("
                + "OBJECT_ID('dbo.FleetVehicle'),"
                + "OBJECT_ID('dbo.StallEquipment'),OBJECT_ID('dbo.Supplier'),"
                + "OBJECT_ID('dbo.StallFitPolicy')))=4 AND "
                + "(SELECT COUNT(*) FROM sys.columns WHERE name='BufferMinutes'"
                + " AND object_id IN (OBJECT_ID('dbo.FleetVehicle'),"
                + "OBJECT_ID('dbo.StallEquipment')))=2 AND "
                + "(SELECT COUNT(*) FROM sys.triggers WHERE name IN "
                + "('TR_Resource_VehicleSchedule','TR_Resource_StallSchedule',"
                + "'TR_Resource_PlanActivation') AND is_disabled=0)=3 "
                + "AND EXISTS (SELECT 1 FROM "
                + "sys.check_constraints WHERE name='CK_ResourceOperation_JSON'"
                + " AND is_disabled=0 AND is_not_trusted=0) AND "
                + "(SELECT COUNT(*) FROM sys.check_constraints WHERE name IN "
                + "('CK_StallFitPolicy_Approval',"
                + "'CK_StallFitPolicy_Dimensions',"
                + "'CK_StallFitPolicy_Slots','CK_Resource_FleetVehicle_Buffer',"
                + "'CK_Resource_StallEquipment_Buffer') AND is_disabled=0 "
                + "AND is_not_trusted=0)=5 THEN 1 ELSE 0 END",
                Integer.class) == 1;
    }

    /** Retained transaction lock shared with reservation consumer/writers. */
    public void lock() {
        Integer result = jdbc.getJdbcTemplate().queryForObject(
                "DECLARE @r int; EXEC @r=sys.sp_getapplock "
                + "@Resource='EquiTransit:resources',@LockMode='Exclusive',"
                + "@LockOwner='Transaction',@LockTimeout=5000; SELECT @r",
                Integer.class);

        if (result == null || result < 0) {
            throw new OrderException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                    "Resource scheduling is busy; retry the same command.");
        }
    }

    /** @param kind fixed catalogue @return row count */
    public long count(ResourceKind kind) {
        return jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo." + table(kind), Long.class);
    }

    /** @param kind catalogue @param page zero-based @param size bounded
     * @param type explicit response @return page without N+1 reads */
    public <T> List<T> list(ResourceKind kind, int page, int size,
            Class<T> type) {
        return jdbc.query(select(kind) + " ORDER BY " + id(kind)
                + " DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                Map.of("offset", (long) page * size, "size", size),
                (row, index) -> type.cast(view(kind, row)));
    }

    /** @param kind catalogue @param key resource ID @param type response
     * @return locked resource, or a safe not-found error */
    public <T> T get(ResourceKind kind, long key, Class<T> type) {
        return jdbc.query(select(kind) + " WHERE " + id(kind) + "=:id",
                Map.of("id", key), (row, index) -> type.cast(view(kind, row)))
                .stream().findFirst().orElseThrow(() -> new OrderException(
                        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                        "The resource was not found."));
    }

    /** @param supplier optional owner/carrier @return active persisted state */
    public boolean activeSupplier(Long supplier) {
        return supplier == null || jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.Supplier WITH (HOLDLOCK) WHERE SupplierID=:id "
                + "AND IsActive=1", Map.of("id", supplier), Integer.class) == 1;
    }

    /** @param kind table @param key zero for creation @param values typed
     * @param version family version @param reviewer trusted StaffID
     * @param now server UTC @return persisted key */
    public long save(ResourceKind kind, long key, Object values, int version,
            Long reviewer, OffsetDateTime now) {
        var args = new org.springframework.jdbc.core.namedparam.
                MapSqlParameterSource("id", key);
        String columns;
        String bindings;

        if (values instanceof ResourceVehicleData v) {
            columns = "VehicleCode,PlateNumber,HorseCapacity,ClimateSystemType,"
                    + "OperationalStatus,OwnerSupplierID";
            bindings = ":code,:plate,:capacity,:climate,:status,:owner";
            args.addValue("code", v.vehicleCode())
                    .addValue("plate", v.plateNumber())
                    .addValue("capacity", v.horseCapacity())
                    .addValue("climate", v.climateSystemType())
                    .addValue("status", v.status().name())
                    .addValue("owner", v.ownerSupplierId());
            window(args, v.window());
        } else if (values instanceof ResourceStallData v) {
            columns = "StallCode,CapacitySlots,InternalHeightCm,"
                    + "InternalWidthCm,"
                    + "WeightLimitKg,EquipmentStatus,OwnerSupplierID";
            bindings = ":code,:capacity,:height,:width,:weight,:status,:owner";
            args.addValue("code", v.stallCode())
                    .addValue("capacity", v.capacitySlots())
                    .addValue("height", v.internalHeightCm())
                    .addValue("width", v.internalWidthCm())
                    .addValue("weight", v.weightLimitKg())
                    .addValue("status", v.status().name())
                    .addValue("owner", v.ownerSupplierId());
            window(args, v.window());
        } else if (values instanceof ResourceSupplierData v) {
            columns = "Name,ContactName,Email,Phone,ServicesOfferedJSON,"
                    + "IsActive";
            bindings = ":name,:contact,:email,:phone,:services,:active";
            args.addValue("name", v.name()).addValue("contact", v.contactName())
                    .addValue("email", v.email()).addValue("phone", v.phone())
                    .addValue("services", encode(v.services()))
                    .addValue("active", v.active());
        } else if (values instanceof ResourcePolicyData v) {
            columns = "PolicyCode,VersionNo,StallID,CarrierSupplierID,"
                    + "SlotQuantity,MaxWeightKg,MaxHeightCm,MaxWidthCm,"
                    + "EffectiveFrom,EffectiveTo,SourceURL,SourceFileKey,"
                    + "ReviewedBy,ReviewedAt,Status";
            bindings = ":code,:version,:stall,:carrier,:slots,:weight,:height,"
                    + ":width,:from,:to,:url,:file,:reviewer,:reviewed,:status";
            args.addValue("code", v.policyCode()).addValue("version", version)
                    .addValue("stall", v.stallId())
                    .addValue("carrier", v.carrierSupplierId())
                    .addValue("slots", v.slotQuantity())
                    .addValue("weight", v.maxWeightKg())
                    .addValue("height", v.maxHeightCm())
                    .addValue("width", v.maxWidthCm())
                    .addValue("from", utc(v.effectiveFrom()))
                    .addValue("to", utc(v.effectiveTo()))
                    .addValue("url", v.sourceUrl())
                    .addValue("file", v.sourceFileKey())
                    .addValue("reviewer", reviewer)
                    .addValue("reviewed", reviewer == null ? null : utc(now))
                    .addValue("status", v.status().name());
        } else {
            throw new IllegalArgumentException("Unsupported resource data.");
        }
        if (kind == ResourceKind.VEHICLE || kind == ResourceKind.STALL) {
            columns += ",MaintenanceFrom,MaintenanceTo,DocumentExpiresAt,"
                    + "BufferMinutes,AssetNotes";
            bindings += ",:maintenanceFrom,:maintenanceTo,:expiry,"
                    + ":buffer,:notes";
        }
        if (key == 0) {
            return jdbc.queryForObject("INSERT dbo." + table(kind) + " ("
                    + columns + ") OUTPUT INSERTED." + id(kind) + " VALUES ("
                    + bindings + ")", args, Long.class);
        }
        var names = columns.split(",");
        var params = bindings.split(",");
        var setters = new java.util.ArrayList<String>();

        for (int i = 0; i < names.length; i++) {
            setters.add(names[i] + "=" + params[i]);
        }
        jdbc.update("UPDATE dbo." + table(kind) + " SET "
                + String.join(",", setters) + " WHERE " + id(kind) + "=:id",
                args);
        return key;
    }

    /** @param code family @return next immutable policy version */
    public int nextVersion(String code) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(VersionNo),0)+1 FROM "
                + "dbo.StallFitPolicy WITH (UPDLOCK,HOLDLOCK) "
                + "WHERE PolicyCode=:code", Map.of("code", code),
                Integer.class);
    }

    /** @param id approved version @param code family to supersede */
    public void retirePolicies(long id, String code) {
        jdbc.update("UPDATE dbo.StallFitPolicy SET Status='INACTIVE' "
                + "WHERE PolicyCode=:code AND PolicyID<>:id AND "
                + "Status='APPROVED'", Map.of("code", code, "id", id));
    }

    /** @param user actor @param operation UUID @return original receipt row */
    public List<Map<String, Object>> operation(long user, UUID operation) {
        return jdbc.queryForList("SELECT RequestHash,ReceiptJSON FROM "
                + "dbo.ResourceOperation WHERE UserID=:user AND "
                + "OperationID=:operation", Map.of("user", user,
                        "operation", operation.toString()));
    }

    /** @param user actor @param operation UUID @param hash canonical input
     * @param receipt original typed result @param kind table @param key ID */
    public void finish(long user, UUID operation, String hash, Object receipt,
            ResourceKind kind, long key) {
        jdbc.update("INSERT dbo.ResourceOperation (UserID,OperationID,"
                + "RequestHash,ReceiptJSON) VALUES (:user,:operation,:hash,"
                + ":receipt)", Map.of("user", user, "operation",
                        operation.toString(), "hash", hash,
                        "receipt", encode(receipt)));
        jdbc.update("INSERT dbo.AuditEvent (UserID,EntityType,EntityID,"
                + "ActionCode,ChangeSnapshot,OccurredAt) VALUES (:user,"
                + "'RESOURCE_CATALOG',:id,'RESOURCE_WRITE',:snapshot,"
                + "SYSUTCDATETIME())", Map.of("user", user, "id", key,
                        "snapshot", encode(Map.of("schemaVersion", 1,
                                "kind", kind, "resourceId", key))));
    }

    /** @param stall optional exact equipment @param carrier optional carrier
     * @param slots requested configuration @param now calculation time
     * @param end journey horizon @return at most two ambiguous matches */
    public List<FitPolicyRecord> policies(long stall, Long carrier,
            java.math.BigDecimal slots, OffsetDateTime now,
            OffsetDateTime end) {
        var args = new org.springframework.jdbc.core.namedparam.
                MapSqlParameterSource("stall", stall)
                .addValue("carrier", carrier)
                .addValue("slots", slots).addValue("now", utc(now))
                .addValue("end", utc(end));

        return jdbc.query("SELECT TOP (2) PolicyID,PolicyCode,VersionNo,"
                + "SlotQuantity,MaxWeightKg,MaxHeightCm,MaxWidthCm FROM "
                + "dbo.StallFitPolicy WITH (HOLDLOCK) WHERE Status='APPROVED' "
                + "AND (StallID IS NULL OR StallID=:stall) AND "
                + "(CarrierSupplierID IS NULL OR CarrierSupplierID=:carrier) "
                + "AND SlotQuantity=:slots AND EffectiveFrom<=:now AND "
                + "(EffectiveTo IS NULL OR EffectiveTo>:end) AND "
                + "ReviewedBy IS NOT NULL AND ReviewedAt<=:now AND "
                + "(SourceURL LIKE 'https://%' OR SourceURL LIKE 'http://%' OR "
                + "LEN(LTRIM(RTRIM(SourceFileKey)))>0)", args,
                (r, i) -> new FitPolicyRecord(r.getLong("PolicyID"),
                        r.getString("PolicyCode"), r.getInt("VersionNo"),
                        r.getBigDecimal("SlotQuantity"),
                        r.getBigDecimal("MaxWeightKg"),
                        r.getBigDecimal("MaxHeightCm"),
                        r.getBigDecimal("MaxWidthCm")));
    }

    /** @param leg proposed replacement leg @param order authorized parent
     * @param revision current revision @return exact parent lineage */
    public boolean legBelongs(long leg, long order, long revision) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TripLeg l "
                + "JOIN dbo.TransportPlan p ON p.PlanID=l.PlanID "
                + "WHERE l.LegID=:leg AND p.OrderID=:order AND "
                + "p.RevisionID=:revision", Map.of("leg", leg, "order", order,
                        "revision", revision), Integer.class) == 1;
    }

    /** @param kind type @param key ID @param start start @param end end
     * @param buffer minimum gap @param exclude own replacement leg or zero
     * @return bounded blocker list, never other customer assignment data */
    public List<ResourceBlocker> scheduleBlockers(ResourceKind kind, long key,
                    OffsetDateTime start, OffsetDateTime end, int buffer,
                    long exclude) {
        String sql = kind == ResourceKind.VEHICLE
                ? "SELECT COALESCE(SUM(CASE WHEN a.PlannedDeparture IS NULL OR "
                        + "a.PlannedArrival IS NULL THEN 1 ELSE 0 END),0) "
                        + "AS Missing, COALESCE(SUM(CASE WHEN "
                        + "a.PlannedDeparture<:end AND DATEADD(minute,:buffer,"
                        + "a.PlannedArrival)>:start THEN 1 ELSE 0 END),0) "
                        + "AS Busy FROM dbo.TripLeg a JOIN dbo.TransportPlan p "
                        + "ON p.PlanID=a.PlanID WHERE a.VehicleID=:id AND "
                        + "a.LegStatus NOT IN ('CANCELLED','CANCELED',"
                        + "'REJECTED') AND a.LegID<>:exclude AND p.PlanStatus "
                        + "NOT IN ('CANCELLED','CANCELED','SUPERSEDED',"
                        + "'REJECTED')"
                : "SELECT COALESCE(SUM(CASE WHEN a.AssignedFrom IS NULL OR "
                        + "a.AssignedTo IS NULL THEN 1 ELSE 0 END),0) "
                        + "AS Missing, CASE WHEN COALESCE(SUM(CASE WHEN "
                        + "a.AssignedFrom<:end AND DATEADD(minute,:buffer,"
                        + "a.AssignedTo)>:start AND (:exclude<=0 OR "
                        + "a.SharingCode<>'SHARED' OR tl.LegID IS NULL OR "
                        + "tl.TransportMode<>'AIR' OR l.TransportMode<>'AIR' "
                        + "OR tl.StartPoint<>l.StartPoint OR tl.EndPoint<>"
                        + "l.EndPoint OR tl.PlannedFlightNumber IS NULL OR "
                        + "l.PlannedFlightNumber IS NULL OR "
                        + "tl.PlannedFlightNumber<>l.PlannedFlightNumber OR "
                        + "tl.PlannedDeparture<>l.PlannedDeparture OR "
                        + "tl.PlannedArrival<>l.PlannedArrival OR tp.OrderID="
                        + "p.OrderID OR (COALESCE(sc.SharingCode,'')<>'SHARED' "
                        + "AND COALESCE(osc.OptionCode,'') NOT IN "
                        + "('SHARED_ONE','WIDE'))) THEN 1 ELSE 0 END),0)>0 OR "
                        + "(:exclude>0 AND COALESCE(SUM(CASE WHEN "
                        + "a.AssignedFrom<:end AND DATEADD(minute,:buffer,"
                        + "a.AssignedTo)>:start THEN 1 ELSE 0 END),0)>0 AND "
                        + "COALESCE(SUM(CASE WHEN a.AssignedFrom<:end AND "
                        + "DATEADD(minute,:buffer,a.AssignedTo)>:start THEN "
                        + "a.SlotQuantity ELSE 0 END),0)+COALESCE(MAX("
                        + "COALESCE(sc.SlotQuantity, CASE WHEN osc.OptionCode="
                        + "'SHARED_ONE' THEN 1.0 WHEN osc.OptionCode='WIDE' "
                        + "THEN 1.5 ELSE 3.0 END)),3.0)>COALESCE(MAX("
                        + "se.CapacitySlots),0)) THEN 1 ELSE 0 END AS Busy "
                        + "FROM dbo.StallAssignment a JOIN dbo.TripLeg l ON "
                        + "l.LegID=a.LegID JOIN dbo.TransportPlan p ON "
                        + "p.PlanID=l.PlanID LEFT JOIN dbo.StallEquipment se "
                        + "ON se.StallID=:id LEFT JOIN dbo.TripLeg tl ON "
                        + "tl.LegID=:exclude AND :exclude>0 LEFT JOIN "
                        + "dbo.TransportPlan tp ON tp.PlanID=tl.PlanID LEFT "
                        + "JOIN dbo.OrderServiceChoice osc ON osc.RevisionID="
                        + "tp.RevisionID AND osc.ServiceCode='AIR_STALL' LEFT "
                        + "JOIN dbo.StallChoice sc ON sc.ChoiceID=osc.ChoiceID "
                        + "WHERE a.StallID=:id AND a.Status NOT IN "
                        + "('CANCELLED','CANCELED','INACTIVE') AND a.LegID<>"
                        + ":exclude AND p.PlanStatus NOT IN ('CANCELLED',"
                        + "'CANCELED','SUPERSEDED','REJECTED')";

        return jdbc.queryForObject(sql, Map.of("id", key, "exclude", exclude,
                "start", utc(start), "end", utc(end.plusMinutes(buffer)),
                "buffer", buffer), (r, index) -> {
                    var result = new java.util.ArrayList<ResourceBlocker>();

                    if (r.getInt("Missing") > 0) {
                        result.add(ResourceBlocker.SCHEDULE_PENDING);
                    }
                    if (r.getInt("Busy") > 0) {
                        result.add(ResourceBlocker.OVERLAP);
                    }
                    return List.copyOf(result);
                });
    }

    private String select(ResourceKind kind) {
        String columns = switch (kind) {
            case VEHICLE -> "VehicleID,VehicleCode,PlateNumber,HorseCapacity,"
                    + "ClimateSystemType,OperationalStatus,OwnerSupplierID,"
                    + "MaintenanceFrom,MaintenanceTo,DocumentExpiresAt,"
                    + "BufferMinutes,AssetNotes";
            case STALL -> "StallID,StallCode,CapacitySlots,InternalHeightCm,"
                    + "InternalWidthCm,WeightLimitKg,EquipmentStatus,"
                    + "OwnerSupplierID,MaintenanceFrom,MaintenanceTo,"
                    + "DocumentExpiresAt,BufferMinutes,AssetNotes";
            case SUPPLIER -> "SupplierID,Name,ContactName,Email,Phone,"
                    + "ServicesOfferedJSON,IsActive";
            case POLICY -> "PolicyID,PolicyCode,VersionNo,StallID,"
                    + "CarrierSupplierID,SlotQuantity,MaxWeightKg,MaxHeightCm,"
                    + "MaxWidthCm,EffectiveFrom,EffectiveTo,SourceURL,"
                    + "SourceFileKey,ReviewedBy,ReviewedAt,Status";
        };

        return "SELECT " + columns
                + ",RecordVersion "
                + "FROM dbo." + table(kind) + " WITH (HOLDLOCK)";
    }

    private Object view(ResourceKind kind, ResultSet r) throws SQLException {
        long key = r.getLong(id(kind));
        String version = java.util.HexFormat.of().formatHex(
                r.getBytes("RecordVersion"));

        return switch (kind) {
            case VEHICLE -> new ResourceVehicleView(key, version,
                    new ResourceVehicleData(r.getString("VehicleCode"),
                            r.getString("PlateNumber"),
                            r.getInt("HorseCapacity"),
                            r.getString("ClimateSystemType"),
                            assetState(r.getString("OperationalStatus")),
                            number(r, "OwnerSupplierID"), window(r)));
            case STALL -> new ResourceStallView(key, version,
                    new ResourceStallData(r.getString("StallCode"),
                            r.getBigDecimal("CapacitySlots"),
                            r.getBigDecimal("InternalHeightCm"),
                            r.getBigDecimal("InternalWidthCm"),
                            r.getBigDecimal("WeightLimitKg"),
                            assetState(r.getString("EquipmentStatus")),
                            number(r, "OwnerSupplierID"), window(r)));
            case SUPPLIER -> new ResourceSupplierView(key, version,
                    new ResourceSupplierData(r.getString("Name"),
                            r.getString("ContactName"), r.getString("Email"),
                            r.getString("Phone"), tags(r.getString(
                                    "ServicesOfferedJSON")),
                            r.getBoolean("IsActive")));
            case POLICY -> new ResourcePolicyView(key, version,
                    r.getInt("VersionNo"), number(r, "ReviewedBy"),
                    time(r, "ReviewedAt"), new ResourcePolicyData(
                            r.getString("PolicyCode"), number(r, "StallID"),
                            number(r, "CarrierSupplierID"),
                            r.getBigDecimal("SlotQuantity"),
                            r.getBigDecimal("MaxWeightKg"),
                            r.getBigDecimal("MaxHeightCm"),
                            r.getBigDecimal("MaxWidthCm"),
                            time(r, "EffectiveFrom"), time(r, "EffectiveTo"),
                            r.getString("SourceURL"),
                            r.getString("SourceFileKey"),
                            PolicyState.valueOf(r.getString("Status"))));
        };
    }

    private AssetState assetState(String value) {
        try {
            return AssetState.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "RESOURCE_LEGACY_DATA", "Review legacy asset status.");
        }
    }

    private ResourceWindow window(ResultSet r) throws SQLException {
        return new ResourceWindow(time(r, "MaintenanceFrom"),
                time(r, "MaintenanceTo"), time(r, "DocumentExpiresAt"),
                (Integer) r.getObject("BufferMinutes"),
                r.getString("AssetNotes"));
    }

    private void window(org.springframework.jdbc.core.namedparam.
            MapSqlParameterSource args, ResourceWindow window) {
        args.addValue("maintenanceFrom", utc(window.maintenanceFrom()))
                .addValue("maintenanceTo", utc(window.maintenanceTo()))
                .addValue("expiry", utc(window.documentExpiresAt()))
                .addValue("buffer", window.bufferMinutes())
                .addValue("notes", window.notes());
    }

    private List<String> tags(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json,
                    new TypeReference<List<String>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new OrderException(HttpStatus.CONFLICT,
                    "RESOURCE_LEGACY_DATA", "Review legacy supplier tags.");
        }
    }

    private String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalArgumentException("Cannot encode resource.");
        }
    }

    private Long number(ResultSet r, String field) throws SQLException {
        return r.getObject(field) == null ? null : r.getLong(field);
    }

    private OffsetDateTime time(ResultSet r, String field) throws SQLException {
        var value = r.getObject(field, LocalDateTime.class);

        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private LocalDateTime utc(OffsetDateTime time) {
        return time == null ? null : time.withOffsetSameInstant(ZoneOffset.UTC)
                .withNano(0).toLocalDateTime();
    }

    private String table(ResourceKind kind) {
        return switch (kind) {
            case VEHICLE -> "FleetVehicle";
            case STALL -> "StallEquipment";
            case SUPPLIER -> "Supplier";
            case POLICY -> "StallFitPolicy";
        };
    }

    private String id(ResourceKind kind) {
        return switch (kind) {
            case VEHICLE -> "VehicleID";
            case STALL -> "StallID";
            case SUPPLIER -> "SupplierID";
            case POLICY -> "PolicyID";
        };
    }
}
