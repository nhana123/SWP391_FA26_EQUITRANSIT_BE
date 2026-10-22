/*
 * HandoverFileUploadRequest
 * Purpose: Upload an inspected F14 handover or actual-cost file.
 */
package com.equitransit.backend.dto.handover.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** JSON Base64 upload contract for F14 private files. */
public record HandoverFileUploadRequest(
        @Schema(example = "receiver-signature.png")
        @NotBlank @Size(min = 1, max = 255) String fileName,
        @Schema(example = "image/png")
        @NotBlank @Size(min = 1, max = 100) String contentType,
        @Schema(example = "HANDOVER_SIGNATURE",
                allowableValues = {"HANDOVER_SIGNATURE",
                        "HANDOVER_PHOTO", "ACTUAL_COST_EVIDENCE"})
        @NotBlank
        @Pattern(regexp = "HANDOVER_SIGNATURE|HANDOVER_PHOTO"
                + "|ACTUAL_COST_EVIDENCE")
        String purpose,
        @Schema(example = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB")
        @NotBlank @Size(min = 1, max = 6990508) String contentBase64) {
}
