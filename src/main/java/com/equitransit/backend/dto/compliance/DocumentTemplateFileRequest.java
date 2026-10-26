package com.equitransit.backend.dto.compliance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Explicitly publishes a newly uploaded PDF as a reusable blank template.
 * @param templateFileKey caller-owned private PDF key */
public record DocumentTemplateFileRequest(
        @NotNull @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0")
        UUID templateFileKey) { }
