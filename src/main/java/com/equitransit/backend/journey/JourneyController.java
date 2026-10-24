package com.equitransit.backend.journey;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opt-in additive APIs. V1 endpoints and their payloads remain untouched. */
@RestController
@Validated
@RequestMapping("/api/v2")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Journey v2", description = "Consolidated sandbox booking flow")
@ConditionalOnProperty(name = "app.journey.enabled", havingValue = "true")
public class JourneyController {
    private final JourneyBookingService bookings;
    private final JourneyTripService trips;
    private final JourneyDocumentService documents;

    /** @param bookings booking flow @param trips operations @param documents
     * independently reviewed horse dossiers */
    public JourneyController(JourneyBookingService bookings,
            JourneyTripService trips, JourneyDocumentService documents) {
        this.bookings = bookings;
        this.trips = trips;
        this.documents = documents;
    }

    /** @param req pricing selections @return retail estimate */
    @PostMapping("/estimates")
    public Map<String, Object> estimate(
            @RequestBody @Valid JourneyModels.Estimate req) {
        return bookings.estimate(req);
    }

    /** @return active customer-visible tariff catalogue */
    @GetMapping("/tariffs")
    public List<Map<String, Object>> tariffs() {
        return bookings.tariffs();
    }

    /** @param req owned horses and journey @return created booking */
    @PostMapping("/bookings")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody @Valid JourneyModels.BookingRequest req) {
        return ResponseEntity.status(201).body(bookings.create(req));
    }

    /** @return owned/assigned booking queue */
    @GetMapping("/bookings")
    public List<Map<String, Object>> bookings() {
        return bookings.list();
    }

    /** @param id booking key @return scoped detail */
    @GetMapping("/bookings/{id}")
    public Map<String, Object> booking(@PathVariable @Positive long id) {
        return bookings.get(id);
    }

    /**
     * @param id booking key @param req assigned staff/deposit @return receipt
     */
    @PostMapping("/bookings/{id}/approve")
    public Map<String, Object> approve(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.Approval req) {
        return bookings.approve(id, req);
    }

    /** @param id booking @param req reconciled funds @return payment receipt */
    @PostMapping("/bookings/{id}/payments")
    public Map<String, Object> pay(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.PaymentRequest req) {
        return bookings.pay(id, req);
    }

    /** @param req trip resources @return created trip */
    @PostMapping("/trips")
    public ResponseEntity<Map<String, Object>> trip(
            @RequestBody @Valid JourneyModels.TripRequest req) {
        return ResponseEntity.status(201).body(trips.create(req));
    }

    /** @return assigned trip queue */
    @GetMapping("/trips")
    public List<Map<String, Object>> trips() {
        return trips.list();
    }

    /** @param id trip key @return scoped manifest */
    @GetMapping("/trips/{id}")
    public Map<String, Object> trip(@PathVariable @Positive long id) {
        return trips.get(id);
    }

    /** @param id trip @param req many bookings' horses @return updated trip */
    @PostMapping("/trips/{id}/manifest")
    public Map<String, Object> manifest(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.ManifestRequest req) {
        return trips.manifest(id, req);
    }

    /** @param id trip @param action submit/approve/depart/close @param req
     * version/retry input @return updated trip */
    @PostMapping("/trips/{id}/actions/"
            + "{action:submit|approve|depart|close|reopen}")
    public Map<String, Object> action(@PathVariable @Positive long id,
            @PathVariable String action,
            @RequestBody @Valid JourneyModels.Command req) {
        return trips.transition(id, action, req);
    }

    /** @param id trip @param req receiver evidence @return updated trip */
    @PostMapping("/trips/{id}/deliveries")
    public Map<String, Object> delivery(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.DeliveryRequest req) {
        return trips.deliver(id, req);
    }

    /** @param id trip @param req safety checklist @return updated trip */
    @PostMapping("/trips/{id}/predeparture-check")
    public Map<String, Object> safety(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.SafetyRequest req) {
        return trips.safety(id, req);
    }

    /** @param id trip @param req allocated expense @return updated trip */
    @PostMapping("/trips/{id}/extras")
    public Map<String, Object> extra(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.ExtraRequest req) {
        return trips.extra(id, req);
    }

    /** @param id booking @return document requirements/review feedback */
    @GetMapping("/bookings/{id}/documents")
    public List<Map<String,
            Object>> documents(@PathVariable @Positive long id) {
        return documents.list(id);
    }

    /**
     * @param id booking @param req file bytes @return opaque file identifier
     */
    @PostMapping("/bookings/{id}/files")
    public Map<String, Object> upload(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.DocumentRequest req) {
        return documents.upload(id, req);
    }

    /**
     * @param id requirement @param req file/expiry @return submitted version
     */
    @PostMapping("/requirements/{id}/upload-and-submit")
    public Map<String, Object> submit(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.DocumentRequest req) {
        return documents.submit(id, req);
    }

    /** @param id requirement @param req specialist decision @return receipt */
    @PostMapping("/requirements/{id}/reviews")
    public Map<String, Object> review(@PathVariable @Positive long id,
            @RequestBody @Valid JourneyModels.ReviewRequest req) {
        return documents.review(id, req);
    }

    /** @param id booking horse @param phase phase code @param req booking
     * version/retry input @return clearance */
    @PostMapping("/booking-horses/{id}/clearances/{phase}")
    public Map<String, Object> clear(@PathVariable @Positive long id,
            @PathVariable String phase,
            @RequestBody @Valid JourneyModels.Command req) {
        return documents.clear(id, phase, req);
    }

    /** @param key opaque file key @return authorized attachment bytes */
    @GetMapping("/files/{key}")
    public ResponseEntity<byte[]> file(@PathVariable UUID key) {
        var f = documents.file(key);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                f.get("ContentType").toString()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("attachment")
                                .build().toString())
                .body((byte[]) f.get("ContentBytes"));
    }
}
