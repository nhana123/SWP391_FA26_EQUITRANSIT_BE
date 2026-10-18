package com.equitransit.backend.dto.compliance.response;

import java.time.OffsetDateTime;
import java.util.List;

/** Immutable public submission and review history.
 * @param items ordered submissions
 * @param page zero-based page
 * @param size requested page size
 * @param totalElements total submissions
 * @param totalPages total pages
 */
public record DocumentHistoryPageResponse(List<Entry> items, int page,
        int size, long totalElements, int totalPages) {

    /** One submission with its public review result.
     * @param submissionId immutable submission
     * @param documentVersionId submitted version
     * @param versionNo version number
     * @param submittedAt submission instant
     * @param decision public decision, null until reviewed
     * @param feedback public correction request
     * @param reviewedAt review instant, null until reviewed
     */
    public record Entry(long submissionId, long documentVersionId,
            int versionNo, OffsetDateTime submittedAt, String decision,
            String feedback, OffsetDateTime reviewedAt) {
    }
}
