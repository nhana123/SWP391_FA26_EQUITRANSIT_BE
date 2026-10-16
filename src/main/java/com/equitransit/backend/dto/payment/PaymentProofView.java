/*
 * PaymentProofView
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Manual bank payment accounting and protected evidence.
 */
package com.equitransit.backend.dto.payment;


import io.swagger.v3.oas.annotations.media.Schema;


/** Public-safe payment read model. */
public record PaymentProofView(
        @Schema(example = "d86b4f63-5acc-4f20-8da3-e24323268814")
        String fileKey,
        @Schema(example = "image/png")
        String mediaType,
        @Schema(example = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQ"
                + "d1PeAAAADElEQVR4nGNgYGAAAAAEAAH2FzhVAAAAAElFTkSuQmCC")
        String contentBase64) {
}
