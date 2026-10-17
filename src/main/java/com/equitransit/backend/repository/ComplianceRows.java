package com.equitransit.backend.repository;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.RowMapper;
import com.equitransit.backend.entity.DocumentTypeCatalog;
import com.equitransit.backend.entity.HorseDocument;
import com.equitransit.backend.entity.DocumentVersion;
import com.equitransit.backend.entity.DocumentRequirement;
import com.equitransit.backend.entity.DocumentSubmission;
import com.equitransit.backend.entity.DocumentReview;
import com.equitransit.backend.entity.AuthorityProcedure;
import com.equitransit.backend.entity.ComplianceClearance;


/** UTC mappings for existing F08 tables. */
final class ComplianceRows {
    private ComplianceRows() {
    }

    /** @return the DocumentTypeCatalog row mapper */
    static RowMapper<DocumentTypeCatalog> documentTypeCatalog() {
        return (row, index) -> {
            var value = new DocumentTypeCatalog();
            value.setTypeCode(row.getString("TypeCode"));
            value.setDisplayName(row.getString("DisplayName"));
            value.setDescription(row.getString("Description"));
            value.setActive(row.getBoolean("IsActive"));
            return value;
        };
    }

    /** @return the HorseDocument row mapper */
    static RowMapper<HorseDocument> horseDocument() {
        return (row, index) -> {
            var value = new HorseDocument();
            value.setDocumentId(row.getLong("DocumentID"));
            value.setHorseId(row.getLong("HorseID"));
            value.setDocumentType(row.getString("DocumentType"));
            value.setCreatedAt(time(row, "CreatedAt"));
            return value;
        };
    }

    /** @return the DocumentVersion row mapper */
    static RowMapper<DocumentVersion> documentVersion() {
        return (row, index) -> {
            var value = new DocumentVersion();
            value.setDocumentVersionId(row.getLong("DocumentVersionID"));
            value.setDocumentId(row.getLong("DocumentID"));
            value.setVersionNo(row.getInt("VersionNo"));
            value.setDocumentNumber(row.getString("DocumentNumber"));
            value.setFileKey(row.getString("FileKey"));
            value.setFileHash(row.getString("FileHash"));
            value.setIssuedDate(row.getObject("IssuedDate", LocalDate.class));
            value.setExpiryDate(row.getObject("ExpiryDate", LocalDate.class));
            value.setUploadedAt(time(row, "UploadedAt"));
            value.setUploadedBy(row.getObject("UploadedBy", Long.class));
            value.setOriginalFileName(row.getString("OriginalFileName"));
            value.setContentType(row.getString("ContentType"));
            value.setFileSizeBytes(row.getObject("FileSizeBytes", Long.class));
            return value;
        };
    }

    /** @return the DocumentRequirement row mapper */
    static RowMapper<DocumentRequirement> documentRequirement() {
        return (row, index) -> {
            var value = new DocumentRequirement();
            value.setRequirementId(row.getLong("RequirementID"));
            value.setRevisionId(row.getLong("RevisionID"));
            value.setRuleId(row.getLong("RuleID"));
            value.setDocumentType(row.getString("DocumentType"));
            value.setPhaseCode(row.getString("PhaseCode"));
            value.setMandatory(row.getBoolean("IsMandatory"));
            value.setDueAt(time(row, "DueAt"));
            value.setStatus(row.getString("Status"));
            value.setInstructions(row.getString("Instructions"));
            value.setScopeCode(row.getString("ScopeCode"));
            value.setHorseId(row.getObject("HorseID", Long.class));
            value.setValidityConditionsJson(
                    row.getString("ValidityConditionsJSON"));
            value.setCaseJustification(row.getString("CaseJustification"));
            return value;
        };
    }

    /** @return the DocumentSubmission row mapper */
    static RowMapper<DocumentSubmission> documentSubmission() {
        return (row, index) -> {
            var value = new DocumentSubmission();
            value.setSubmissionId(row.getLong("SubmissionID"));
            value.setRequirementId(row.getLong("RequirementID"));
            value.setDocumentVersionId(row.getLong("DocumentVersionID"));
            value.setStatus(row.getString("Status"));
            value.setSubmittedAt(time(row, "SubmittedAt"));
            return value;
        };
    }

    /** @return the DocumentReview row mapper */
    static RowMapper<DocumentReview> documentReview() {
        return (row, index) -> {
            var value = new DocumentReview();
            value.setReviewId(row.getLong("ReviewID"));
            value.setSubmissionId(row.getLong("SubmissionID"));
            value.setReviewedBy(row.getLong("ReviewedBy"));
            value.setDecision(row.getString("Decision"));
            value.setFeedback(row.getString("Feedback"));
            value.setReviewedAt(time(row, "ReviewedAt"));
            return value;
        };
    }

    /** @return the AuthorityProcedure row mapper */
    static RowMapper<AuthorityProcedure> authorityProcedure() {
        return (row, index) -> {
            var value = new AuthorityProcedure();
            value.setProcedureId(row.getLong("ProcedureID"));
            value.setRevisionId(row.getLong("RevisionID"));
            value.setProcedureCode(row.getString("ProcedureCode"));
            value.setAuthorityName(row.getString("AuthorityName"));
            value.setApplicationReference(
                    row.getString("ApplicationReference"));
            value.setApprovalStatus(row.getString("ApprovalStatus"));
            value.setEvidenceFileKey(row.getString("EvidenceFileKey"));
            value.setDecidedAt(time(row, "DecidedAt"));
            value.setPhaseCode(row.getString("PhaseCode"));
            value.setAuthorityContact(row.getString("AuthorityContact"));
            value.setHoldReason(row.getString("HoldReason"));
            value.setExpiresAt(time(row, "ExpiresAt"));
            value.setRecordedBy(row.getObject("RecordedBy", Long.class));
            return value;
        };
    }

    /** @return the ComplianceClearance row mapper */
    static RowMapper<ComplianceClearance> complianceClearance() {
        return (row, index) -> {
            var value = new ComplianceClearance();
            value.setClearanceId(row.getLong("ClearanceID"));
            value.setRevisionId(row.getLong("RevisionID"));
            value.setSpecialistId(row.getLong("SpecialistID"));
            value.setPhaseCode(row.getString("PhaseCode"));
            value.setStatus(row.getString("Status"));
            value.setCheckedAt(time(row, "CheckedAt"));
            value.setExpiresAt(time(row, "ExpiresAt"));
            value.setAssessmentNotes(row.getString("AssessmentNotes"));
            return value;
        };
    }

    private static OffsetDateTime time(ResultSet row, String column)
            throws SQLException {
        LocalDateTime value = row.getObject(column, LocalDateTime.class);

        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
