/*
 * HorseRepository
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.repository.horse;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.equitransit.backend.entity.horse.Horse;
import com.equitransit.backend.entity.horse.HorseSex;


/** Owner-scoped parameterized SQL with atomic rowversion updates. */
@Repository
public class HorseRepository {

    private static final String TABLE = "dbo.Horse";
    private static final String COLUMNS =
            "HorseID, CustomerID, Name, MicrochipID, PassportNumber, " +
            "Breed, DateOfBirth, SexCode, WeightKg, HeightCm, " +
            "BodyWidthCm, MeasuredAt, SpecialCareNotes, " +
            "HandlingNotes, FeedWaterRequirements, StallPreferences, " +
            "VeterinaryInstructions, VeterinaryInstructionSource, " +
            "RecordVersion";
    private static final String INSERT =
            "INSERT INTO dbo.Horse (CustomerID, Name, MicrochipID, " +
            "PassportNumber, Breed, DateOfBirth, SexCode, WeightKg, " +
            "HeightCm, BodyWidthCm, MeasuredAt, SpecialCareNotes, " +
            "HandlingNotes, FeedWaterRequirements, StallPreferences, " +
            "VeterinaryInstructions, VeterinaryInstructionSource) " +
            "OUTPUT INSERTED.HorseID VALUES (:customerId, :name, " +
            ":microchipId, :passportNumber, :breed, :dateOfBirth, " +
            ":sexCode, :weightKg, :heightCm, :bodyWidthCm, " +
            ":measuredAt, :specialCareNotes, :handlingNotes, " +
            ":feedWaterRequirements, :stallPreferences, " +
            ":veterinaryInstructions, :veterinaryInstructionSource)";
    private static final String UPDATE =
            "UPDATE dbo.Horse SET Name=:name, " +
            "MicrochipID=:microchipId, " +
            "PassportNumber=:passportNumber, Breed=:breed, " +
            "DateOfBirth=:dateOfBirth, SexCode=:sexCode, " +
            "WeightKg=:weightKg, HeightCm=:heightCm, " +
            "BodyWidthCm=:bodyWidthCm, MeasuredAt=:measuredAt, " +
            "SpecialCareNotes=:specialCareNotes, " +
            "HandlingNotes=:handlingNotes, " +
            "FeedWaterRequirements=:feedWaterRequirements, " +
            "StallPreferences=:stallPreferences, " +
            "VeterinaryInstructions=:veterinaryInstructions, " +
            "VeterinaryInstructionSource=:veterinaryInstructionSource " +
            "WHERE HorseID=:horseId AND CustomerID=:customerId AND " +
            "RecordVersion=:version";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the SQL repository.
     * @param jdbc the transaction-aware JDBC template
     */
    public HorseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reads only a horse belonging to the supplied trusted owner.
     * @param id the horse key
     * @param owner the authenticated customer key
     * @return the matching horse, otherwise empty
     */
    public Optional<Horse> findOwned(long id, long owner) {
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("horseId", id).addValue("customerId", owner);

        return jdbc.query("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE HorseID=:horseId AND CustomerID=:customerId",
                args, (row, index) -> read(row)).stream().findFirst();
    }

    /**
     * Locks the selected horse while a booking measurement check commits.
     * @param id selected owned horse key
     * @param owner live customer key
     * @return owned locked profile or empty
     */
    public Optional<Horse> lockOwned(long id, long owner) {
        return jdbc.query("SELECT " + COLUMNS + " FROM " + TABLE
                + " WITH (UPDLOCK,HOLDLOCK) WHERE HorseID=:id"
                + " AND CustomerID=:owner",
                new MapSqlParameterSource("id", id).addValue("owner", owner),
                (row, index) -> read(row)).stream().findFirst();
    }

    /**
     * Lists the owner's profiles with fixed, deterministic sorting.
     * @param owner the authenticated customer key
     * @param page the zero-based page
     * @param size the bounded page size
     * @param query the normalized name filter
     * @return only the owner's requested page
     */
    public List<Horse> list(long owner, int page, int size, String query) {
        MapSqlParameterSource args = search(owner, query)
                .addValue("offset", (long) page * size)
                .addValue("size", size);

        return jdbc.query("SELECT " + COLUMNS + " FROM " + TABLE
                + filter() + " ORDER BY HorseID DESC OFFSET :offset ROWS"
                + " FETCH NEXT :size ROWS ONLY", args,
                (row, index) -> read(row));
    }

    /**
     * Counts the same owner and search scope as the page query.
     * @param owner the authenticated customer key
     * @param query the normalized name filter
     * @return the matching count
     */
    public long count(long owner, String query) {
        Long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM "
                + TABLE + filter(), search(owner, query), Long.class);

        return total == null ? 0 : total;
    }

    /**
     * Inserts a profile, letting SQL Server create identity and version.
     * @param horse the validated persistence model
     * @return the generated key
     */
    public long insert(Horse horse) {
        Long id = jdbc.queryForObject(INSERT, values(horse), Long.class);

        if (id == null) {
            throw new IllegalStateException("Horse identity unavailable.");
        }
        return id;
    }

    /**
     * Updates atomically only if owner and binary version still match.
     * @param horse the normalized replacement profile
     * @param version the client's last observed version
     * @return whether one row changed
     */
    public boolean update(Horse horse, String version) {
        MapSqlParameterSource args = values(horse)
                .addValue("horseId", horse.getHorseId())
                .addValue("version", HexFormat.of().parseHex(version));

        return jdbc.update(UPDATE, args) == 1;
    }

    private String filter() {
        return " WHERE CustomerID=:customerId"
                + " AND Name LIKE :query ESCAPE '\\'";
    }

    private MapSqlParameterSource search(long owner, String query) {
        String escaped = query.replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_")
                .replace("[", "\\[");

        return new MapSqlParameterSource().addValue("customerId", owner)
                .addValue("query", "%" + escaped + "%");
    }

    private MapSqlParameterSource values(Horse horse) {
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("customerId", horse.getCustomerId());

        args.addValue("name", horse.getName());
        args.addValue("microchipId", horse.getMicrochipId());
        args.addValue("passportNumber", horse.getPassportNumber());
        args.addValue("breed", horse.getBreed());
        args.addValue("dateOfBirth", horse.getDateOfBirth());
        args.addValue("sexCode", horse.getSexCode() == null ? null
                : horse.getSexCode().name());
        args.addValue("weightKg", horse.getWeightKg());
        args.addValue("heightCm", horse.getHeightCm());
        args.addValue("bodyWidthCm", horse.getBodyWidthCm());
        args.addValue("specialCareNotes", horse.getSpecialCareNotes());
        args.addValue("handlingNotes", horse.getHandlingNotes());
        args.addValue("feedWaterRequirements",
                horse.getFeedWaterRequirements());
        args.addValue("stallPreferences", horse.getStallPreferences());
        args.addValue("veterinaryInstructions",
                horse.getVeterinaryInstructions());
        args.addValue("veterinaryInstructionSource",
                horse.getVeterinaryInstructionSource());
        args.addValue("measuredAt", horse.getMeasuredAt() == null ? null
                : horse.getMeasuredAt().withOffsetSameInstant(
                        ZoneOffset.UTC).toLocalDateTime());
        return args;
    }

    private Horse read(ResultSet row) throws SQLException {
        Horse horse = new Horse();
        String sex = row.getString("SexCode");

        horse.setHorseId(row.getLong("HorseID"));
        horse.setCustomerId(row.getLong("CustomerID"));
        horse.setName(row.getString("Name"));
        horse.setMicrochipId(row.getString("MicrochipID"));
        horse.setPassportNumber(row.getString("PassportNumber"));
        horse.setBreed(row.getString("Breed"));
        horse.setWeightKg(row.getBigDecimal("WeightKg"));
        horse.setHeightCm(row.getBigDecimal("HeightCm"));
        horse.setBodyWidthCm(row.getBigDecimal("BodyWidthCm"));
        horse.setSpecialCareNotes(row.getString("SpecialCareNotes"));
        horse.setHandlingNotes(row.getString("HandlingNotes"));
        horse.setFeedWaterRequirements(row.getString("FeedWaterRequirements"));
        horse.setStallPreferences(row.getString("StallPreferences"));
        horse.setVeterinaryInstructions(
                row.getString("VeterinaryInstructions"));
        horse.setVeterinaryInstructionSource(
                row.getString("VeterinaryInstructionSource"));
        horse.setSexCode(sex == null ? null : HorseSex.valueOf(sex));
        horse.setDateOfBirth(row.getDate("DateOfBirth") == null ? null
                : row.getDate("DateOfBirth").toLocalDate());
        horse.setMeasuredAt(row.getTimestamp("MeasuredAt") == null ? null
                : row.getTimestamp("MeasuredAt").toLocalDateTime()
                        .atOffset(ZoneOffset.UTC));
        horse.setRecordVersion(HexFormat.of().formatHex(
                row.getBytes("RecordVersion")));
        return horse;
    }
}
