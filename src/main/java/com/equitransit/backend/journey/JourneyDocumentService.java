package com.equitransit.backend.journey;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.equitransit.backend.journey.JourneyAccess.number;
import static com.equitransit.backend.journey.JourneyBookingService.time;
import static com.equitransit.backend.journey.JourneyPolicy.require;

/** Per-booking-horse evidence and independently expiring phase clearance. */
@Service
@Transactional
public class JourneyDocumentService {
    private final JourneyStore db;
    private final JourneyAccess access;
    private final Clock clock;

    /** @param db store @param access authorization @param clock UTC time */
    public JourneyDocumentService(JourneyStore db, JourneyAccess access,
            Clock clock) {
        this.db = db;
        this.access = access;
        this.clock = clock;
    }

    /** @param id booking @return required documents and latest review notes */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(long id) {
        access.booking(id);
        return db.rows("SELECT r.*,s.SubmissionID,s.FileKey,s.ExpiresAt,"
                + "s.Decision,s.ReviewNotes FROM journey.Requirement r"
                + " JOIN journey.BookingHorse h ON"
                + " h.BookingHorseID=r.BookingHorseID OUTER APPLY"
                + " (SELECT TOP(1) x.* FROM journey.Submission x WHERE"
                + " x.RequirementID=r.RequirementID ORDER BY "
                        + "SubmissionID DESC) s"
                + " WHERE h.BookingID=:id ORDER BY r.BookingHorseID,"
                + "r.Phase,r.RequirementID", Map.of("id", id));
    }

    /** @param id booking @param req file data @return opaque file key */
    public Map<String, Object> upload(long id,
            JourneyModels.DocumentRequest req) {
        access.role("CUSTOMER", "SPECIALIST", "COORDINATOR", "DRIVER",
                "MANAGER");
        return access.operation("FILE_UPLOAD:" + id,
                req.command().operationId(), req, () -> {
                    var b = access.booking(id);
                    access.version(b, req.command().version());
                    return Map.of("fileKey", saveFile(id, req).toString());
                });
    }

    /** @param key opaque key @return authorized file metadata and bytes */
    @Transactional(readOnly = true)
    public Map<String, Object> file(UUID key) {
        var row = access.one("SELECT * FROM journey.FileObject WHERE "
                + "FileKey=:key",
                Map.of("key", key.toString()));
        access.booking(number(row, "BookingID"));
        return row;
    }

    /** @param id requirement @param req upload/version data
     * @return updated requirement; earlier versions remain in history */
    public Map<String, Object> submit(long id,
            JourneyModels.DocumentRequest req) {
        access.role("CUSTOMER", "SPECIALIST");
        return access.operation("DOCUMENT_SUBMIT:" + id,
                req.command().operationId(), req, () -> {
                    var row = requirement(id);
                    access.version(row, req.command().version());
                    long booking = number(row, "BookingID");
                    access.booking(booking);
                    require(req.expiresAt().isAfter(LocalDateTime.now(clock)),
                            "DOCUMENT_EXPIRED");
                    UUID key = saveFile(booking, req);
                    long submission = db.insert("INSERT journey.Submission"
                            + "(RequirementID,FileKey,ExpiresAt)"
                            + " VALUES (:id,:file,:expiry)", Map.of("id", id,
                                    "file", key.toString(),
                                    "expiry", req.expiresAt()));
                    db.write("UPDATE journey.Requirement SET "
                            + "Status='SUBMITTED',"
                            + "Version=Version+1 WHERE RequirementID=:id",
                            Map.of("id", id));
                    invalidate(row);
                    return Map.of("requirementId", id, "submissionId",
                            submission,
                            "fileKey", key.toString(), "status", "SUBMITTED",
                            "version", req.command().version() + 1);
                });
    }

    /** @param id requirement @param req current submission review
     * @return updated requirement */
    public Map<String, Object> review(long id,
            JourneyModels.ReviewRequest req) {
        var actor = access.role("SPECIALIST");
        return access.operation("DOCUMENT_REVIEW:" + id,
                req.command().operationId(), req, () -> {
                    var row = requirement(id);
                    access.booking(number(row, "BookingID"));
                    access.version(row, req.command().version());
                    require("SUBMITTED".equals(row.get("Status")),
                            "DOCUMENT_NOT_SUBMITTED");
                    var last = access.one("SELECT TOP(1) * FROM "
                            + "journey.Submission"
                            + " WHERE RequirementID=:id ORDER BY "
                                    + "SubmissionID DESC",
                            Map.of("id", id));
                    require(number(last, "SubmissionID") == req.submissionId(),
                            "SUBMISSION_SUPERSEDED");
                    require(!"REJECTED".equals(req.decision())
                            || (req.notes() != null && !req.notes().isBlank()),
                            "REJECTION_REASON_REQUIRED");
                    require(!"APPROVED".equals(req.decision())
                            || time(last, "ExpiresAt").isAfter(
                                    LocalDateTime.now(clock)),
                                            "DOCUMENT_EXPIRED");
                    var p = new HashMap<String, Object>();
                    p.put("id", req.submissionId());
                    p.put("decision", req.decision());
                    p.put("notes", req.notes());
                    p.put("staff", actor.staffId());
                    db.write("UPDATE journey.Submission SET Decision=:decision,"
                            + "ReviewNotes=:notes,ReviewedBy=:staff"
                            + " WHERE SubmissionID=:id", p);
                    db.write("UPDATE journey.Requirement SET Status=:decision,"
                            + "Version=Version+1 WHERE RequirementID=:id",
                            Map.of("id", id, "decision", req.decision()));
                    invalidate(row);
                    return Map.of("requirementId", id, "status", req.decision(),
                            "version", req.command().version() + 1);
                });
    }

    /** @param horse booking horse @param phase legal phase @param cmd booking
     * version/retry key @return expiring clearance receipt */
    public Map<String, Object> clear(long horse, String phase,
            JourneyModels.Command cmd) {
        var actor = access.role("SPECIALIST");
        require(List.of("PRE_EXPORT", "ARRIVAL", "QUARANTINE_RELEASE")
                .contains(phase), "PHASE_INVALID");
        return access.operation("HORSE_CLEAR:" + horse + ":" + phase,
                cmd.operationId(), cmd, () -> {
                    var h = access.one("SELECT BookingID FROM "
                            + "journey.BookingHorse"
                            + " WHERE BookingHorseID=:id", Map.of("id", horse));
                    var b = access.booking(number(h, "BookingID"));
                    access.version(b, cmd.version());
                    var rows = db.rows("SELECT r.Status,s.Decision,s.ExpiresAt"
                            + " FROM journey.Requirement r OUTER APPLY"
                            + " (SELECT TOP(1) x.Decision,x.ExpiresAt FROM"
                            + " journey.Submission x WHERE x.RequirementID="
                            + "r.RequirementID ORDER BY x.SubmissionID DESC) s"
                            + " WHERE r.BookingHorseID=:id AND r.Phase=:phase",
                            Map.of("id", horse, "phase", phase));
                    require(!rows.isEmpty() && rows.stream().allMatch(r ->
                            "APPROVED".equals(r.get("Status"))
                            && "APPROVED".equals(r.get("Decision"))
                            && time(r, "ExpiresAt").isAfter(time(b,
                                    "DeliveryBy"))),
                            "APPROVED_DOCUMENTS_REQUIRED");
                    LocalDateTime expires = rows.stream().map(r ->
                            time(r, "ExpiresAt")).min(LocalDateTime::compareTo)
                            .orElseThrow();
                    var p = Map.of("horse", horse, "phase", phase,
                            "staff", actor.staffId(), "expiry", expires);
                    db.write("DELETE journey.Clearance WHERE BookingHorseID="
                            + ":horse AND Phase=:phase", p);
                    db.write("INSERT journey.Clearance(BookingHorseID,Phase,"
                            + "SpecialistID,ValidUntil) VALUES"
                            + " (:horse,:phase,:staff,:expiry)", p);
                    db.write("UPDATE journey.Booking SET Version=Version+1"
                            + " WHERE BookingID=:id", Map.of("id",
                                    h.get("BookingID")));
                    return Map.of("bookingHorseId", horse, "phase", phase,
                            "status", "CLEARED", "validUntil", expires,
                            "version", cmd.version() + 1);
                });
    }

    private Map<String, Object> requirement(long id) {
        return access.one("SELECT r.*,h.BookingID FROM journey.Requirement r"
                + " JOIN journey.BookingHorse h ON"
                + " h.BookingHorseID=r.BookingHorseID WHERE RequirementID=:id",
                Map.of("id", id));
    }

    private void invalidate(Map<String, Object> requirement) {
        db.write("DELETE journey.Clearance WHERE BookingHorseID=:horse"
                + " AND Phase=:phase", Map.of("horse",
                        requirement.get("BookingHorseID"), "phase",
                        requirement.get("Phase")));
    }

    private UUID saveFile(long id, JourneyModels.DocumentRequest req) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(req.contentBase64());
        } catch (IllegalArgumentException ex) {
            require(false, "INVALID_BASE64");
            throw ex;
        }
        require(bytes.length > 0 && bytes.length <= 5242880,
                "FILE_SIZE_INVALID");
        boolean signature = switch (req.contentType()) {
            case "application/pdf" -> bytes.length >= 5 && bytes[0] == '%'
                    && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
            case "image/png" -> bytes.length >= 8 && bytes[0] == (byte) 137
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            case "image/jpeg" -> bytes.length >= 3 && bytes[0] == (byte) 255
                    && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
            default -> false;
        };
        require(signature, "FILE_SIGNATURE_INVALID");
        UUID key = UUID.randomUUID();
        db.write("INSERT journey.FileObject(FileKey,BookingID,UploadedBy,"
                + "FileName,ContentType,ContentBytes) VALUES"
                + " (:key,:id,:user,:name,:type,:bytes)", Map.of("key",
                        key.toString(), "id", id, "user",
                                access.actor().userId(),
                        "name", req.fileName(), "type", req.contentType(),
                        "bytes", bytes));
        return key;
    }
}
