package com.equitransit.backend.dto.compliance;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Data-driven blank form; not a completed or approved certificate.
 * @param typeCode type @param displayName label @param formCode reference
 * @param issuingAuthority issuer label @param subAuthority secondary issuer
 * @param certTextTemplate plain text @param badgeColor UI color token
 * @param emblemIcon UI icon token @param templateFileKey optional PDF
 * @param hasTemplateFile PDF configured @param templateDownloadUrl PDF URL */
@Schema(name = "DocumentTemplateView")
public record DocumentTemplateView(
        @Schema(example = "COGGINS_EIA") String typeCode,
        @Schema(example = "EIA Test") String displayName,
        @Schema(example = "AGID-EIA-TEST FORM 10-11") String formCode,
        @Schema(example = "Authorized veterinary authority")
        String issuingAuthority,
        @Schema(example = "Authorized laboratory") String subAuthority,
        @Schema(example = "UNSIGNED REFERENCE FORM. Sample: ______.")
        String certTextTemplate,
        @Schema(example = "blue") String badgeColor,
        @Schema(example = "blood") String emblemIcon,
        @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0",
                nullable = true)
        UUID templateFileKey,
        @Schema(example = "false") boolean hasTemplateFile,
        @Schema(example = "/api/files/84032724-eac7-45df-8d04-e54f092279d0",
                nullable = true) String templateDownloadUrl) { }
