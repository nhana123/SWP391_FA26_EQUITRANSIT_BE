package com.equitransit.backend.repository;

import com.equitransit.backend.dto.compliance.DocumentTemplateView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Template metadata adapter; updates retain an active catalog row lock. */
@Repository
public class DocumentTemplateRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc bound, transaction-aware SQL access */
    public DocumentTemplateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return migration025 columns are deployed */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM "
                + "sys.columns WHERE object_id=OBJECT_ID("
                + "'dbo.DocumentTypeCatalog') AND name IN ('FormCode',"
                + "'IssuingAuthority','SubAuthority','CertTextTemplate',"
                + "'BadgeColor','EmblemIcon','TemplateFileKey')",
                Integer.class) == 7;
    }

    /** @return all active document types, including types without a PDF */
    public List<DocumentTemplateView> all() {
        return jdbc.query("SELECT * FROM dbo.DocumentTypeCatalog "
                + "WHERE IsActive=1 ORDER BY TypeCode", Map.of(), this::view);
    }

    /** @param type type code @param lock retained update lock @return match */
    public List<DocumentTemplateView> find(String type, boolean lock) {
        return jdbc.query("SELECT * FROM dbo.DocumentTypeCatalog "
                + (lock ? "WITH(UPDLOCK,HOLDLOCK) " : "")
                + "WHERE IsActive=1 AND TypeCode=:type", Map.of("type", type),
                this::view);
    }

    /** @param key candidate PDF @param user uploader @return owned file row */
    public List<Map<String, Object>> ownedFile(UUID key, long user) {
        return jdbc.queryForList("SELECT * FROM dbo.LocalFileObject "
                + "WITH(UPDLOCK,HOLDLOCK) WHERE FileKey=:key AND UserID=:user",
                Map.of("key", key, "user", user));
    }

    /** @param key candidate @return already used as private horse evidence */
    public boolean usedAsEvidence(UUID key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "
                + "dbo.DocumentVersion WITH(UPDLOCK,HOLDLOCK) "
                + "WHERE FileKey=:key",
                Map.of("key", key.toString()), Integer.class) > 0;
    }

    /** @param type locked active type @param key verified PDF */
    public void attach(String type, UUID key) {
        jdbc.update("UPDATE dbo.DocumentTypeCatalog SET TemplateFileKey=:key "
                + "WHERE TypeCode=:type AND IsActive=1",
                Map.of("type", type, "key", key));
    }

    private DocumentTemplateView view(ResultSet row, int index)
            throws SQLException {
        String stored = row.getString("TemplateFileKey");
        UUID key = stored == null ? null : UUID.fromString(stored);
        return new DocumentTemplateView(row.getString("TypeCode"),
                row.getString("DisplayName"), row.getString("FormCode"),
                row.getString("IssuingAuthority"),
                row.getString("SubAuthority"),
                row.getString("CertTextTemplate"), row.getString("BadgeColor"),
                row.getString("EmblemIcon"), key, key != null,
                key == null ? null : "/api/files/" + key);
    }
}
