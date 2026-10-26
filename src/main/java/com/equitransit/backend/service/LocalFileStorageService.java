package com.equitransit.backend.service;

import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.LocalFileRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.compliance.LegalFilePolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** UUID-only private files; no public static-resource mapping. */
@Service
public class LocalFileStorageService implements FileStorageService {
    private final Path root;
    private final LocalFileRepository data;
    private final CurrentActorService actors;
    private final LegalFilePolicy policy;

    /** @param directory private root @param data metadata @param actors caller
     * @param policy parsed-content validation */
    public LocalFileStorageService(@Value("${app.storage.directory:"
            + "storage/uploads/documents}") String directory,
            LocalFileRepository data, CurrentActorService actors,
            LegalFilePolicy policy) {
        root = Path.of(directory).toAbsolutePath().normalize();
        this.data = data;
        this.actors = actors;
        this.policy = policy;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public StoredFile store(MultipartFile file, String category) {
        var actor = actors.current();
        requireReady();
        if (category == null || !category.matches("[A-Za-z0-9_-]{1,40}")
                || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw invalid("FILE_SIZE_OR_CATEGORY_INVALID");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.length() > 255 || name.contains("/")
                || name.contains("\\") || name.contains("..")
                || name.chars().anyMatch(Character::isISOControl)
                || file.getContentType() == null || !name.contains(".")) {
            throw invalid("FILE_TYPE_INVALID");
        }
        try {
            String extension = name.substring(name.lastIndexOf('.') + 1)
                    .toLowerCase(java.util.Locale.ROOT);
            byte[] bytes = policy.inspect("evidence." + extension,
                    file.getContentType(),
                    Base64.getEncoder().encodeToString(file.getBytes()),
                    10 * 1024 * 1024);
            UUID key = UUID.randomUUID();
            Files.createDirectories(root);
            Path path = root.resolve(key.toString());
            Files.write(path, bytes, java.nio.file.StandardOpenOption
                    .CREATE_NEW);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException error) {
                                    org.slf4j.LoggerFactory.getLogger(
                                            LocalFileStorageService.class)
                                            .error("Cleanup failed", error);
                                }
                            }
                        }
                    });
            var receipt = new StoredFile(key, name, bytes.length,
                    file.getContentType());
            data.insert(receipt, actor.userId(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
            return receipt;
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "FILE_STORAGE_UNAVAILABLE", "File storage unavailable.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public StoredFile metadata(UUID fileKey) {
        requireReady();
        var rows = data.visible(fileKey, actors.current());
        if (rows.isEmpty()) {
            throw new OrderException(HttpStatus.NOT_FOUND,
                    "FILE_NOT_FOUND", "File not found.");
        }
        var row = rows.getFirst();
        return new StoredFile(fileKey, (String) row.get("FileName"),
                ((Number) row.get("FileSizeBytes")).longValue(),
                (String) row.get("ContentType"));
    }

    /** {@inheritDoc} */
    @Override
    public Resource loadAsResource(String fileKey) {
        UUID key;
        try {
            key = UUID.fromString(fileKey);
        } catch (IllegalArgumentException error) {
            throw invalid("FILE_KEY_INVALID");
        }
        metadata(key);
        Path path = root.resolve(key.toString()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path,
                LinkOption.NOFOLLOW_LINKS)) {
            throw new OrderException(HttpStatus.NOT_FOUND,
                    "FILE_NOT_FOUND", "File not found.");
        }
        return new FileSystemResource(path);
    }

    private OrderException invalid(String code) {
        return new OrderException(HttpStatus.UNPROCESSABLE_ENTITY,
                code, "Invalid file upload.");
    }

    private void requireReady() {
        if (!data.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "FILE_SCHEMA_PENDING", "Deploy migration024.");
        }
    }
}
