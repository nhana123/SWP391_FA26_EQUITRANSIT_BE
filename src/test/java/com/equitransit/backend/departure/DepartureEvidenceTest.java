package com.equitransit.backend.departure;

import com.equitransit.backend.service.departure.DepartureEvidenceStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real image bytes, durable restart, isolation and tamper regressions. */
public class DepartureEvidenceTest {

    private Path root;
    private final OffsetDateTime now =
            OffsetDateTime.parse("2026-10-18T00:00:00Z");

    /** Keep temporary test artifacts inside the build workspace. */
    @BeforeEach
    public void setup() throws Exception {
        root = Files.createDirectories(Path.of("target", "departure-tests",
                java.util.UUID.randomUUID().toString()));
    }

    /** Restart does not lose the image or its scope/hash metadata. */
    @Test
    public void coldRestartPreservesImageAndHash() throws Exception {
        var storage = new DepartureEvidenceStorage(root.toString());
        byte[] image = DepartureCompletionTest.image();
        var descriptor = storage.store(101, 311, 401, 41, image, now);
        var restarted = new DepartureEvidenceStorage(root.toString());
        assertThat(restarted.read(descriptor.fileKey(), 101, 311, 401, 41L))
                .isEqualTo(image);
        assertThat(descriptor.sha256()).hasSize(64);
        assertThat(descriptor.contentType()).isEqualTo("image/png");
    }

    /** Same valid key cannot be rebound to another case, leg or uploader. */
    @Test
    public void coldRestartCannotRebindScope() throws Exception {
        var storage = new DepartureEvidenceStorage(root.toString());
        String key = storage.store(101, 311, 401, 41,
                DepartureCompletionTest.image(), now).fileKey();
        var restarted = new DepartureEvidenceStorage(root.toString());
        assertThatThrownBy(() -> restarted.read(key, 999, 311, 401, 41L))
                .hasMessageContaining("valid scoped");
        assertThatThrownBy(() -> restarted.read(key, 101, 999, 401, 41L))
                .hasMessageContaining("valid scoped");
        assertThatThrownBy(() -> restarted.read(key, 101, 311, 999, 41L))
                .hasMessageContaining("valid scoped");
        assertThatThrownBy(() -> restarted.read(key, 101, 311, 401, 42L))
                .hasMessageContaining("valid scoped");
    }

    /** Stored bytes must still match the server-computed immutable hash. */
    @Test
    public void modifiedImageClosesEvidenceReadiness() throws Exception {
        var storage = new DepartureEvidenceStorage(root.toString());
        String key = storage.store(101, 311, 401, 41,
                DepartureCompletionTest.image(), now).fileKey();
        Path file = root.resolve(key + ".evidence");
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 5] ^= 1;
        Files.write(file, bytes);
        assertThat(storage.valid(key, 101, 311, 401)).isFalse();
    }

    /** A PNG header alone and disguised non-images are rejected. */
    @Test
    public void fakeMimeAndTruncatedImageCannotBeStored() {
        var storage = new DepartureEvidenceStorage(root.toString());
        assertThatThrownBy(() -> storage.store(101, 311, 401, 41,
                "%PDF-1.7 not a photo".getBytes(java.nio.charset.
                        StandardCharsets.US_ASCII), now))
                .hasMessageContaining("valid scoped");
        assertThatThrownBy(() -> storage.store(101, 311, 401, 41,
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10},
                now)).hasMessageContaining("valid scoped");
    }

    /** Oversized images and client paths never enter storage. */
    @Test
    public void sizeAndTraversalAreRejected() {
        var storage = new DepartureEvidenceStorage(root.toString());
        assertThatThrownBy(() -> storage.store(101, 311, 401, 41,
                new byte[10 * 1024 * 1024 + 1], now))
                .hasMessageContaining("valid scoped");
        assertThat(storage.valid("../other-file", 101, 311, 401)).isFalse();
    }
}
