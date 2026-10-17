package com.equitransit.backend.repository;


import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.equitransit.backend.entity.HorseDocument;


/** SQL adapter for the existing HorseDocument table. */
@Repository
public class HorseDocumentRepository extends ComplianceJdbcSupport {

    /** @param jdbc the transaction-aware SQL template */
    public HorseDocumentRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /** Serializes immutable version allocation across application instances.
     * @param horse owned horse
     * @param customer authenticated customer
     */
    public void lockHorse(long horse, long customer) {
        require(!jdbc.query("SELECT HorseID FROM dbo.Horse"
                + " WITH (UPDLOCK,HOLDLOCK) WHERE HorseID=:horse"
                + " AND CustomerID=:customer", args("horse", horse)
                        .addValue("customer", customer),
                (row, i) -> row.getLong(1)).isEmpty());
    }

    /** @param id the row key
     * @return the matching row
     */
    public Optional<HorseDocument> findById(long id) {
        return find("HorseDocument", "DocumentID", id,
                ComplianceRows.horseDocument());
    }

    /** @param horse the horse key
     * @return its bounded document list
     */
    public List<HorseDocument> findByHorseId(long horse) {
        return list("HorseDocument", "HorseID=:horse", args("horse", horse),
                ComplianceRows.horseDocument(), "DocumentID");
    }

    /** @param horse the horse key
     * @param type the document type
     * @return the first matching document record
     */
    public Optional<HorseDocument> findByHorseIdAndType(long horse,
            String type) {
        return list("HorseDocument", "HorseID=:horse AND DocumentType=:type",
                args("horse", horse).addValue("type", type),
                ComplianceRows.horseDocument(), "DocumentID")
                .stream().findFirst();
    }

    /** @param horse the horse key
     * @param customer the authenticated owner
     */
    public void verifyOwnership(long horse, long customer) {
        require(count("SELECT COUNT(*) FROM dbo.Horse WHERE HorseID=:horse"
                + " AND CustomerID=:owner", args("horse", horse)
                        .addValue("owner", customer)) == 1);
    }

    /** @param document the document key
     * @param customer the authenticated owner
     */
    public void verifyDocumentOwnership(long document, long customer) {
        require(count("SELECT COUNT(*) FROM dbo.HorseDocument d"
                + " JOIN dbo.Horse h ON h.HorseID=d.HorseID"
                + " WHERE d.DocumentID=:id AND h.CustomerID=:owner",
                args("id", document).addValue("owner", customer)) == 1);
    }

    /** @param value the new row
     * @return its identity
     */
    public long insert(HorseDocument value) {
        var params = new MapSqlParameterSource();

        params.addValue("HorseID", value.getHorseId());
        params.addValue("DocumentType", value.getDocumentType());
        params.addValue("CreatedAt", now());
        return insert("HorseDocument", "DocumentID", params);
    }

}
