package com.equitransit.backend.repository;

import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.service.FileStorageService.StoredFile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Metadata and owner/case visibility for private local objects. */
@Repository
public class LocalFileRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** @param jdbc transaction-aware persistence */
    public LocalFileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param file immutable receipt @param user uploader @param hash SHA256 */
    public void insert(StoredFile file, long user, String hash) {
        jdbc.update("INSERT dbo.LocalFileObject(FileKey,UserID,FileName,"
                + "ContentType,FileSizeBytes,FileHash) VALUES(:key,:user,"
                + ":name,:mime,:size,:hash)", Map.of("key", file.fileKey(),
                        "user", user, "name", file.fileName(), "mime",
                        file.contentType(), "size", file.fileSizeBytes(),
                        "hash", hash));
    }

    /** @return whether local metadata is deployed */
    public boolean ready() {
        return jdbc.getJdbcTemplate().queryForObject("SELECT CASE WHEN "
                + "OBJECT_ID('dbo.LocalFileObject','U') IS NULL THEN 0 "
                + "ELSE 1 END", Integer.class) == 1;
    }

    /** @param key opaque key @param actor live identity @return visible rows */
    public List<Map<String, Object>> visible(UUID key, CurrentActor actor) {
        return jdbc.queryForList("SELECT f.* FROM dbo.LocalFileObject f "
                + "WHERE f.FileKey=:key AND (f.UserID=:user OR :role='MANAGER'"
                + templateVisibility()
                + " OR EXISTS(SELECT 1 FROM dbo.DocumentVersion v JOIN "
                + "dbo.HorseDocument hd ON hd.DocumentID=v.DocumentID JOIN "
                + "dbo.TransportOrderDetail od ON od.HorseID=hd.HorseID "
                + "JOIN dbo.TransportOrder o ON o.OrderID=od.OrderID WHERE "
                + "v.FileKey=CONVERT(varchar(36),f.FileKey) AND "
                + "(o.CustomerID=:customer OR EXISTS(SELECT 1 FROM "
                + "dbo.OrderAssignment a WHERE a.OrderID=o.OrderID AND "
                + "a.StaffID=:staff AND a.EndedAt IS NULL))))",
                new org.springframework.jdbc.core.namedparam
                        .MapSqlParameterSource("key", key)
                        .addValue("user", actor.userId())
                        .addValue("role", actor.roleCode())
                        .addValue("customer", actor.customerId())
                        .addValue("staff", actor.staffId()));
    }

    private String templateVisibility() {
        boolean present = jdbc.getJdbcTemplate().queryForObject("SELECT "
                + "CASE WHEN COL_LENGTH('dbo.DocumentTypeCatalog',"
                + "'TemplateFileKey') IS NULL THEN 0 ELSE 1 END",
                Integer.class) == 1;
        return present ? " OR EXISTS(SELECT 1 FROM dbo.DocumentTypeCatalog t "
                + "WHERE t.TemplateFileKey=f.FileKey AND t.IsActive=1)" : "";
    }
}
