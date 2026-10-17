/*
 * ComplianceFileRequest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.dto.compliance;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.dto.quote.QuoteCommand;


/** Typed legal evidence contract; files are private opaque keys. */
public record ComplianceFileRequest(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "authority-response.png")
        @NotBlank @Size(min = 1, max = 255) String fileName,
        @Schema(example = "image/png")
        @NotBlank @Size(min = 1, max = 100) String contentType,
        @Schema(example = ComplianceExamples.PNG)
        @NotBlank @Size(min = 1, max = 6990508) String contentBase64) {
}
