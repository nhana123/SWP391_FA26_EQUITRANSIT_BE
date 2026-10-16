/*
 * PaymentProofCommand
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import com.equitransit.backend.dto.quote.QuoteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/** Validated manual payment command. */
public record PaymentProofCommand(
        @Schema(example = "{\"recordVersion\":\"000000000001A2B3\","
                + "\"operationId\":\"d86b4f63-5acc-4f20-8da3-e24323268814\"}")
        @NotNull @Valid QuoteCommand command,
        @Schema(example = "image/png")
        @NotBlank
        @Size(min = 1, max = 20) String mediaType,
        @Schema(example = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQ"
                + "d1PeAAAADElEQVR4nGNgYGAAAAAEAAH2FzhVAAAAAElFTkSuQmCC")
        @NotBlank
        @Size(min = 1, max = 6990508) String contentBase64) {
}
