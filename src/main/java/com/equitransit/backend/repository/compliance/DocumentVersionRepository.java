package com.equitransit.backend.repository.compliance;


import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.compliance.DocumentVersion;


/** SQL adapter for the existing DocumentVersion table. */
@Repository
public class DocumentVersionRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public DocumentVersionRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @param id the row key
     * @return the matching row
     */
    public Optional<DocumentVersion> findById(long id) {
        return find("DocumentVersion", "DocumentVersionID", id,
                ComplianceRows.documentVersion());
    }

    /** Finds an immutable registration for an idempotent metadata replay.
     * @param key verified storage key
     * @return original version if already registered
     */
    public Optional<DocumentVersion> findByFileKey(String key) {
        return jdbc.query("SELECT * FROM dbo.DocumentVersion WHERE "
        + "FileKey=:key",
                args("key", key), ComplianceRows.documentVersion())
                .stream().findFirst();
    }

    /** @param document the document key
     * @return its bounded immutable version history
     */
    public List<DocumentVersion> findByDocumentId(long document) {
        return list("DocumentVersion", "DocumentID=:id", args("id", document),
                ComplianceRows.documentVersion(), "VersionNo DESC");
    }

    /** @param document the document key
     * @return its maximum stored version number
     */
    public int maxVersionNo(long document) {
        return (int) count("SELECT COALESCE(MAX(VersionNo),0)"
                + " FROM dbo.DocumentVersion WHERE DocumentID=:id",
                args("id", document));
    }

    /** @param value the new row
     * @return its identity
     */
    public long insert(DocumentVersion value) {
        var params = new MapSqlParameterSource();

        params.addValue("DocumentID", value.getDocumentId());
        params.addValue("VersionNo", value.getVersionNo());
        params.addValue("DocumentNumber", value.getDocumentNumber());
        params.addValue("FileKey", value.getFileKey());
        params.addValue("FileHash", value.getFileHash());
        params.addValue("IssuedDate", value.getIssuedDate());
        params.addValue("ExpiryDate", value.getExpiryDate());
        params.addValue("UploadedAt", now());
        params.addValue("UploadedBy", value.getUploadedBy());
        params.addValue("OriginalFileName", value.getOriginalFileName());
        params.addValue("ContentType", value.getContentType());
        params.addValue("FileSizeBytes", value.getFileSizeBytes());
        return insert("DocumentVersion", "DocumentVersionID", params);
    }

}
