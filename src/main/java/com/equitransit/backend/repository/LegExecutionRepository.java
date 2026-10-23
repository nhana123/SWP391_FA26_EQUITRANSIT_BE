package com.equitransit.backend.repository;


import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** Parameterized F12 storage; callers hold the TransportOrder parent lock. */
@Repository
public class LegExecutionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** @param source shared transaction-aware datasource */
    public LegExecutionRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return actual table, visibility, composite FK and append-only gates */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT CASE WHEN "
                + "OBJECT_ID('dbo.LegExecutionOperation','U') IS NOT NULL "
                + "AND COL_LENGTH('dbo.HorseWelfareLog','RecordedAt') "
                + "IS NOT NULL AND COL_LENGTH('dbo.HorseWelfareLog',"
                + "'SourceCode') IS NOT NULL AND COL_LENGTH("
                + "'dbo.HorseWelfareLog','SourceReference') IS NOT NULL AND "
                + "(SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id IN (OBJECT_ID('dbo.HorseWelfareLog'),"
                + "OBJECT_ID('dbo.LegMilestoneLog')) AND name IN "
                + "('PlanID','IsCustomerVisible','PublicNote'))=6 AND "
                + "(SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN "
                + "('FK_Figma_LegMilestoneLog_LegID',"
                + "'FK_Figma_HorseWelfareLog_LegID',"
                + "'FK_Figma_HorseWelfareLog_SupersedesLogID') "
                + "AND is_disabled=0 AND is_not_trusted=0)=3 AND "
                + "(SELECT COUNT(*) FROM sys.triggers WHERE name IN "
                + "('TR_Execution_WelfareImmutable',"
                + "'TR_Execution_MilestoneImmutable') AND is_disabled=0)=2 "
                + "AND EXISTS(SELECT 1 FROM sys.indexes WHERE object_id="
                + "OBJECT_ID('dbo.HorseWelfareLog') AND name="
                + "'UQ_Execution_WelfareCorrection' AND is_unique=1 "
                + "AND is_disabled=0) AND EXISTS(SELECT 1 FROM "
                + "sys.check_constraints WHERE name="
                + "'CK_LegExecutionOperation_JSON' AND is_disabled=0 "
                + "AND is_not_trusted=0) "
                + "THEN 1 ELSE 0 END", Integer.class) == 1;
    }

    /** @param key internal statement @param args bound values @return rows */
    public List<Map<String, Object>> rows(String key,
            Map<String, ?> args) {
        return jdbc.queryForList(sql(key), args);
    }

    /** @param key internal statement @param args bound values @return count */
    public int update(String key, Map<String, ?> args) {
        return jdbc.update(sql(key), args);
    }

    /** @param key internal statement @param args bound values @return key */
    public long number(String key, Map<String, ?> args) {
        return jdbc.queryForObject(sql(key), args, Long.class);
    }

    /** @param time timezone-aware instant @return SQL datetime2 UTC */
    public static Timestamp utc(OffsetDateTime time) {
        return time == null ? null : Timestamp.valueOf(time
                .withOffsetSameInstant(java.time.ZoneOffset.UTC)
                .toLocalDateTime());
    }

    private String sql(String key) {
        return switch (key) {
            case "scope" -> "SELECT o.CustomerID,o.CurrentPlanID,p.RevisionID,"
                + "p.PlanStatus FROM dbo.TransportOrder o JOIN "
                + "dbo.TransportPlan p WITH (UPDLOCK,HOLDLOCK) ON "
                + "p.PlanID=o.CurrentPlanID AND p.OrderID=o.OrderID "
                + "WHERE o.OrderID=:order";
            case "leg" -> "SELECT "
                + "LegID,PlanID,SequenceNo,TransportMode,LegStatus,"
                + "ActualDeparture,ActualArrival FROM dbo.TripLeg "
                + "WITH (UPDLOCK,HOLDLOCK) WHERE PlanID=:plan AND LegID=:leg";
            case "driver", "driverHistory" -> "SELECT COUNT(*) FROM "
                + "dbo.TripAssignment "
                + "WITH (UPDLOCK,HOLDLOCK) WHERE LegID=:leg AND StaffID=:staff "
                + "AND RoleInTrip='DRIVER' AND Status IN "
                + (key.equals("driverHistory")
                    ? "('CONFIRMED','ACTIVE','COMPLETED')"
                    : "('CONFIRMED','ACTIVE')");
            case "driverRead" -> "SELECT COUNT(*) FROM dbo.TripAssignment a "
                + "JOIN dbo.TripLeg l ON a.LegID=l.LegID WHERE "
                + "l.PlanID=:plan AND l.TransportMode='ROAD' "
                + "AND a.StaffID=:staff AND a.RoleInTrip='DRIVER' "
                + "AND a.Status IN ('CONFIRMED','ACTIVE','COMPLETED')";
            case "previous" -> "SELECT ActualArrival,LegStatus FROM "
                + "dbo.TripLeg "
                + "WITH (UPDLOCK,HOLDLOCK) WHERE PlanID=:plan "
                + "AND SequenceNo=:previous";
            case "operation" -> "SELECT RequestHash,ReceiptJSON FROM "
                + "dbo.LegExecutionOperation WHERE OrderID=:order "
                + "AND UserID=:user AND OperationID=:operation";
            case "save" -> "INSERT dbo.LegExecutionOperation "
                + "(OrderID,UserID,OperationID,RequestHash,ReceiptJSON) "
                + "VALUES(:order,:user,:operation,:hash,:receipt)";
            case "start" -> "UPDATE dbo.TripLeg SET LegStatus='IN_TRANSIT',"
                + "ActualDeparture=:at WHERE LegID=:leg AND PlanID=:plan "
                + "AND LegStatus='CONFIRMED' AND ActualDeparture IS NULL";
            case "complete" -> "UPDATE dbo.TripLeg SET LegStatus='COMPLETED',"
                + "ActualArrival=:at WHERE LegID=:leg AND PlanID=:plan "
                + "AND LegStatus='IN_TRANSIT' AND ActualArrival IS NULL";
            case "touch" -> "UPDATE dbo.TransportOrder SET "
                + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:order";
            case "assignmentStart" -> "UPDATE dbo.TripAssignment SET "
                + "Status='ACTIVE' WHERE LegID=:leg AND Status='CONFIRMED'";
            case "assignmentEnd" -> "UPDATE dbo.TripAssignment SET "
                + "Status='COMPLETED' WHERE LegID=:leg "
                + "AND Status IN ('CONFIRMED','ACTIVE')";
            case "supersedes" -> "SELECT WelfareLogID,IsCustomerVisible FROM "
                + "dbo.HorseWelfareLog WITH (UPDLOCK,HOLDLOCK) "
                + "WHERE WelfareLogID=:supersedes AND LegID=:leg "
                + "AND PlanID=:plan";
            case "correctionExists" -> "SELECT COUNT(*) FROM "
                + "dbo.HorseWelfareLog "
                + "WHERE SupersedesLogID=:supersedes AND LegID=:leg";
            case "milestone" -> "SET NOCOUNT ON; DECLARE @ids "
                + "TABLE(ID BIGINT); "
                + "INSERT dbo.LegMilestoneLog (PlanID,LegID,ReportedBy,"
                + "MilestoneType,OccurredAt,RecordedAt,Latitude,Longitude,"
                + "Notes,SourceCode,SourceReference,LocationDescription,"
                + "EstimatedArrivalAt,DelayReason,IsCustomerVisible,PublicNote)"
                + " OUTPUT inserted.MilestoneID INTO @ids VALUES "
                + "(:plan,:leg,:staff,:type,:at,:now,:latitude,:longitude,"
                + ":notes,:source,:reference,:location,:eta,:delay,"
                + ":visible,:publicNote); SELECT ID FROM @ids;";
            case "welfare" -> "SET NOCOUNT ON; DECLARE @ids TABLE(ID BIGINT); "
                + "INSERT dbo.HorseWelfareLog (PlanID,LegID,RecordedBy,"
                + "CheckTime,RecordedAt,HealthStatus,TemperatureCelsius,"
                + "WaterIntakeLiters,FeedAmountKg,BehaviorNotes,CareNotes,"
                + "SupersedesLogID,SourceCode,SourceReference,"
                + "IsCustomerVisible,PublicNote) OUTPUT inserted.WelfareLogID "
                + "INTO @ids VALUES (:plan,:leg,:staff,:at,:now,:health,"
                + ":temperature,:water,:feed,:behavior,:care,:supersedes,"
                + ":source,:reference,:visible,:publicNote); "
                + "SELECT ID FROM @ids;";
            case "incident" -> "SET NOCOUNT ON; DECLARE @ids TABLE(ID BIGINT); "
                + "INSERT dbo.IncidentReport (LegID,ReportedBy,IncidentType,"
                + "SeverityLevel,IncidentTime,Description,Status) "
                + "OUTPUT inserted.IncidentID INTO @ids VALUES "
                + "(:leg,:staff,'WELFARE','CRITICAL',:at,"
                + "'Urgent welfare observation; staff response required.',"
                + "'OPEN'); SELECT ID FROM @ids;";
            case "legCount" -> "SELECT COUNT_BIG(*) FROM dbo.TripLeg "
                + "WHERE PlanID=:plan AND (:driver=0 OR LegID IN "
                + "(SELECT LegID FROM dbo.TripAssignment WHERE "
                + "StaffID=:staff AND RoleInTrip='DRIVER' AND "
                + "Status IN ('CONFIRMED','ACTIVE','COMPLETED')))";
            case "legs" -> "SELECT LegID,SequenceNo,TransportMode,"
                + "LegStatus,PlannedDeparture,PlannedArrival,ActualDeparture,"
                + "ActualArrival FROM dbo.TripLeg WHERE PlanID=:plan "
                + "AND (:driver=0 OR LegID IN (SELECT LegID FROM "
                + "dbo.TripAssignment WHERE StaffID=:staff AND "
                + "RoleInTrip='DRIVER' AND Status IN "
                + "('CONFIRMED','ACTIVE','COMPLETED'))) "
                + "ORDER BY SequenceNo,LegID OFFSET :offset ROWS "
                + "FETCH NEXT :size ROWS ONLY";
            case "milestones", "milestoneCount", "milestoneLast" ->
                history(key, "LegMilestoneLog", "MilestoneID");
            case "welfareLogs", "welfareCount", "welfareLast" ->
                history(key, "HorseWelfareLog", "WelfareLogID");
            default -> throw new IllegalArgumentException("Unknown F12 query");
        };
    }

    private String history(String key, String table, String id) {
        // Identifiers below are constants selected by the internal switch.
        String filter = " FROM dbo." + table + " WHERE PlanID=:plan "
                + "AND (:customer=0 OR IsCustomerVisible=1) "
                + "AND (:driver=0 OR LegID IN (SELECT a.LegID FROM "
                + "dbo.TripAssignment a WHERE a.StaffID=:staff AND "
                + "a.RoleInTrip='DRIVER' AND a.Status IN "
                + "('CONFIRMED','ACTIVE','COMPLETED')))";
        if (key.endsWith("Count")) {
            return "SELECT COUNT_BIG(*) AS Total" + filter;
        }
        if (key.endsWith("Last")) {
            return "SELECT MAX(RecordedAt) AS LastUpdate" + filter;
        }
        String fields = table.equals("LegMilestoneLog")
                ? "MilestoneID,LegID,MilestoneType,OccurredAt,RecordedAt,"
                        + "SourceCode,SourceReference,Latitude,Longitude,"
                        + "LocationDescription,EstimatedArrivalAt,DelayReason,"
                        + "CASE WHEN :customer=1 THEN NULL ELSE Notes END "
                        + "AS Notes,PublicNote,IsCustomerVisible"
                : "WelfareLogID,LegID,HealthStatus,CheckTime,RecordedAt,"
                        + "TemperatureCelsius,WaterIntakeLiters,FeedAmountKg,"
                        + "SourceCode,SourceReference,SupersedesLogID,"
                        + "CASE WHEN :customer=1 THEN NULL ELSE BehaviorNotes "
                        + "END AS BehaviorNotes,CASE WHEN :customer=1 "
                        + "THEN NULL ELSE CareNotes END AS CareNotes,"
                        + "PublicNote,IsCustomerVisible";
        return "SELECT " + fields + filter + " ORDER BY RecordedAt DESC,"
                + id + " DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
    }
}
