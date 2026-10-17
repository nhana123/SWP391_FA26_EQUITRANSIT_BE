package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies actual process restart and disk tampering detection. */
class ComplianceDurableStorageTest {
    @TempDir(factory = ComplianceTestDirectoryFactory.class)
    Path storage;

    @Test
    void secondJvmReadsPersistentEvidenceAndRejectsModifiedBytes()
            throws Exception {
        assertThat(probe("write")).isZero();
        assertThat(probe("read")).isZero();
        Files.writeString(storage.resolve("fixture/passport.pdf"),
                "%PDF-1.7 tampered data", StandardCharsets.UTF_8);
        assertThat(probe("read")).isNotZero();
    }

    @Test
    void serverKeyCannotOverwriteExistingEvidence() throws Exception {
        assertThat(probe("write")).isZero();
        assertThat(probe("write")).isNotZero();
        assertThat(probe("read")).isZero();
    }

    private int probe(String mode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java")
                .toString();
        Path output = storage.resolve("probe-" + mode + ".log");
        var process = new ProcessBuilder(java, "-cp",
                System.getProperty("java.class.path"),
                ComplianceStorageProcessProbe.class.getName(),
                storage.toString(),
                        mode,
                "fixture/passport.pdf").redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Storage subprocess timed out");
        }
        return process.exitValue();
    }
}
