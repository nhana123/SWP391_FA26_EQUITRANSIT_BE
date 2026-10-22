/*
 * HandoverFileResponse
 * Purpose: Return an opaque F14 file and its Base64 content.
 */
package com.equitransit.backend.dto.handover.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** File response shared by F14 upload and download endpoints. */
public record HandoverFileResponse(
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0")
        @NotBlank @Size(min = 1, max = 36) String fileKey,
        @Schema(example = "HANDOVER_SIGNATURE")
        @NotBlank @Size(min = 1, max = 32) String purpose,
        @Schema(example = "image/png")
        @NotBlank @Size(min = 1, max = 100) String contentType,
        @Schema(example = "receiver-signature.png")
        @NotBlank @Size(min = 1, max = 255) String fileName,
        @Schema(example = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB")
        @NotBlank @Size(min = 1, max = 6990508) String contentBase64) {
}
