/*
 * ComplianceUploadRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import com.equitransit.backend.dto.validation.OptionalText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import com.equitransit.backend.dto.quote.QuoteCommand;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceUploadRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "HEALTH_CERTIFICATE")
        @NotBlank @Size(min = 1, max = 50)
        @Pattern(regexp = "^[A-Z0-9_-]{1,50}$") String documentType,
        @Schema(example = "health-certificate.png")
        @NotBlank @Size(min = 1, max = 255) String fileName,
        @Schema(example = "image/png")
        @NotBlank @Size(min = 1, max = 100) String contentType,
        @Schema(example = ComplianceExamples.PNG)
        @NotBlank @Size(min = 1, max = 6990508) String contentBase64,
        @Schema(example = "2026-10-01")
        @NotNull LocalDate issuedDate,
        @Schema(example = "2027-03-01")
        @NotNull LocalDate expiryDate,
        @Schema(example = "HEALTH-2027-001",
                nullable = true)
        @OptionalText @Size(min = 1, max = 50) String documentNumber) {
}
