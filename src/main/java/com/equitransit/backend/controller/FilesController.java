package com.equitransit.backend.controller;

import com.equitransit.backend.service.FileStorageService;
import com.equitransit.backend.service.FileStorageService.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Private multipart upload and inline preview transport. */
@RestController
@RequestMapping("/api/files")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class FilesController {
    private final FileStorageService files;

    /** @param files authorized private storage */
    public FilesController(FileStorageService files) {
        this.files = files;
    }

    /** @param file PDF or image @param category grouping @return metadata */
    @Operation(summary = "Upload a private PDF or image up to 10 MiB")
    @PostMapping(value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StoredFile upload(@RequestParam MultipartFile file,
            @RequestParam(defaultValue = "documents") String category) {
        return files.store(file, category);
    }

    /** @param fileKey opaque key @return private inline stream */
    @Operation(summary = "Preview a file visible to the current caller")
    @GetMapping(value = "/{fileKey}", produces = {
            MediaType.APPLICATION_PDF_VALUE, MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE})
    public ResponseEntity<Resource> preview(@PathVariable UUID fileKey) {
        var meta = files.metadata(fileKey);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                meta.contentType())).contentLength(meta.fileSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(meta.fileName(),
                                StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(files.loadAsResource(fileKey.toString()));
    }
}
