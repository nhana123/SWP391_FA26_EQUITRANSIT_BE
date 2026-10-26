/*
 * HandoverFileController
 * Purpose: F14 upload/download endpoints for handover evidence.
 */
package com.equitransit.backend.controller.handover;

import com.equitransit.backend.dto.handover.request.HandoverFileUploadRequest;
import com.equitransit.backend.dto.handover.response.HandoverFileResponse;
import com.equitransit.backend.service.handover.HandoverFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F14 order-scoped file boundary. */
@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "F14 handover files")
@RequestMapping(value = "/api/transport-orders/{orderId}/handover-files",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class HandoverFileController {

    private final HandoverFileService files;

    /** @param files F14 file service */
    public HandoverFileController(HandoverFileService files) {
        this.files = files;
    }

    /**
     * Uploads handover signature/photo or internal actual-cost evidence.
     * @param orderId order key
     * @param request upload payload
     * @return opaque file response
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload F14 handover file",
            description = "Driver, assigned coordinator or manager may upload.")
    public ResponseEntity<HandoverFileResponse> upload(
            @PathVariable @Positive long orderId,
            @RequestBody @Valid HandoverFileUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                files.upload(orderId, request));
    }

    /**
     * Downloads permitted handover evidence.
     * @param orderId order key
     * @param fileKey opaque file key
     * @return Base64 file response
     */
    @GetMapping("/{fileKey}")
    @Operation(summary = "Download F14 handover file",
            description = "Customers cannot download actual-cost evidence.")
    public ResponseEntity<HandoverFileResponse> download(
            @PathVariable @Positive long orderId,
            @PathVariable UUID fileKey) {
        return ResponseEntity.ok(files.download(orderId, fileKey));
    }
}
