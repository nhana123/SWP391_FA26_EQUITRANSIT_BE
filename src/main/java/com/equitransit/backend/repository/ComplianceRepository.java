/*
 * ComplianceRepository
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.repository;


import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


/** Internal rows stay behind typed, actor-scoped services. */
@Repository
public class ComplianceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param source shared transaction-aware database */
    public ComplianceRepository(DataSource source) {
        var template = new JdbcTemplate(source);

        template.setQueryTimeout(10);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    /** @return whether the additive F08 evidence schema is deployed */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject(
                "SELECT CASE WHEN OBJECT_ID("
                + "'dbo.ComplianceChecklistHistory','U') IS NOT NULL AND "
                + "(SELECT COUNT(*) FROM "
                + "sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.ComplianceFile') AND "
                + "name IN ('FileID', 'FileKey', 'OrderID', "
                + "'RevisionID', 'UserID', 'ContentType', "
                + "'OriginalFileName', 'ContentBytes', "
                + "'FileHash'))=9 AND (SELECT COUNT(*) FROM "
                + "sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.ComplianceOperation') "
                + "AND name IN ('OrderID', 'UserID', 'OperationID', "
                + "'RequestHash', 'ReceiptJSON'))=5 AND (SELECT "
                + "COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.ComplianceChecklist') "
                + "AND name IN ('RevisionID', 'PhaseCode', "
                + "'TransitCountriesJSON', 'RuleIDsJSON', "
                + "'SourceReference', 'AssessedAt', 'ValidUntil', "
                + "'SpecialistID'))=8 AND (SELECT COUNT(*) FROM "
                + "sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.DocumentVersion') AND "
                + "name IN ('RevokedAt', 'RevokedBy', "
                + "'RevocationReason'))=3 AND (SELECT COUNT(*) FROM "
                + "sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.AuthorityProcedure') "
                + "AND name IN ('RuleID', 'SourceReference'))=2 AND "
                + "(SELECT COUNT(*) FROM sys.columns WHERE "
                + "object_id=OBJECT_ID('dbo.ComplianceRuleVersion') "
                + "AND name IN ('AuthorityRequired'))=1 AND (SELECT "
                + "COUNT(*) FROM sys.check_constraints WHERE name "
                + "IN ('CK_ComplianceFile_Size', "
                + "'CK_ComplianceFile_Type', "
                + "'CK_ComplianceOperation_JSON', "
                + "'CK_ComplianceChecklist_JSON', "
                + "'CK_ComplianceChecklist_Dates') AND "
                + "is_disabled=0 AND is_not_trusted=0)=5 AND "
                + "EXISTS(SELECT 1 FROM sys.indexes WHERE "
                + "object_id=OBJECT_ID('dbo.DocumentVersion') AND "
                + "name='UQ_DocumentVersion_Version' AND "
                + "is_unique=1 AND is_disabled=0) AND (SELECT COUNT(*) "
                + "FROM sys.indexes WHERE name IN "
                + "('UX_QuarantineAssessment_Current',"
                + "'UX_VeterinaryAssessment_Current') AND is_unique=1 "
                + "AND is_disabled=0)=2 THEN 1 ELSE 0 END",
                Integer.class) == 1;
    }

    /**
     * Reads only the order-level quarantine release gate.
     *
     * This query is intentionally independent of actor-scoped F08 access:
     * F14 needs the gate result, but must not expose F08 documents.
     *
     * @param orderId transport order key
     * @return true when the current revision is cleared and not expired
     */
    public boolean quarantineReleasePassed(long orderId) {
        String sql = "SELECT COUNT(*) "
                + "FROM dbo.ComplianceClearance c "
                + "JOIN dbo.TransportOrder o ON o.CurrentRevisionID="
                + "c.RevisionID WHERE o.OrderID=:orderId "
                + "AND c.PhaseCode='QUARANTINE_RELEASE' "
                + "AND c.Status IN ('PASS', 'CLEARED') "
                + "AND (c.ExpiresAt IS NULL OR c.ExpiresAt>SYSUTCDATETIME())";
        Integer count = jdbc.queryForObject(sql,
                Map.of("orderId", orderId), Integer.class);
        return count != null && count > 0;
    }

    /** @param key service-owned query @param args bound values @return rows */
    public List<Map<String, Object>> rows(String key,
            Map<String, ?> args) {
        return jdbc.queryForList(statement(key), args);
    }

    /** @param key service-owned write @param args bound values @return count */
    public int update(String key, Map<String, ?> args) {
        return jdbc.update(statement(key), args);
    }

    /** @param key insert @param args bound values @return generated identity */
    public long insert(String key, Map<String, ?> args) {
        return jdbc.queryForObject(statement(key), args, Long.class);
    }

    private String statement(String key) {
        return switch (key) {
            case "ruleFamily" ->
                    "SELECT TOP(1) * FROM dbo.ComplianceRuleVersion WHERE"
                    + " RuleCode=:code ORDER BY VersionNo DESC";
            case "checklistHistory" ->
                    "INSERT dbo.ComplianceChecklistHistory(RevisionID,"
                    + "PhaseCode,SnapshotJSON,AssessedAt,SpecialistID) VALUES"
                    + "(:revision,:phase,:checklistSnapshot,:now,:staff)";
            case "ruleReadLock" ->
                    "DECLARE @result INT; EXEC @result=sys.sp_getapplock "
                    + "@Resource='EquiTransit:legal-rule-catalog', "
                    + "@LockMode='Shared', @LockOwner='Transaction', "
                    + "@LockTimeout=5000; IF @result<0 THROW 51000, 'Legal "
                    + "rules unavailable', 1;";
            case "activeSpecialists" ->
                    "SELECT DISTINCT s.StaffID FROM dbo.OrderAssignment a "
                    + "JOIN dbo.StaffProfile s ON s.StaffID=a.StaffID JOIN "
                    + "dbo.[User] u ON u.UserID=s.UserID JOIN dbo.[Role] r "
                    + "ON r.RoleID=u.RoleID WHERE a.OrderID=:order AND "
                    + "a.EndedAt IS NULL AND a.AssignmentRole='SPECIALIST' "
                    + "AND r.RoleCode='SPECIALIST' AND u.Status='ACTIVE'";
            case "rulesByChecklist" ->
                    "SELECT TOP(101) r.* FROM dbo.ComplianceRuleVersion r "
                    + "JOIN dbo.ComplianceChecklist c ON "
                    + "c.RevisionID=:revision JOIN dbo.DocumentRequirement "
                    + "d ON d.RuleID=r.RuleID AND d.RevisionID=c.RevisionID "
                    + "AND d.PhaseCode=c.PhaseCode AND "
                    + "d.Status<>'SUPERSEDED' WHERE r.RuleID=:id";
            case "replay" ->
                    "SELECT RequestHash, ReceiptJSON FROM "
                    + "dbo.ComplianceOperation WHERE OrderID=:order AND "
                    + "UserID=:user AND OperationID=:op";
            case "operation" ->
                    "INSERT dbo.ComplianceOperation(OrderID, UserID, "
                    + "OperationID, RequestHash, ReceiptJSON) "
                    + "VALUES(:order, :user, :op, :hash, :receipt)";
            case "horseLock" ->
                    "SELECT HorseID FROM dbo.Horse WITH(UPDLOCK, "
                    + "HOLDLOCK) WHERE HorseID=:horse";
            case "route" ->
                    "SELECT o.OriginCountryCode, "
                    + "o.DestinationCountryCode, o.TransitAirportsJSON, "
                    + "d.HorseID, o.PreferredDepartureAt, "
                    + "o.DeliveryDeadline FROM dbo.TransportOrder o JOIN "
                    + "dbo.TransportOrderDetail d ON d.OrderID=o.OrderID "
                    + "WHERE o.OrderID=:order";
            case "rules" ->
                    "SELECT TOP(101) r.* FROM dbo.ComplianceRuleVersion r "
                    + "JOIN dbo.DocumentTypeCatalog t ON "
                    + "t.TypeCode=r.DocumentType AND t.IsActive=1 WHERE "
                    + "r.OriginCountryCode=:origin AND "
                    + "r.DestinationCountryCode=:destination AND "
                    + "r.PhaseCode=:phase AND r.ReviewedAt<=:now AND "
                    + "r.EffectiveFrom<=:now AND (r.TransitCountryCode IS "
                    + "NULL OR r.TransitCountryCode IN (SELECT value FROM "
                    + "OPENJSON(:transits))) AND NOT EXISTS(SELECT 1 FROM "
                    + "dbo.ComplianceRuleVersion newer WHERE "
                    + "newer.RuleCode=r.RuleCode AND "
                    + "newer.VersionNo>r.VersionNo AND "
                    + "newer.ReviewedAt<=:now AND "
                    + "newer.EffectiveFrom<=:now) ORDER BY r.RuleID";
            case "ruleLock" ->
                    "DECLARE @result INT; EXEC @result=sys.sp_getapplock "
                    + "@Resource=:resource, @LockMode='Exclusive', "
                    + "@LockOwner='Transaction', @LockTimeout=5000; IF "
                    + "@result<0 THROW 51000, 'Legal catalog lock "
                    + "unavailable', 1;";
            case "catalog" ->
                    "SELECT TypeCode, IsActive FROM "
                    + "dbo.DocumentTypeCatalog WHERE TypeCode=:type";
            case "catalogInsert" ->
                    "INSERT dbo.DocumentTypeCatalog(TypeCode, "
                    + "DisplayName, IsActive) VALUES(:type, :display, 1)";
            case "ruleInsert" ->
                    "INSERT dbo.ComplianceRuleVersion(RuleCode,VersionNo,"
                    + "OriginCountryCode,DestinationCountryCode,DocumentType,"
                    + "PhaseCode,SourceURL,ReviewedAt,EffectiveFrom,"
                    + "EffectiveTo,"
                    + "TransitCountryCode,AuthorityRequired,RuleDescription)"
                    + " OUTPUT INSERTED.RuleID VALUES(:code,(SELECT COALESCE("
                    + "MAX(VersionNo),0)+1 FROM dbo.ComplianceRuleVersion"
                    + " WHERE RuleCode=:code),:origin,:destination,:type,"
                    + ":phase,:source,:now,:from,:until,:transit,:authority,"
                    + ":instructions)";
            case "checklist" ->
                    "SELECT * FROM dbo.ComplianceChecklist WHERE "
                    + "RevisionID=:revision AND PhaseCode=:phase";
            case "checklistDelete" ->
                    "DELETE dbo.ComplianceChecklist WHERE "
                    + "RevisionID=:revision AND PhaseCode=:phase";
            case "checklistInsert" ->
                    "INSERT dbo.ComplianceChecklist(RevisionID, "
                    + "PhaseCode, TransitCountriesJSON, RuleIDsJSON, "
                    + "SourceReference, AssessedAt, ValidUntil, "
                    + "SpecialistID) VALUES(:revision, :phase, :transits, "
                    + ":rules, :source, :now, :until, :staff)";
            case "oldRequirements" ->
                    "UPDATE dbo.DocumentRequirement SET "
                    + "Status='SUPERSEDED' WHERE RevisionID=:revision AND "
                    + "PhaseCode=:phase";
            case "requirementInsert" ->
                    "INSERT dbo.DocumentRequirement(RevisionID,RuleID,"
                    + "DocumentType,PhaseCode,IsMandatory,Status,ScopeCode,"
                    + "HorseID,Instructions) SELECT :revision,r.RuleID,"
                    + "r.DocumentType,:phase,1,'PENDING','HORSE',:horse,"
                    + "r.RuleDescription FROM dbo.ComplianceRuleVersion r"
                    + " WHERE r.RuleID IN(SELECT value FROM OPENJSON(:rules))"
                    + " AND NOT EXISTS(SELECT 1 FROM dbo.DocumentRequirement"
                    + " d WHERE d.RevisionID=:revision AND d.RuleID=r.RuleID"
                    + " AND d.PhaseCode=:phase)";
            case "requirementReactivate" ->
                    "UPDATE dbo.DocumentRequirement SET Status='PENDING' "
                    + "WHERE RevisionID=:revision AND PhaseCode=:phase AND "
                    + "RuleID IN(SELECT value FROM OPENJSON(:rules))";
            case "requirements" ->
                    "SELECT TOP(101) d.RequirementID, d.RuleID, "
                    + "d.DocumentType, d.PhaseCode, d.Instructions, d.Status, "
                    + "d.IsMandatory, s.SubmissionID, s.DocumentVersionID, "
                    + "v.ExpiryDate,v.IssuedDate,v.RevokedAt,v.UploadedAt,"
                    + " CASE WHEN "
                    + "EXISTS(SELECT 1 FROM dbo.ComplianceFile f WHERE "
                    + "f.FileKey=TRY_CONVERT(uniqueidentifier, v.FileKey) "
                    + "AND f.FileHash=v.FileHash) THEN 1 ELSE 0 END AS "
                    + "PrivateFilePresent, review.Decision, "
                    + "review.ReviewedBy, review.ReviewedAt, "
                    + "latest.DocumentVersionID AS LatestVersionID FROM "
                    + "dbo.DocumentRequirement d OUTER APPLY(SELECT TOP(1) "
                    + "s.* FROM dbo.DocumentSubmission s WHERE "
                    + "s.RequirementID=d.RequirementID ORDER BY "
                    + "s.SubmissionID DESC) s LEFT JOIN dbo.DocumentVersion "
                    + "v ON v.DocumentVersionID=s.DocumentVersionID OUTER "
                    + "APPLY(SELECT TOP(1) r.Decision, r.ReviewedBy, "
                    + "r.ReviewedAt FROM dbo.DocumentReview r WHERE "
                    + "r.SubmissionID=s.SubmissionID ORDER BY r.ReviewID "
                    + "DESC) review OUTER APPLY(SELECT TOP(1) "
                    + "dv.DocumentVersionID FROM dbo.DocumentVersion dv "
                    + "JOIN dbo.HorseDocument hd ON "
                    + "hd.DocumentID=dv.DocumentID WHERE hd.HorseID=:horse "
                    + "AND hd.DocumentType=d.DocumentType ORDER BY "
                    + "dv.DocumentVersionID DESC) latest WHERE "
                    + "d.RevisionID=:revision AND d.PhaseCode=:phase AND "
                    + "d.Status<>'SUPERSEDED' ORDER BY d.RequirementID";
            case "requirement" ->
                    "SELECT * FROM dbo.DocumentRequirement WHERE "
                    + "RequirementID=:id AND RevisionID=:revision AND "
                    + "Status<>'SUPERSEDED'";
            case "document" ->
                    "SELECT TOP(1) DocumentID FROM dbo.HorseDocument "
                    + "WHERE HorseID=:horse AND DocumentType=:type ORDER BY "
                    + "DocumentID DESC";
            case "documentInsert" ->
                    "INSERT dbo.HorseDocument(HorseID, DocumentType) "
                    + "OUTPUT INSERTED.DocumentID VALUES(:horse, :type)";
            case "versionInsert" ->
                    "INSERT dbo.DocumentVersion(DocumentID, VersionNo, "
                    + "DocumentNumber, FileKey, FileHash, IssuedDate, "
                    + "ExpiryDate, UploadedBy, OriginalFileName, "
                    + "ContentType, FileSizeBytes) OUTPUT "
                    + "INSERTED.DocumentVersionID VALUES(:document, (SELECT "
                    + "COALESCE(MAX(VersionNo), 0)+1 FROM "
                    + "dbo.DocumentVersion WHERE DocumentID=:document), "
                    + ":number, :key, :fileHash, :issued, :expiry, :user, "
                    + ":name, :mime, :length)";
            case "version" ->
                    "SELECT v.*, h.DocumentType FROM dbo.DocumentVersion "
                    + "v JOIN dbo.HorseDocument h ON "
                    + "h.DocumentID=v.DocumentID WHERE h.HorseID=:horse AND "
                    + "v.DocumentVersionID=:id";
            case "documents" ->
                    "SELECT v.*, h.DocumentType FROM dbo.DocumentVersion "
                    + "v JOIN dbo.HorseDocument h ON "
                    + "h.DocumentID=v.DocumentID WHERE h.HorseID=:horse "
                    + "ORDER BY v.DocumentVersionID DESC OFFSET :offset "
                    + "ROWS FETCH NEXT :size ROWS ONLY";
            case "submissionInsert" ->
                    "INSERT dbo.DocumentSubmission(RequirementID, "
                    + "DocumentVersionID, Status) OUTPUT "
                    + "INSERTED.SubmissionID VALUES(:requirement, :version, "
                    + "'SUBMITTED')";
            case "submission" ->
                    "SELECT s.*, d.PhaseCode FROM dbo.DocumentSubmission "
                    + "s JOIN dbo.DocumentRequirement d ON "
                    + "d.RequirementID=s.RequirementID WHERE "
                    + "s.SubmissionID=:id AND d.RevisionID=:revision AND "
                    + "d.Status<>'SUPERSEDED' AND NOT EXISTS(SELECT 1 FROM "
                    + "dbo.DocumentSubmission newer WHERE "
                    + "newer.RequirementID=s.RequirementID AND "
                    + "newer.SubmissionID>s.SubmissionID)";
            case "reviewInsert" ->
                    "INSERT dbo.DocumentReview(SubmissionID, ReviewedBy, "
                    + "Decision, Feedback, ReviewedAt) OUTPUT "
                    + "INSERTED.ReviewID VALUES(:id, :staff, :decision, "
                    + ":feedback, :now)";
            case "revoke" ->
                    "UPDATE dbo.DocumentVersion SET RevokedAt=:now, "
                    + "RevokedBy=:staff, RevocationReason=:reason WHERE "
                    + "DocumentVersionID=:id AND RevokedAt IS NULL";
            case "fileInsert" ->
                    "INSERT dbo.ComplianceFile(FileKey, OrderID, "
                    + "RevisionID, UserID, ContentType, OriginalFileName, "
                    + "ContentBytes, FileHash) OUTPUT INSERTED.FileID "
                    + "VALUES(:key, :order, :revision, :user, :mime, :name, "
                    + ":bytes, :fileHash)";
            case "file" ->
                    "SELECT FileID, FileKey, UserID, ContentType, "
                    + "OriginalFileName, FileHash, DATALENGTH(ContentBytes) "
                    + "AS FileSizeBytes FROM dbo.ComplianceFile WHERE "
                    + "FileKey=:key AND OrderID=:order AND "
                    + "RevisionID=:revision";
            case "fileRead" ->
                    "SELECT f.ContentBytes, f.ContentType, "
                    + "f.OriginalFileName FROM dbo.ComplianceFile f WHERE "
                    + "f.FileKey=:key AND (f.OrderID=:order AND "
                    + "f.RevisionID=:revision OR EXISTS(SELECT 1 FROM "
                    + "dbo.DocumentVersion v JOIN dbo.HorseDocument d ON "
                    + "d.DocumentID=v.DocumentID WHERE "
                    + "v.FileKey=CONVERT(nvarchar(36), f.FileKey) AND "
                    + "d.HorseID=:horse))";
            case "procedureInsert" ->
                    "INSERT dbo.AuthorityProcedure(RevisionID, RuleID, "
                    + "ProcedureCode, PhaseCode, AuthorityName, "
                    + "ApplicationReference, ApprovalStatus, "
                    + "EvidenceFileKey, DecidedAt, ExpiresAt, HoldReason, "
                    + "RecordedBy, SourceReference) OUTPUT "
                    + "INSERTED.ProcedureID VALUES(:revision, :rule, "
                    + "'LEGAL_APPROVAL', :phase, :authorityName, "
                    + ":reference, :decision, :key, :decided, :until, "
                    + ":hold, :staff, :source)";
            case "procedures" ->
                    "SELECT p.*, CASE WHEN EXISTS(SELECT 1 FROM "
                    + "dbo.ComplianceFile f WHERE "
                    + "f.FileKey=TRY_CONVERT(uniqueidentifier, "
                    + "p.EvidenceFileKey) AND f.OrderID=:order AND "
                    + "f.RevisionID=:revision) THEN 1 ELSE 0 END AS "
                    + "PrivateFilePresent FROM dbo.AuthorityProcedure p "
                    + "WHERE p.RevisionID=:revision AND p.PhaseCode=:phase "
                    + "AND NOT EXISTS(SELECT 1 FROM dbo.AuthorityProcedure "
                    + "newer WHERE newer.RevisionID=p.RevisionID AND "
                    + "newer.PhaseCode=p.PhaseCode AND "
                    + "newer.RuleID=p.RuleID AND "
                    + "newer.ProcedureID>p.ProcedureID) ORDER BY "
                    + "p.ProcedureID";
            case "clearance" ->
                    "SELECT TOP(1) * FROM dbo.ComplianceClearance WHERE "
                    + "RevisionID=:revision AND PhaseCode=:phase ORDER BY "
                    + "ClearanceID DESC";
            case "clearanceInsert" ->
                    "INSERT dbo.ComplianceClearance(RevisionID, "
                    + "SpecialistID, PhaseCode, Status, CheckedAt, "
                    + "ExpiresAt) OUTPUT INSERTED.ClearanceID "
                    + "VALUES(:revision, :staff, :phase, 'CLEARED', :now, "
                    + ":until)";
            case "invalidate" ->
                    "UPDATE dbo.ComplianceClearance SET "
                    + "Status='SUPERSEDED' WHERE RevisionID=:revision AND "
                    + "Status='CLEARED'";
            case "quarantine" ->
                    "SELECT * FROM dbo.RevisionQuarantineAssessment WHERE "
                    + "RevisionID=:revision AND SupersededAt IS NULL";
            case "vet" ->
                    "SELECT * FROM dbo.RevisionVeterinaryAssessment WHERE "
                    + "RevisionID=:revision AND SupersededAt IS NULL";
            case "quarantineOld" ->
                    "UPDATE dbo.RevisionQuarantineAssessment SET "
                    + "SupersededAt=:now WHERE RevisionID=:revision AND "
                    + "SupersededAt IS NULL";
            case "vetOld" ->
                    "UPDATE dbo.RevisionVeterinaryAssessment SET "
                    + "SupersededAt=:now WHERE RevisionID=:revision AND "
                    + "SupersededAt IS NULL";
            case "quarantineInsert" ->
                    "INSERT dbo.RevisionQuarantineAssessment(RevisionID, "
                    + "Applicability, RequiredDays, SpecialistID, "
                    + "AssessedAt, ValidUntil, SourceReference) OUTPUT "
                    + "INSERTED.AssessmentID VALUES(:revision, "
                    + ":applicability, :days, :staff, :now, :until, "
                    + ":source)";
            case "vetInsert" ->
                    "INSERT dbo.RevisionVeterinaryAssessment(RevisionID, "
                    + "VisitCount, TestCount, DoseCount, CertificateCount, "
                    + "SpecialistID, AssessedAt, ValidUntil, "
                    + "SourceReference) VALUES(:revision, :visits, :tests, "
                    + ":doses, :certificates, :staff, :now, :until, "
                    + ":source)";
            case "quarantineChoice" ->
                    "UPDATE q SET RequiredDays=:days, ConfirmedBy=CASE "
                    + "WHEN :days IS NULL THEN NULL ELSE :staff END, "
                    + "DaysConfirmedAt=CASE WHEN :days IS NULL THEN NULL "
                    + "ELSE :now END FROM dbo.QuarantineChoice q JOIN "
                    + "dbo.OrderServiceChoice c ON c.ChoiceID=q.ChoiceID "
                    + "WHERE c.RevisionID=:revision";
            case "bump" ->
                    "UPDATE dbo.TransportOrder SET "
                    + "UpdatedAt=SYSUTCDATETIME() WHERE OrderID=:order";
            default -> throw new IllegalArgumentException("Unknown legal SQL");
        };
    }
}
