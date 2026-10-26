package com.equitransit.backend.service;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.media.Schema;

/** Private storage port; callers must authorize every read and attachment. */
public interface FileStorageService {

    /** @param file validated upload @param category logical grouping
     * @return private immutable metadata */
    StoredFile store(MultipartFile file, String category);

    /** @param fileKey opaque identity @return authorized file resource */
    Resource loadAsResource(String fileKey);

    /** @param fileKey opaque identity @return authorized metadata */
    StoredFile metadata(UUID fileKey);

    /** Immutable upload receipt; never exposes a server filesystem path.
     * @param fileKey opaque key @param fileName original name
     * @param fileSizeBytes length @param contentType validated media type */
    @Schema(name = "StoredFile")
    record StoredFile(
            @Schema(example = "84032724-eac7-45df-8d04-e54f092279d0")
            UUID fileKey,
            @Schema(example = "certificate.pdf") String fileName,
            @Schema(example = "10240") long fileSizeBytes,
            @Schema(example = "application/pdf") String contentType) { }
}
