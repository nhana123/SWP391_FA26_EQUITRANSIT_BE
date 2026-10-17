package com.equitransit.backend.repository;


import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.DocumentTypeCatalog;


/** SQL adapter for the existing DocumentTypeCatalog table. */
@Repository
public class DocumentTypeCatalogRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public DocumentTypeCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** @return a bounded page of active document types */
    public List<DocumentTypeCatalog> listActive() {
        return list("DocumentTypeCatalog", "IsActive=1",
                new MapSqlParameterSource(),
                ComplianceRows.documentTypeCatalog(), "TypeCode");
    }

}
