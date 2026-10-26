package com.equitransit.backend.dto.compliance;

import com.equitransit.backend.entity.compliance.CompliancePhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Checklist and reusable horse versions, returned together for two tabs.
 * @param recordVersion concurrency token @param requirements required papers
 * @param versions horse document history @param totalElements requirement count
 * @param page page @param size limit */
@Schema(name = "ComplianceDocuments")
public record ComplianceDocumentsView(
        @Schema(example = "000000000001A2B3") String recordVersion,
        @Schema(example = "[]") List<Requirement> requirements,
        @Schema(example = "[]") List<ComplianceDocumentView> versions,
        @Schema(example = "8") long totalElements,
        @Schema(example = "0") int page, @Schema(example = "20") int size) {

    /** Required paper including the current submission and review.
     * @param requirementId identity @param horseId horse
     * @param documentType type
     * @param phase phase @param status PENDING_SUBMISSION or evidence status
     * @param submissionId latest submission @param documentVersionId version
     * @param instructions guidance @param feedback reviewer note
     * @param attachment latest file */
    @Schema(name = "RequiredDocument")
    public record Requirement(@Schema(example = "101") long requirementId,
            @Schema(example = "12") long horseId,
            @Schema(example = "FEI_PASSPORT") String documentType,
            @Schema(example = "PRE_EXPORT") CompliancePhase phase,
            @Schema(example = "PENDING_SUBMISSION") String status,
            @Schema(example = "102") Long submissionId,
            @Schema(example = "103") Long documentVersionId,
            @Schema(example = "Upload current passport") String instructions,
            @Schema(example = "Scan is unreadable") String feedback,
            @Schema(example = "{}") ComplianceAttachmentView attachment) { }
}
